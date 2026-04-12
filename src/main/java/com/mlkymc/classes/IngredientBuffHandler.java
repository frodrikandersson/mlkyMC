package com.mlkymc.classes;

import com.mlkymc.classes.ItemBaseValues.AttributeBuff;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.*;

/**
 * Tags food items with attribute buffs when a Farmhand Lv30+ crafts/cooks them.
 * Tags potions with attribute buffs when a Cleric Lv20+ brews them.
 *
 * The buff data is stored in CUSTOM_DATA as:
 *   mlkymc_buffs: [{type: "HEALTH", percent: 10}, {type: "SPEED", percent: 10}]
 *   mlkymc_buff_duration: 900 (ticks)
 *
 * Buffs are sorted alphabetically by type for consistent stacking/display.
 */
public class IngredientBuffHandler {

    private final ClassManager classManager;

    // Non-food items that should still receive attribute buffs when crafted by a Farmhand
    private static final Set<net.minecraft.world.item.Item> BUFF_EXEMPT_ITEMS = Set.of(
            net.minecraft.world.item.Items.GLISTERING_MELON_SLICE,
            net.minecraft.world.item.Items.CAKE,
            net.minecraft.world.item.Items.EGG,
            net.minecraft.world.item.Items.BROWN_EGG,
            net.minecraft.world.item.Items.BLUE_EGG,
            net.minecraft.world.item.Items.SUGAR,
            net.minecraft.world.item.Items.BROWN_MUSHROOM,
            net.minecraft.world.item.Items.RED_MUSHROOM,
            net.minecraft.world.item.Items.SUGAR_CANE,
            net.minecraft.world.item.Items.FERMENTED_SPIDER_EYE,
            net.minecraft.world.item.Items.KELP,
            net.minecraft.world.item.Items.HONEYCOMB,
            net.minecraft.world.item.Items.DANDELION,
            net.minecraft.world.item.Items.POPPY,
            net.minecraft.world.item.Items.BLUE_ORCHID,
            net.minecraft.world.item.Items.ALLIUM,
            net.minecraft.world.item.Items.AZURE_BLUET,
            net.minecraft.world.item.Items.RED_TULIP,
            net.minecraft.world.item.Items.ORANGE_TULIP,
            net.minecraft.world.item.Items.WHITE_TULIP,
            net.minecraft.world.item.Items.PINK_TULIP,
            net.minecraft.world.item.Items.OXEYE_DAISY,
            net.minecraft.world.item.Items.CORNFLOWER,
            net.minecraft.world.item.Items.LILY_OF_THE_VALLEY,
            net.minecraft.world.item.Items.WITHER_ROSE,
            net.minecraft.world.item.Items.MELON,
            net.minecraft.world.item.Items.HONEYCOMB_BLOCK,
            net.minecraft.world.item.Items.HONEY_BLOCK
    );

    // Cache ingredient buffs from cutting boards and cooking blocks, keyed by block position (asLong)
    // Populated by tickCookingBlocks, consumed by onFoodItemSpawn
    private final java.util.Map<Long, Map<AttributeBuff, Integer>> cookingBlockCache = new java.util.concurrent.ConcurrentHashMap<>();

