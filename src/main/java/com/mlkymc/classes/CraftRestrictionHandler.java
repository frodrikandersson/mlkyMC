package com.mlkymc.classes;

import com.mlkymc.registry.ModBlocks;
import com.mlkymc.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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

        // Cleric crafts
        restrict(ModItems.BLESSED_EMBER, ClassType.CLERIC);
        restrict(ModItems.TOTEM_OF_RESURRECTION, ClassType.CLERIC);
        restrict(ModItems.HOLY_WATER, ClassType.CLERIC);
        restrict(ModItems.BLESSING_SCROLL, ClassType.CLERIC);

        // Farmhand crafts
        restrict(ModItems.LIVING_ESSENCE, ClassType.FARMHAND);
        restrict(ModItems.GROWTH_FERTILIZER, ClassType.FARMHAND);
        restrict(ModItems.ANIMAL_FEED, ClassType.FARMHAND);

        // MineCrafter crafts
        restrict(ModItems.RESONANT_CORE, ClassType.MINECRAFTER);
        restrict(ModItems.REINFORCED_PICKAXE, ClassType.MINECRAFTER);
        restrict(ModItems.REINFORCED_AXE, ClassType.MINECRAFTER);
        restrict(ModItems.BUILDERS_WAND, ClassType.MINECRAFTER);
        restrict(ModItems.ENDER_CHEST_BACKPACK, ClassType.MINECRAFTER);

        // Smith crafts
        restrict(ModItems.TEMPERED_PLATE, ClassType.SMITH);
        restrict(ModItems.WHETSTONE, ClassType.SMITH);
        restrict(ModItems.ARMOR_PLATING, ClassType.SMITH);

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

    @SubscribeEvent
    public void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ensureInitialized();
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        ItemStack result = event.getCrafting();
        ClassType required = restrictedItems.get(result.getItem());
        if (required == null) return; // Not a restricted item

        ClassData data = classManager.getOrCreate(player);
        if (data.getChosenClass() == required) return; // Correct class, allow

        // Wrong class — void the craft
        result.setCount(0);
        player.sendSystemMessage(Component.literal("Only " + required.getDisplayName() + " class can craft this!")
                .withColor(0xFF5555));
    }
}
