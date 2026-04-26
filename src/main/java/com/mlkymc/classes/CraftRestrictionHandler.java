package com.mlkymc.classes;

import com.mlkymc.registry.ModBlocks;
import com.mlkymc.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Phase 5: Restricts crafting of class-specific items.
 * Cross-class reagents can be crafted by their respective class only.
 * Class items can only be crafted by that class.
 */
public class CraftRestrictionHandler {
    private final ClassManager classManager;
    private final Map<Item, ClassType> restrictedItems = new HashMap<>();

    private boolean initialized = false;

    public CraftRestrictionHandler(ClassManager classManager) {
        this.classManager = classManager;
    }

    private void ensureInitialized() {
        if (initialized) return;
        initialized = true;
        registerRestrictions();
    }

    private void registerRestrictions() {
        // Adventurer crafts
        restrict(ModItems.WAYSTONE_SHARD, ClassType.ADVENTURER);
        restrict(ModItems.WAYFINDER_COMPASS, ClassType.ADVENTURER);
        restrict(ModItems.GRAPPLING_HOOK, ClassType.ADVENTURER);
        restrict(ModItems.GRAPPLING_HOOK_AMMO, ClassType.ADVENTURER);
        restrict(ModItems.WARP_STONE, ClassType.ADVENTURER);

        // Cleric crafts
        restrict(ModItems.BLESSED_EMBER, ClassType.CLERIC);
        restrict(ModItems.TOTEM_OF_RESURRECTION, ClassType.CLERIC);
        restrict(ModItems.BLESSING_SCROLL, ClassType.CLERIC);
        restrictVanilla(net.minecraft.world.item.Items.ENCHANTED_GOLDEN_APPLE, ClassType.CLERIC);
        // Bottle o' Enchanting: no longer craftable — Cleric converts via crouch+RMB
        restrict(ModItems.TOME_OF_THE_SOUL_WARDEN, ClassType.CLERIC);
        restrictBlock(ModBlocks.SOULSTONE_BRICK_ITEM, ClassType.CLERIC);
        restrictBlock(ModBlocks.SOUL_PILLAR_ITEM, ClassType.CLERIC);
        restrictBlock(ModBlocks.CONDUIT_CORE_ITEM, ClassType.CLERIC);
        restrictBlock(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM, ClassType.CLERIC);

        // Farmhand crafts
        restrict(ModItems.LIVING_ESSENCE, ClassType.FARMHAND);
        restrict(ModItems.GROWTH_FERTILIZER, ClassType.FARMHAND);
        restrict(ModItems.ANIMAL_FEED, ClassType.FARMHAND);
        restrict(ModItems.SANDWICH, ClassType.FARMHAND);

        // MineCrafter crafts
        restrict(ModItems.RESONANT_CORE, ClassType.MINECRAFTER);
        restrict(ModItems.REINFORCED_PICKAXE, ClassType.MINECRAFTER);
        restrict(ModItems.REINFORCED_AXE, ClassType.MINECRAFTER);
        restrict(ModItems.BUILDERS_WAND, ClassType.MINECRAFTER);
        restrict(ModItems.ENDER_POUCH, ClassType.MINECRAFTER);
        restrictVanilla(net.minecraft.world.item.Items.LODESTONE, ClassType.MINECRAFTER);

        // Smith crafts
        restrict(ModItems.TEMPERED_PLATE, ClassType.SMITH);
        restrict(ModItems.WHETSTONE, ClassType.SMITH);
        restrict(ModItems.ARMOR_PLATING, ClassType.SMITH);
        restrictVanilla(net.minecraft.world.item.Items.SPAWNER, ClassType.SMITH);
        // Netherite upgrade template is NOT Smith-restricted — any class can craft
        // the vanilla recipe. Smith still has their own exclusive recipe for it.

        // Farmhand crafts — vanilla mob eggs
        restrictVanilla(net.minecraft.world.item.Items.ZOMBIE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.SKELETON_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.SPIDER_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.CAVE_SPIDER_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.BLAZE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.SILVERFISH_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.MAGMA_CUBE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.COW_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.SHEEP_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.CHICKEN_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.PIG_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.RABBIT_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.WOLF_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.CAT_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.BEE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.GOAT_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.HORSE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.DONKEY_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.LLAMA_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.FOX_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.FROG_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.TURTLE_SPAWN_EGG, ClassType.FARMHAND);
        restrictVanilla(net.minecraft.world.item.Items.IRON_GOLEM_SPAWN_EGG, ClassType.FARMHAND);

        // Block items
        restrictBlock(ModBlocks.WARP_ANCHOR_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_BASE_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.SCARECROW_ITEM, ClassType.FARMHAND);
        restrictBlock(ModBlocks.SOUL_FORGE_ITEM, ClassType.SMITH);

        // Trophy blocks — Adventurer
        restrictBlock(ModBlocks.TROPHY_WITHER_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_DRAGON_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_ELDER_GUARDIAN_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_WARDEN_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_BLAZE_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_ENDER_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_CREEPER_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_SPIDER_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_PHANTOM_ITEM, ClassType.ADVENTURER);
        restrictBlock(ModBlocks.TROPHY_WITCH_ITEM, ClassType.ADVENTURER);
    }

    private void restrict(net.neoforged.neoforge.registries.DeferredItem<Item> item, ClassType requiredClass) {
        restrictedItems.put(item.get(), requiredClass);
    }

    private void restrictBlock(net.neoforged.neoforge.registries.DeferredItem<? extends Item> item, ClassType requiredClass) {
        restrictedItems.put(item.get(), requiredClass);
    }