    public IngredientBuffHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    /**
     * Periodically scan cutting boards and cooking blocks near Farmhand players
     * and cache their input items' attribute buffs.
     * Also tags untagged food in hoppers adjacent to owned cooking blocks.
     * Called from server tick.
     */
    public void tickCookingBlocks(net.minecraft.world.level.Level level) {
        if (level.isClientSide()) return;
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;

        var ownerData = com.mlkymc.world.BlockOwnerData.get(sl.getServer());

        // --- Pass 1: proximity-based scan for cutting boards near online Farmhands ---
        // Cutting boards need aggregated ingredient buffs which require scanning the
        // board's container contents. This only runs when the Farmhand is nearby (8 blocks).
        for (var player : sl.players()) {
            if (!(player instanceof net.minecraft.server.level.ServerPlayer sp)) continue;

            ClassData data = classManager.getOrCreate(sp);
            if (data.getChosenClass() != ClassType.FARMHAND) continue;
            if (data.getLevel(ProfessionType.FARMHAND) < 30) continue;

            var nearbyOwned = ownerData.getOwnersNear(sp.blockPosition(), 8);
            for (var entry : nearbyOwned.entrySet()) {
                String blockId = sl.getBlockState(entry.getKey()).getBlock().getDescriptionId();
                if (!blockId.contains("cutting_board")) continue;

                var be = sl.getBlockEntity(entry.getKey());
                if (be == null || !(be instanceof net.minecraft.world.Container container)) continue;

                var ingredientBuffs = new LinkedHashMap<AttributeBuff, Integer>();
                for (int i = 0; i < container.getContainerSize(); i++) {
                    ItemStack slot = container.getItem(i);
                    if (slot.isEmpty()) continue;

                    if (hasBuff(slot)) {
                        var applied = readBuffs(slot);
                        for (var e : applied.entrySet()) {
                            ingredientBuffs.merge(e.getKey(), e.getValue(), Integer::sum);
                        }
                    }

                    AttributeBuff base = ItemBaseValues.getAttributeBuff(slot.getItem());
                    if (base != AttributeBuff.NONE) {
                        ingredientBuffs.merge(base, 10, Integer::sum);
                    }
                }

                if (!ingredientBuffs.isEmpty()) {
                    cookingBlockCache.put(entry.getKey().asLong(), ingredientBuffs);
                }
                tagHopperContents(sl, entry.getKey(), ingredientBuffs);
            }
        }

        // --- Pass 2: ownership-based scan for furnaces owned by Farmhand Lv30+ ---
        // Furnaces don't need the Farmhand to be nearby — the ownership stamp on the
        // block is proof enough. This lets hopper automation work while the Farmhand
        // is anywhere on the server (or even offline). We iterate all owned blocks,
        // filter for furnaces whose owner is a Farmhand Lv30+, and tag hopper contents.
        for (var entry : ownerData.getAllEntries()) {
            net.minecraft.core.BlockPos pos = net.minecraft.core.BlockPos.of(Long.parseLong(entry.getKey()));
            // Only process if chunk is loaded (don't force-load chunks)
            if (!sl.isLoaded(pos)) continue;
            String blockId = sl.getBlockState(pos).getBlock().getDescriptionId();
            boolean isVanillaFurnace = blockId.contains("furnace")
                    || blockId.contains("smoker") || blockId.contains("blast");
            if (!isVanillaFurnace) continue;

            // Look up the owner's class data (works for online and offline players)
            java.util.UUID ownerUuid;
            try { ownerUuid = java.util.UUID.fromString(entry.getValue().uuid()); }
            catch (IllegalArgumentException e) { continue; }

            ClassData ownerData2 = classManager.getOrCreate(ownerUuid);
            if (ownerData2.getChosenClass() != ClassType.FARMHAND) continue;
            if (ownerData2.getLevel(ProfessionType.FARMHAND) < 30) continue;

            // Tag untagged food in adjacent hoppers with the food's own base buff
            tagHopperContents(sl, pos, null);
        }
    }

    /**
     * Check hoppers adjacent to a cooking block and tag any untagged food items
     * with the appropriate attribute buffs.
     */
    private void tagHopperContents(net.minecraft.server.level.ServerLevel sl,
                                   net.minecraft.core.BlockPos cookingPos,
                                   Map<AttributeBuff, Integer> cachedInputBuffs) {
        var directions = net.minecraft.core.Direction.values();
        for (var dir : directions) {
            var hopperPos = cookingPos.relative(dir);
            var hopperBe = sl.getBlockEntity(hopperPos);
            if (!(hopperBe instanceof net.minecraft.world.level.block.entity.HopperBlockEntity hopper)) continue;

            for (int i = 0; i < hopper.getContainerSize(); i++) {
                ItemStack slot = hopper.getItem(i);
                if (slot.isEmpty()) continue;
                if (!slot.has(DataComponents.FOOD)) continue;
                if (hasBuff(slot)) continue;

                // Determine buffs for this food item
                var buffCounts = new LinkedHashMap<AttributeBuff, Integer>();
                int buffedCount = 0;

                // Base buff from smelting input or own type
                AttributeBuff baseBuff;
                var inputItem = ItemBaseValues.getSmeltingInput(slot.getItem());
                if (inputItem != null) {
                    baseBuff = ItemBaseValues.getAttributeBuff(inputItem);
                } else {
                    baseBuff = ItemBaseValues.getAttributeBuff(slot.getItem());
                }
                if (baseBuff != AttributeBuff.NONE) {
                    buffCounts.put(baseBuff, 10);
                    buffedCount++;
                }

                // Add cached input ingredient buffs
                if (cachedInputBuffs != null && !cachedInputBuffs.isEmpty()) {
                    for (var cb : cachedInputBuffs.entrySet()) {
                        buffCounts.merge(cb.getKey(), cb.getValue(), Integer::sum);
                        buffedCount++;
                    }
                }

                if (buffCounts.isEmpty()) continue;

                int durationTicks = (15 + 15 * buffedCount) * 20;
                applyBuffsToItem(slot, buffCounts, durationTicks);
                hopper.setChanged();
            }
        }
    }

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ItemStack output = event.getCrafting();

