package com.mlkymc.classes;

import com.mlkymc.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilCraftEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * MineCrafter exclusive: Fletching Table opens a Fletcher's Forge menu that behaves
 * like an anvil. Placing equipment with Smith gamble attributes + a Tempered Plate
 * applies a permanent percentage modifier (1%–30%) on ALL Smith gamble attribute
 * values on the item.
 *
 * <p>Rerolls are allowed but escalate steeply in cost — each successive reroll on the
 * same item costs more player XP levels and more Tempered Plates. The roll itself is
 * heavily weighted toward low values; high rolls are rare.
 *
 * <p>While in the anvil preview, the new modifier line shows "???%". The real value is
 * revealed only after the player takes the result (via the reveal tick scanning
 * inventory + armor + offhand + cursor), mirroring the Smith gamble pattern.
 */
public class FletcherModifierHandler {

    private static final String NBT_MODIFIER_KEY = "mlkymc_fletcher_modifier";
    private static final String NBT_REROLL_COUNT = "mlkymc_fletcher_rerolls";
    private static final String NBT_PENDING_REVEAL = "mlkymc_fletcher_pending";

    private final ClassManager classManager;
    private final Random random = new Random();

    public FletcherModifierHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    // =========================================================================
    // Custom menu — AnvilMenu variant whose isValidBlock accepts fletching tables.
    // Without this, the inherited stillValid() closes the menu immediately because
    // it looks for #minecraft:anvil at the ContainerLevelAccess position.
    // =========================================================================

    public static class FletcherForgeMenu extends AnvilMenu {
        public FletcherForgeMenu(int containerId, Inventory inv, ContainerLevelAccess access) {
            super(containerId, inv, access);
        }

        @Override
        protected boolean isValidBlock(BlockState state) {
            return state.is(Blocks.FLETCHING_TABLE);
        }
    }