    private void restrictVanilla(Item item, ClassType requiredClass) {
        restrictedItems.put(item, requiredClass);
    }

    /**
     * Per-player pending refund state. Collected during ItemCraftedEvent, drained in
     * ServerTickEvent.Post — we cannot use server.execute() to defer, because when
     * called from the server thread (which packet handling is) it runs synchronously,
     * which would restore the matrix BEFORE vanilla's shrink loop inside ResultSlot.onTake.
     */
    private static final class PendingRefund {
        final ServerPlayer player;
        final Item restrictedItem;
        final int allowedCount;
        final net.minecraft.world.Container matrix;
        final java.util.List<ItemStack> snapshot;
        PendingRefund(ServerPlayer player, Item restrictedItem, int allowedCount,
                      net.minecraft.world.Container matrix, java.util.List<ItemStack> snapshot) {
            this.player = player;
            this.restrictedItem = restrictedItem;
            this.allowedCount = allowedCount;
            this.matrix = matrix;
            this.snapshot = snapshot;
        }
    }

    private final Map<java.util.UUID, PendingRefund> pendingRefunds = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ensureInitialized();
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack result = event.getCrafting();
        ClassType required = restrictedItems.get(result.getItem());
        if (required == null) return;

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() == required) return;

        Item restrictedItem = result.getItem();
        net.minecraft.world.Container matrix = event.getInventory();

        // On first violation this tick, snapshot the pristine pre-shrink matrix state
        // and record the player's legitimate item count. Subsequent craft events from the
        // same shift-click batch piggyback on this snapshot.
        if (!pendingRefunds.containsKey(player.getUUID())) {
            int existing = countItem(player, restrictedItem);
            // Subtract the result count since shift-click may already have placed it in inventory
            int allowed = Math.max(0, existing - result.getCount());

            java.util.List<ItemStack> snapshot = new java.util.ArrayList<>(matrix.getContainerSize());
            for (int i = 0; i < matrix.getContainerSize(); i++) {
                snapshot.add(matrix.getItem(i).copy());
            }
            pendingRefunds.put(player.getUUID(),
                    new PendingRefund(player, restrictedItem, allowed, matrix, snapshot));

            player.sendSystemMessage(Component.literal("Only " + required.getDisplayName() + " class can craft this!")
                    .withColor(0xFF5555));
        }

        // Void this specific craft result so nothing ends up in the cursor/inventory
        result.setCount(0);
    }

    /**
     * Drain pending refunds at end of tick — guaranteed to run after all packet handling
     * (and therefore after vanilla's shrink loop inside ResultSlot.onTake).
     */
    @SubscribeEvent
    public void onServerTickPost(ServerTickEvent.Post event) {
        if (pendingRefunds.isEmpty()) return;
        for (PendingRefund pr : pendingRefunds.values()) {
            ServerPlayer player = pr.player;
            // Trim any crafted items that leaked into inventory/cursor (shift-click path)
            trimItemToCount(player, pr.restrictedItem, pr.allowedCount);
            // Restore the crafting matrix to its pre-craft contents
            net.minecraft.world.Container mtx = pr.matrix;
            java.util.List<ItemStack> snap = pr.snapshot;
            if (mtx != null && snap != null) {
                for (int i = 0; i < mtx.getContainerSize() && i < snap.size(); i++) {
                    mtx.setItem(i, snap.get(i));
                }
                if (player.containerMenu != null) {
                    player.containerMenu.slotsChanged(mtx);
                    player.containerMenu.broadcastChanges();
                }
            }
        }
        pendingRefunds.clear();
    }

    private int countItem(ServerPlayer player, Item item) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(item)) count += slot.getCount();
        }
        if (player.containerMenu != null && player.containerMenu.getCarried().is(item)) {
            count += player.containerMenu.getCarried().getCount();
        }
        return count;
    }

    private void trimItemToCount(ServerPlayer player, Item item, int allowedCount) {
        int remaining = allowedCount;

        // Keep up to allowedCount, remove the rest
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.is(item)) {
                if (remaining >= slot.getCount()) {
                    remaining -= slot.getCount();
                } else {
                    slot.setCount(remaining);
                    if (remaining == 0) player.getInventory().setItem(i, ItemStack.EMPTY);
                    remaining = 0;
                }
            }
        }
        // Check cursor
        if (player.containerMenu != null) {
            ItemStack carried = player.containerMenu.getCarried();
            if (carried.is(item)) {
                if (remaining >= carried.getCount()) {
                    remaining -= carried.getCount();
                } else {
                    carried.setCount(remaining);
                    if (remaining == 0) player.containerMenu.setCarried(ItemStack.EMPTY);
                    remaining = 0;
                }
            }
            player.containerMenu.broadcastChanges();
        }
        player.inventoryMenu.broadcastChanges();
    }

    /**
     * Bottle o' Enchanting: consume 1 XP level on craft. Void if lacking XP.
     */
    @SubscribeEvent
    public void onExpBottleCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ItemStack result = event.getCrafting();
        if (!result.is(net.minecraft.world.item.Items.EXPERIENCE_BOTTLE)) return;

        if (player.experienceLevel < 1) {
            result.setCount(0);
            player.sendSystemMessage(Component.literal("Need at least 1 XP level to craft!").withColor(0xFF5555));
            return;
        }
        player.giveExperienceLevels(-1);
    }

    /**
     * (Reinforced tool enchantment auto-apply removed — tools no longer get Unbreaking III + Efficiency IV)
     */
}