        // Special handling for Sandwich — tag with the 3 food ingredients' buffs
        if (output.getItem() instanceof com.mlkymc.registry.SandwichItem) {
            ClassData sData = classManager.getOrCreate(player);
            if (sData.getChosenClass() != ClassType.FARMHAND) return;
            // Collect the 3 food ingredients (not bread, not milky star)
            var grid = event.getInventory();
            java.util.List<ItemStack> foodIngredients = new java.util.ArrayList<>();
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack slot = grid.getItem(i);
                if (slot.isEmpty()) continue;
                if (slot.is(net.minecraft.world.item.Items.BREAD)) continue;
                if (slot.is(com.mlkymc.registry.ModItems.MILKY_STAR.get())) continue;
                // Accept items with FOOD component or items with attribute buffs in ItemBaseValues
                if (slot.has(DataComponents.FOOD)
                        || ItemBaseValues.getAttributeBuff(slot.getItem()) != AttributeBuff.NONE) {
                    foodIngredients.add(slot.copy());
                }
            }
            if (!foodIngredients.isEmpty()) {
                // Tag the output directly (works for left-click)
                com.mlkymc.registry.SandwichItem.tagSandwich(output, foodIngredients);

                // Schedule shift-click tagging + merging
                var ingredientsCopy = new java.util.ArrayList<>(foodIngredients);
                ShiftClickHelper.scheduleTagAndMerge(player, output.getItem(),
                        stack -> !hasBuff(stack),
                        stack -> com.mlkymc.registry.SandwichItem.tagSandwich(stack, ingredientsCopy));
            }
            return;
        }

        // Only buff food items or specific non-food items that carry attribute buffs
        if (!output.has(DataComponents.FOOD) && !BUFF_EXEMPT_ITEMS.contains(output.getItem())) return;

        // Check if crafter is Farmhand Lv30+
        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.FARMHAND) return;
        if (data.getLevel(ProfessionType.FARMHAND) < 30) return;

        // Collect attribute buffs from ingredients:
        // 1. Each ingredient's base AttributeBuff from ItemBaseValues
        // 2. Any existing mlkymc_buffs tag on the ingredient (from previous crafting steps)
        var grid = event.getInventory();
        Map<AttributeBuff, Integer> buffCounts = new LinkedHashMap<>();
        int buffedIngredientCount = 0;

        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack slot = grid.getItem(i);
            if (slot.isEmpty()) continue;

            // 1. Base buff from the item type itself
            AttributeBuff baseBuff = ItemBaseValues.getAttributeBuff(slot.getItem());
            if (baseBuff != AttributeBuff.NONE) {
                buffCounts.merge(baseBuff, 10, Integer::sum); // +10% per ingredient
                buffedIngredientCount++;
            }

            // 2. Inherited buffs from previous crafting steps (multi-step accumulation)
            if (hasBuff(slot)) {
                var inherited = readBuffs(slot);
                for (var entry : inherited.entrySet()) {
                    buffCounts.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
                if (!inherited.isEmpty()) buffedIngredientCount++;
            }
        }

        if (buffCounts.isEmpty()) return;

        // Sort alphabetically for consistent stacking/display
        var sortedBuffs = new LinkedHashMap<AttributeBuff, Integer>();
        buffCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(Enum::name)))
                .forEach(e -> sortedBuffs.put(e.getKey(), e.getValue()));
        buffCounts = sortedBuffs;

        // Calculate duration: 30s base + 15s per buffed ingredient
        int durationTicks = (15 + 15 * buffedIngredientCount) * 20;

        // Apply to output directly (left-click)
        var buffCountsFinal = buffCounts;
        int durationFinal = durationTicks;
        applyBuffsToItem(output, buffCountsFinal, durationFinal);

        // Schedule shift-click tagging + merging
        ShiftClickHelper.scheduleTagAndMerge(player, output.getItem(),
                stack -> !hasBuff(stack),
                stack -> applyBuffsToItem(stack, buffCountsFinal, durationFinal));
    }

    /**
     * Catch-all for modded cooking blocks (grills, stoves, etc.):
     * When a food item entity spawns near a cooking block owned by a Farmhand Lv30+,
     * and the food has a smelting input mapping, tag it with the input's base buff.
     */
    @SubscribeEvent
    public void onFoodItemSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return;
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl)) return;

        ItemStack stack = itemEntity.getItem();
        if (!stack.has(DataComponents.FOOD)) return;
        if (hasBuff(stack)) return;

        // Only tag items that are actual cooked/smelted outputs. Require a smelting-input
        // mapping — raw food dropped by the player has no mapping and must NOT be tagged
        // (otherwise a Farmhand could take raw food out of a grill and drop it next to the
        // grill to instantly gain +10% buffs, then re-cook it for more stacking).
        var inputItem = ItemBaseValues.getSmeltingInput(stack.getItem());
        if (inputItem == null) return;
        AttributeBuff baseBuff = ItemBaseValues.getAttributeBuff(inputItem);

        // Check if spawned near an owned cooking block
        var pos = itemEntity.blockPosition();
        var ownerData = com.mlkymc.world.BlockOwnerData.get(sl.getServer());
        var nearbyOwners = ownerData.getOwnersNear(pos, 2);

        for (var entry : nearbyOwners.entrySet()) {
            var blockState = sl.getBlockState(entry.getKey());
            String blockId = blockState.getBlock().getDescriptionId();
            boolean isCookingBlock = ItemBaseValues.isCookingBlock(blockState.getBlock());
            boolean isCuttingBoard = blockId.contains("cutting_board");
            if (!isCookingBlock && !isCuttingBoard) continue;

            // Found a cooking block nearby — check if owner is Farmhand Lv30+
            java.util.UUID ownerUuid = java.util.UUID.fromString(entry.getValue().uuid());
            var owner = sl.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner == null) continue;

            ClassData data = classManager.getOrCreate(owner);
            if (data.getChosenClass() != ClassType.FARMHAND) continue;
            if (data.getLevel(ProfessionType.FARMHAND) < 30) continue;

            // Collect buffs: base buff + cached input ingredient buffs
            var buffCounts = new LinkedHashMap<AttributeBuff, Integer>();
            int buffedCount = 0;

            if (baseBuff != AttributeBuff.NONE) {
                buffCounts.put(baseBuff, 10);
                buffedCount++;
            }

            // Check cached ingredient buffs from this cooking block
            var cachedBuffs = cookingBlockCache.remove(entry.getKey().asLong());
            if (cachedBuffs != null) {
                for (var cb : cachedBuffs.entrySet()) {
                    buffCounts.merge(cb.getKey(), cb.getValue(), Integer::sum);
                    buffedCount++;
                }
            }

            if (buffCounts.isEmpty()) continue;

            int durationTicks = (15 + 15 * buffedCount) * 20;
            applyBuffsToItem(stack, buffCounts, durationTicks);
            break;
        }
    }

    /**
     * Smelting/cooking: apply input item's base buff to the output.
     * Covers furnaces, smokers, blast furnaces.
     *
     * Does NOT tag the output directly — by the time this event fires,
     * shift-click may have already merged the output with existing inventory stacks.
     * Instead, schedules a next-tick task that tags only the extracted count,
     * splitting merged stacks if needed.
     */
    @SubscribeEvent
    public void onItemSmelted(net.neoforged.neoforge.event.entity.player.PlayerEvent.ItemSmeltedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isCreative()) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() != ClassType.FARMHAND) return;
        if (data.getLevel(ProfessionType.FARMHAND) < 30) return;

        ItemStack output = event.getSmelting();
        if (!output.has(DataComponents.FOOD)) return;

        // Look up what input item produced this output
        var inputItem = ItemBaseValues.getSmeltingInput(output.getItem());
        if (inputItem == null) return;

        AttributeBuff baseBuff = ItemBaseValues.getAttributeBuff(inputItem);
        if (baseBuff == AttributeBuff.NONE) return;

        var buffCounts = new LinkedHashMap<AttributeBuff, Integer>();
        buffCounts.put(baseBuff, 10);
        int durationTicks = 15 * 20;

        int extractedCount = output.getCount();
        var itemType = output.getItem();

        // Schedule next-tick: find untagged items of this type and tag only extractedCount,
        // splitting stacks that merged with pre-existing untagged items
        player.level().getServer().execute(() -> {
            var inv = player.getInventory();
            int remaining = extractedCount;

            for (int i = 0; i < inv.getContainerSize() && remaining > 0; i++) {
                ItemStack slot = inv.getItem(i);
                if (slot.isEmpty()) continue;
                if (slot.getItem() != itemType) continue;
                if (hasBuff(slot)) continue;

                if (slot.getCount() <= remaining) {
                    // Tag entire stack
                    applyBuffsToItem(slot, buffCounts, durationTicks);
                    remaining -= slot.getCount();
                } else {
                    // Split: tag only 'remaining' items, leave rest untagged
                    ItemStack toTag = slot.split(remaining);
                    applyBuffsToItem(toTag, buffCounts, durationTicks);
                    if (!inv.add(toTag)) {
                        player.drop(toTag, false);
                    }
                    remaining = 0;
                }
            }

            // Also check cursor (left-click extraction)
            ItemStack carried = player.containerMenu.getCarried();
            if (!carried.isEmpty() && carried.getItem() == itemType && !hasBuff(carried) && remaining > 0) {
                applyBuffsToItem(carried, buffCounts, durationTicks);
            }
        });
    }

    public static void applyBuffsToItemStatic(ItemStack item, Map<AttributeBuff, Integer> buffCounts, int durationTicks) {
        applyBuffsToItemImpl(item, buffCounts, durationTicks);
    }

    private void applyBuffsToItem(ItemStack item, Map<AttributeBuff, Integer> buffCounts, int durationTicks) {
        applyBuffsToItemImpl(item, buffCounts, durationTicks);
    }

    private static void applyBuffsToItemImpl(ItemStack item, Map<AttributeBuff, Integer> buffCounts, int durationTicks) {
        // Store buff data in CUSTOM_DATA
        CompoundTag nbt = new CompoundTag();
        ListTag buffList = new ListTag();
        for (var entry : buffCounts.entrySet()) {
            CompoundTag buffTag = new CompoundTag();
            buffTag.putString("type", entry.getKey().name());
            buffTag.putInt("percent", entry.getValue());
            buffList.add(buffTag);
        }
        nbt.put("mlkymc_buffs", buffList);
        nbt.putInt("mlkymc_buff_duration", durationTicks);

        CustomData existing = item.get(DataComponents.CUSTOM_DATA);
        if (existing != null) {
            CompoundTag merged = existing.copyTag();
            merged.put("mlkymc_buffs", buffList);
            merged.putInt("mlkymc_buff_duration", durationTicks);
            item.set(DataComponents.CUSTOM_DATA, CustomData.of(merged));
        } else {
            item.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));
        }

        item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal("Farmhand Enhanced").withColor(0x55FF55));
        for (var entry : buffCounts.entrySet()) {
            int pct = entry.getValue();
            boolean inverted = entry.getKey() == ItemBaseValues.AttributeBuff.BURNING_TIME
                    || entry.getKey() == ItemBaseValues.AttributeBuff.FALL_DAMAGE;
            String sign = inverted ? "-" : "+";
            lore.add(Component.literal("  " + sign + pct + "% " + formatBuffName(entry.getKey()))
                    .withColor(0xAAFFAA));
        }
        lore.add(Component.literal("  Duration: " + (durationTicks / 20) + "s").withColor(0xAAAAAA));
        item.set(DataComponents.LORE, new ItemLore(lore));
    }

    /**
     * Format buff enum name to display name: ATTACK_SPEED -> "Attack Speed"
     */
    public static String formatBuffName(AttributeBuff buff) {
        // Special display names
        if (buff == AttributeBuff.REACH) return "Block Reach";
        if (buff == AttributeBuff.ENTITY_REACH) return "Entity Reach";

        String name = buff.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        boolean capitalize = true;
        for (char c : name.toCharArray()) {
            if (capitalize) {
                sb.append(Character.toUpperCase(c));
                capitalize = false;
            } else {
                sb.append(c);
            }
            if (c == ' ') capitalize = true;
        }
        return sb.toString();
    }

    /**
     * Check if an ItemStack has mlkymc buff data.
     */
    public static boolean hasBuff(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return false;
        return cd.copyTag().contains("mlkymc_buffs");
    }

    /**
     * Read buff entries from an item's CUSTOM_DATA.
     * Returns a map of AttributeBuff -> percent (e.g., HEALTH -> 20 means +20%)
     */
    public static Map<AttributeBuff, Integer> readBuffs(ItemStack stack) {
        Map<AttributeBuff, Integer> result = new LinkedHashMap<>();
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return result;
        CompoundTag nbt = cd.copyTag();
        if (!nbt.contains("mlkymc_buffs")) return result;

        var list = nbt.getListOrEmpty("mlkymc_buffs");
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) instanceof CompoundTag tag) {
                try {
                    AttributeBuff type = AttributeBuff.valueOf(tag.getStringOr("type", "NONE"));
                    int percent = tag.getIntOr("percent", 0);
                    if (type != AttributeBuff.NONE && percent > 0) {
                        result.put(type, percent);
                    }
                } catch (IllegalArgumentException ignored) {}
            }
        }
        return result;
    }

    /**
     * Read buff duration in ticks from an item's CUSTOM_DATA.
     */
    public static int readDuration(ItemStack stack) {
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return 0;
        return cd.copyTag().getIntOr("mlkymc_buff_duration", 0);
    }
}