    // =========================================================================
    // Open the menu on MineCrafter right-click
    // =========================================================================

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.FLETCHING_TABLE)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;

        event.setCanceled(true);

        player.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> new FletcherForgeMenu(
                        containerId, inv, ContainerLevelAccess.create(level, pos)),
                Component.literal("Fletcher's Forge")
        ));
    }

    // =========================================================================
    // Anvil preview: pre-roll the modifier, bake it into NBT + ATTRIBUTE_MODIFIERS,
    // but show ???% in lore and mark as pending-reveal. The real value is only
    // visible after the player takes the item (reveal tick updates the lore).
    // =========================================================================

    @SubscribeEvent
    public void onAnvilUpdate(AnvilUpdateEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        // Only fire inside our Fletcher's Forge menu — vanilla anvils untouched.
        if (!(player.containerMenu instanceof FletcherForgeMenu)) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.MINECRAFTER) return;

        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();

        if (!SmithGambleHandler.isGambleableEquipment(left)) return;
        if (!right.is(ModItems.TEMPERED_PLATE.get())) return;

        var gambleData = SmithGambleHandler.readGambleData(left);
        if (gambleData.isEmpty()) return;

        // Rerolls: if the left stack already has a fletcher modifier, increment the
        // reroll count. Escalating cost formula:
        //   xpLevels   = 1 + rerolls * 3   (1, 4, 7, 10, ...)
        //   platesUsed = 1 + rerolls       (1, 2, 3, 4, ...)
        int previousRerolls = readRerollCount(left);
        int nextRerollCount = hasModifier(left) ? previousRerolls + 1 : 0;
        int xpCost = 1 + nextRerollCount * 3;
        int plateCost = 1 + nextRerollCount;

        // Not enough tempered plates in the right slot to cover the cost — no output.
        if (right.getCount() < plateCost) return;

        // Roll the new value. New random each preview call — player can't see the
        // value until they commit by taking, and each take consumes resources, so
        // they pay per attempt. The curve shifts upward with the reroll count so
        // higher rerolls have meaningfully better odds at top-tier modifiers.
        double modifier = rollModifier(nextRerollCount);

        // Build the output: apply modifier to the attribute modifiers, store modifier
        // + reroll count in CUSTOM_DATA, build lore with ???% line, mark pending.
        ItemStack output = left.copy();
        writeModifier(output, modifier);
        writeRerollCount(output, nextRerollCount);
        applyModifierToAttributes(output, modifier);

        // Rebuild Smith gamble lore on the output, strip any previous Fletcher Modifier
        // lines (SmithGambleHandler.updateLore merges non-gamble lore back in, so a stale
        // fletcher line would accumulate on each reroll), then append the new ???% line.
        SmithGambleHandler.updateLorePublic(output, SmithGambleHandler.readGambleData(output));
        List<Component> lore = stripFletcherLines(output.get(DataComponents.LORE));
        String label = nextRerollCount > 0 ? " (reroll " + nextRerollCount + ")" : "";
        lore.add(Component.literal("Fletcher Modifier: ???%" + label).withColor(0x55FFFF));
        output.set(DataComponents.LORE, new ItemLore(lore));

        // Pending-reveal flag — the tick handler will replace ???% with the real value
        // after the player takes the item.
        CompoundTag nbt = output.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putBoolean(NBT_PENDING_REVEAL, true);
        output.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        event.setOutput(output);
        event.setXpCost(xpCost);
        event.setMaterialCost(plateCost);
    }

    // =========================================================================
    // Anvil craft: +3 MineCrafter XP + action bar confirmation when the player takes
    // the result. event.getEntity() is the player (from PlayerEvent base); the old
    // menu.slots.getFirst().container cast silently failed and this body never ran.
    // =========================================================================

    @SubscribeEvent
    public void onAnvilCraft(AnvilCraftEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getMenu() instanceof FletcherForgeMenu)) return;

        ItemStack output = event.getOutput();
        if (!hasModifier(output)) return;

        classManager.addXp(player, ProfessionType.MINECRAFTER, 3, "fletcher forge");
    }

    // =========================================================================
    // Reveal tick: scan each player's main inventory, armor, offhand, and cursor
    // for items with the pending-reveal flag. Rewrite the lore with the real
    // modifier value and clear the flag. Called from MlkyMC's server tick.
    // =========================================================================

    public void tickRevealFletcher(net.minecraft.server.MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            // Cursor (normal left-click pickup lands here)
            ItemStack carried = player.containerMenu.getCarried();
            if (needsReveal(carried)) revealItem(player, carried);

            // Inventory.getContainerSize() covers main (0-35) + armor (36-39) + offhand (40)
            // in one contiguous index space, so a single loop catches any slot the
            // forged item might land in (main, hotbar, equipped armor, offhand).
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack slot = inv.getItem(i);
                if (needsReveal(slot)) revealItem(player, slot);
            }
        }
    }

    private static boolean needsReveal(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        return cd.copyTag().getBooleanOr(NBT_PENDING_REVEAL, false);
    }

    private void revealItem(ServerPlayer player, ItemStack stack) {
        double modifier = readModifier(stack);
        int rerolls = readRerollCount(stack);

        // Rebuild Smith gamble lore, strip any previous Fletcher Modifier lines (same
        // rationale as onAnvilUpdate — updateLorePublic preserves them otherwise), then
        // append the revealed fletcher line with the real value.
        var gambleData = SmithGambleHandler.readGambleData(stack);
        SmithGambleHandler.updateLorePublic(stack, gambleData);
        List<Component> lore = stripFletcherLines(stack.get(DataComponents.LORE));
        String suffix = rerolls > 0 ? " (reroll " + rerolls + ")" : "";
        lore.add(Component.literal("Fletcher Modifier: +" + String.format("%.1f", modifier * 100) + "%" + suffix)
                .withColor(0x55FFFF));
        stack.set(DataComponents.LORE, new ItemLore(lore));

        // Clear the pending flag
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null) {
            CompoundTag nbt = cd.copyTag();
            nbt.remove(NBT_PENDING_REVEAL);
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }

        // Action-bar confirmation so the player sees the real value at the moment of reveal.
        player.displayClientMessage(Component.literal(
                "Fletcher's Forge: +" + String.format("%.1f", modifier * 100) + "% modifier applied!")
                .withColor(0x55FFFF), true);
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Roll a modifier between 1% and 30%. Base distribution at reroll 0 is
     * heavily bottom-weighted (80/15/4/1 across low/med/good/rare). Each reroll
     * shrinks the low-tier probability and grows the higher tiers — at reroll 12+
     * the distribution is roughly 20/35/24/21, giving a committed player real odds
     * at a high-tier roll. The player pays the escalating XP + Tempered Plate cost
     * for each attempt, so reaching the biased tiers is expensive but achievable.
     *
     * <ul>
     *   <li>Tier 1 (low):    1% – 5%</li>
     *   <li>Tier 2 (medium): 6% – 10%</li>
     *   <li>Tier 3 (good):  11% – 20%</li>
     *   <li>Tier 4 (rare):  21% – 30%</li>
     * </ul>
     */
    private double rollModifier(int rerollCount) {
        // Linear ramp from 0.0 (reroll 0) to 1.0 (reroll 12+). Past reroll 12 the
        // distribution no longer improves — enough is enough.
        double factor = Math.min(1.0, rerollCount * (1.0 / 12.0));

        // Shift the cumulative thresholds: the low tier shrinks from 80% down to
        // 20%, medium from the ~15% band shifts, etc. All four tiers remain
        // reachable; the rare tier's probability grows from 1% to ~21%.
        double lowThresh = 0.80 - factor * 0.60;   // 0.80 → 0.20
        double medThresh = 0.95 - factor * 0.40;   // 0.95 → 0.55
        double goodThresh = 0.99 - factor * 0.20;  // 0.99 → 0.79
        // rare tier = above goodThresh (grows from 0.01 to 0.21)

        double roll = random.nextDouble();
        double percent;
        if (roll < lowThresh) {
            percent = 1.0 + random.nextDouble() * 4.0;
        } else if (roll < medThresh) {
            percent = 6.0 + random.nextDouble() * 4.0;
        } else if (roll < goodThresh) {
            percent = 11.0 + random.nextDouble() * 9.0;
        } else {
            percent = 21.0 + random.nextDouble() * 9.0;
        }
        return Math.round(percent * 10.0) / 1000.0; // e.g., 15.3% → 0.153
    }

    /**
     * Apply the percentage modifier to all Smith gamble attribute values on the item.
     * Strips the previous gamble modifiers (prefix "mlkymc:smith_gamble_") and re-adds
     * them with value * (1 + modifier). Reads base values from the gamble NBT, so
     * re-applying with a different modifier is safe (always boosts from base, never
     * compounds on a previously-boosted value).
     */
    private static void applyModifierToAttributes(ItemStack stack, double modifier) {
        var gambleData = SmithGambleHandler.readGambleData(stack);
        if (gambleData.isEmpty()) return;

        ItemAttributeModifiers existing = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS,
                ItemAttributeModifiers.EMPTY);
        var builder = ItemAttributeModifiers.builder();

        for (var entry : existing.modifiers()) {
            String modId = entry.modifier().id().toString();
            if (modId.startsWith("mlkymc:smith_gamble_")) continue;
            builder.add(entry.attribute(), entry.modifier(), entry.slot());
        }

        var slotGroup = SmithGambleHandler.getSlotGroup(stack);
        for (var entry : gambleData.values()) {
            var attr = SmithGambleHandler.getVanillaAttribute(entry.attrName);
            if (attr == null) continue;
            double boostedValue = entry.value * (1.0 + modifier);
            Identifier modId = Identifier.parse("mlkymc:smith_gamble_" + entry.attrName);
            builder.add(attr,
                    new AttributeModifier(modId, boostedValue, AttributeModifier.Operation.ADD_VALUE),
                    slotGroup);
        }

        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
    }

    /**
     * Return a mutable copy of the given lore with any "Fletcher Modifier:" lines removed.
     * Used to prevent stale modifier lines from stacking up across rerolls — the Smith
     * gamble updateLore() merges non-gamble lore back in, which would otherwise preserve
     * the previous fletcher line each pass.
     */
    private static List<Component> stripFletcherLines(ItemLore lore) {
        List<Component> out = new ArrayList<>();
        if (lore == null) return out;
        for (Component line : lore.lines()) {
            if (line.getString().startsWith("Fletcher Modifier")) continue;
            out.add(line);
        }
        return out;
    }

    public static boolean hasModifier(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        return cd.copyTag().contains(NBT_MODIFIER_KEY);
    }

    private static double readModifier(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getDoubleOr(NBT_MODIFIER_KEY, 0);
    }

    private static void writeModifier(ItemStack stack, double modifier) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putDouble(NBT_MODIFIER_KEY, modifier);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }

    private static int readRerollCount(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getIntOr(NBT_REROLL_COUNT, 0);
    }

    private static void writeRerollCount(ItemStack stack, int count) {
        CompoundTag nbt = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        nbt.putInt(NBT_REROLL_COUNT, count);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
    }
}
