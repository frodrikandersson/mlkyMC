package com.mlkymc.altar;

import com.mlkymc.MlkyMC;
import com.mlkymc.classes.ClassType;
import com.mlkymc.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Handles Soul Altar interactions:
 * - Right-click Capstone: validate multiblock, open GUI
 * - Any player with Milky Star: donate to altar
 * - Block break: invalidate altar if multiblock broken
 */
public class SoulAltarListener {

    // Track SE to store on capstone drops when altar is broken
    private static final java.util.Map<Long, int[]> pendingCapstoneDropSE = new java.util.HashMap<>();
    // Track SE from capstone items about to be placed (playerUUID -> [storedSE, highWaterSE])
    private static final java.util.Map<java.util.UUID, int[]> pendingPlacementSE = new java.util.HashMap<>();

    private static final String SE_TAG = "mlkymc_altar_se";
    private static final String HW_TAG = "mlkymc_altar_hw";

    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);

        // Only handle Soul Altar Capstone
        if (state.getBlock() != ModBlocks.SOUL_ALTAR_CAPSTONE.get()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        var altarManager = MlkyMC.getSoulAltarManager();
        String dim = level.dimension().identifier().toString();

        // Check if altar already exists at this position
        SoulAltarData existingAltar = altarManager.getAltar(dim, pos);

        if (existingAltar == null) {
            // Validate multiblock structure
            if (!altarManager.validateMultiblock(level, pos)) {
                player.sendSystemMessage(Component.literal(
                        "Soul Altar structure incomplete. Build the full 3x3x2 multiblock.").withColor(0xFF5555));
                return;
            }

            // Only Clerics can create altars
            var classData = MlkyMC.getClassManager().getOrCreate(player);
            if (classData.getChosenClass() != ClassType.CLERIC) {
                player.sendSystemMessage(Component.literal(
                        "Only a Cleric can activate the Soul Altar.").withColor(0xFF5555));
                return;
            }

            // Check if this Cleric already owns an altar
            SoulAltarData ownedAltar = altarManager.getAltarByOwner(player.getUUID());
            if (ownedAltar != null) {
                player.sendSystemMessage(Component.literal(
                        "You already own a Soul Altar. Destroy the old one first.").withColor(0xFF5555));
                return;
            }

            altarManager.createAltar(player, pos, dim);

            // Restore SE from capstone if it had stored data
            int[] storedData = pendingPlacementSE.remove(player.getUUID());
            if (storedData != null && storedData[0] > 0) {
                var newAltar = altarManager.getAltar(dim, pos);
                if (newAltar != null) {
                    newAltar.storedSE = Math.min(storedData[0], SoulAltarManager.MAX_ALTAR_SE);
                    newAltar.highWaterSE = Math.max(storedData[1], newAltar.storedSE);
                    altarManager.save();
                    player.sendSystemMessage(Component.literal(
                            "Restored " + newAltar.storedSE + " SE from previous altar!").withColor(0x55FF55));
                }
            }

            player.sendSystemMessage(Component.literal(
                    "Soul Altar activated! Right-click to manage.").withColor(0xAA55FF));
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.5f, 0.8f);
            return;
        }

        // Altar exists — handle interaction
        final SoulAltarData altar = existingAltar;

        // Ghost interaction: connect to altar and open donation GUI
        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager != null && ghostManager.isGhost(player.getUUID())) {
            // Check if already connected
            boolean alreadyConnected = altar.connectedGhosts.stream()
                    .anyMatch(gc -> gc.ghostUuid.equals(player.getUUID()));
            if (!alreadyConnected) {
                if (altar.connectedGhosts.size() >= SoulAltarManager.MAX_GHOSTS) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "Altar already has " + SoulAltarManager.MAX_GHOSTS + " ghosts connected.").withColor(0xFF5555));
                    return;
                }
                var gc = new SoulAltarData.GhostConnection();
                gc.ghostUuid = player.getUUID();
                gc.name = player.getName().getString();
                gc.donatedTotal = 0;
                altar.connectedGhosts.add(gc);
                altarManager.save();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Connected to " + altar.ownerName + "'s Soul Altar!").withColor(0x55FFFF));
            }
            // Open ghost donation GUI
            final ServerPlayer ghostPlayer = player;
            final SoulAltarData altarRef = altar;
            com.mlkymc.ghost.GhostListener.allowNextMenu(player.getUUID());
            player.level().getServer().execute(() -> {
                com.mlkymc.ghost.GhostListener.allowNextMenu(ghostPlayer.getUUID());
                GhostDonationMenu.open(ghostPlayer, altarRef, altarManager);
            });
            return;
        }

        // Non-owner: open donation GUI
        if (!altar.ownerUuid.equals(player.getUUID())) {
            openDonationGUI(player, altar);
            return;
        }

        // Owner: open altar management GUI
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new SoulAltarMenu(containerId, playerInv, player, altar),
                Component.literal("Soul Altar")
        ));
    }

    /**
     * Opens a donation GUI for non-owner players to donate Milky Stars to the altar.
     */
    private void openDonationGUI(ServerPlayer player, SoulAltarData altar) {
        var altarManager = MlkyMC.getSoulAltarManager();
        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(9);

        int tier = altar.highWaterSE >= 100_000 ? 4 : altar.highWaterSE >= 75_000 ? 3
                : altar.highWaterSE >= 50_000 ? 2 : altar.highWaterSE >= 25_000 ? 1 : 0;
        int available = com.mlkymc.economy.MilkyStar.count(player);

        // Slot 0: Altar info
        net.minecraft.world.item.ItemStack info = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BEACON);
        info.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Soul Altar — " + altar.ownerName).withColor(0xAA55FF));
        java.util.List<Component> infoLore = new java.util.ArrayList<>();
        infoLore.add(Component.literal("Tier: " + tier).withColor(0xAAAAAA));
        infoLore.add(Component.literal("SE: " + altar.storedSE + " / 100,000").withColor(0xAAAAAA));
        infoLore.add(Component.literal("Ghosts: " + altar.connectedGhosts.size() + " / 3").withColor(0xAAAAAA));
        infoLore.add(Component.literal("Your Milky Stars: " + available).withColor(0xFFAA00));
        info.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(infoLore));
        container.setItem(0, info);

        // Slot 2: Donate 1 star
        net.minecraft.world.item.ItemStack d1 = new net.minecraft.world.item.ItemStack(
                com.mlkymc.registry.ModItems.MILKY_STAR.get());
        d1.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Donate 1 Star (+30 SE)").withColor(0x55FF55));
        container.setItem(2, d1);

        // Slot 4: Donate 10 stars
        net.minecraft.world.item.ItemStack d10 = new net.minecraft.world.item.ItemStack(
                com.mlkymc.registry.ModItems.MILKY_STAR.get(), 10);
        d10.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Donate 10 Stars (+300 SE)").withColor(0x55FF55));
        container.setItem(4, d10);

        // Slot 6: Donate all
        net.minecraft.world.item.ItemStack dAll = new net.minecraft.world.item.ItemStack(
                com.mlkymc.registry.ModItems.MILKY_STAR.get(), Math.min(64, Math.max(1, available)));
        dAll.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Donate All (" + available + " Stars)").withColor(0x55FF55));
        container.setItem(6, dAll);

        // Slot 8: Close
        net.minecraft.world.item.ItemStack close = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BARRIER);
        close.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                Component.literal("Close").withColor(0xFF5555));
        container.setItem(8, close);

        player.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        net.minecraft.world.inventory.MenuType.GENERIC_9x1, syncId, inv, container, 1) {
                    @Override
                    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType,
                                        net.minecraft.world.entity.player.Player clickPlayer) {
                        if (!(clickPlayer instanceof ServerPlayer sp)) return;
                        if (slotId == 8) { sp.closeContainer(); return; }

                        int amount = 0;
                        if (slotId == 2) amount = 1;
                        else if (slotId == 4) amount = 10;
                        else if (slotId == 6) amount = com.mlkymc.economy.MilkyStar.count(sp);
                        else return;

                        if (amount <= 0) return;
                        int avail = com.mlkymc.economy.MilkyStar.count(sp);
                        if (avail <= 0) {
                            sp.sendSystemMessage(Component.literal("No Milky Stars!").withColor(0xFF5555));
                            return;
                        }
                        amount = Math.min(amount, avail);

                        // Cap at altar max
                        int spaceLeft = SoulAltarManager.MAX_ALTAR_SE - altar.storedSE;
                        if (spaceLeft <= 0) {
                            sp.sendSystemMessage(Component.literal("Altar is full!").withColor(0xFF5555));
                            return;
                        }
                        int starsNeeded = (int) Math.ceil(spaceLeft / 30.0);
                        amount = Math.min(amount, starsNeeded);

                        com.mlkymc.economy.MilkyStar.remove(sp, amount);
                        int gained = altarManager.depositMilkyStars(altar, sp.getName().getString(), amount);
                        sp.sendSystemMessage(Component.literal(
                                "Donated " + amount + " Milky Stars (+" + gained + " SE)").withColor(0x55FF55));

                        // Refresh GUI
                        sp.closeContainer();
                        openDonationGUI(sp, altar);
                    }

                    @Override
                    public net.minecraft.world.item.ItemStack quickMoveStack(
                            net.minecraft.world.entity.player.Player player, int index) {
                        return net.minecraft.world.item.ItemStack.EMPTY;
                    }
                },
                Component.literal("Donate to Soul Altar")
        ));
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        var block = state.getBlock();

        // Check if breaking any part of a soul altar multiblock
        if (block != ModBlocks.SOUL_ALTAR_CAPSTONE.get()
                && block != ModBlocks.SOULSTONE_BRICK.get()
                && block != ModBlocks.CONDUIT_CORE.get()
                && block != ModBlocks.SOUL_PILLAR.get()) {
            return;
        }

        var altarManager = MlkyMC.getSoulAltarManager();
        String dim = level.dimension().identifier().toString();

        // Search nearby for an altar that includes this block position
        for (var altar : altarManager.getAllAltars()) {
            if (!altar.dimension.equals(dim)) continue;
            if (isPartOfAltar(pos, altar.capstonePos)) {
                // Reset owner's selected ability to Heal (0) and resync HUD
                var ownerPlayer = level.getServer().getPlayerList().getPlayer(altar.ownerUuid);
                if (ownerPlayer != null) {
                    var classData = MlkyMC.getClassManager().getOrCreate(ownerPlayer);
                    classData.setSelectedAltarAbility(0);
                    MlkyMC.getClassManager().save();
                }
                int storedSE = altar.storedSE;
                int highWater = altar.highWaterSE;
                BlockPos capPos = altar.capstonePos;
                altarManager.removeAltar(altar);
                if (ownerPlayer != null) {
                    MlkyMC.getClassManager().sendSoulSync(ownerPlayer);
                }

                // Drop the capstone as an item with SE data stored on it
                // Remove the capstone block from the world if it still exists
                if (level.getBlockState(capPos).getBlock() == ModBlocks.SOUL_ALTAR_CAPSTONE.get()) {
                    level.destroyBlock(capPos, false); // destroy without drops
                    // Create capstone item with SE
                    net.minecraft.world.item.ItemStack capItem = new net.minecraft.world.item.ItemStack(
                            ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get());
                    if (storedSE > 0) {
                        var nbt = new net.minecraft.nbt.CompoundTag();
                        nbt.putInt(SE_TAG, storedSE);
                        nbt.putInt(HW_TAG, highWater);
                        capItem.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                                net.minecraft.world.item.component.CustomData.of(nbt));
                        java.util.List<Component> lore = new java.util.ArrayList<>();
                        lore.add(Component.literal("Stored SE: " + storedSE).withColor(0xAA55FF));
                        lore.add(Component.literal("Highest SE: " + highWater).withColor(0x777777));
                        capItem.set(net.minecraft.core.component.DataComponents.LORE,
                                new net.minecraft.world.item.component.ItemLore(lore));
                    }
                    // Drop the item in the world
                    net.minecraft.world.entity.item.ItemEntity itemEntity =
                            new net.minecraft.world.entity.item.ItemEntity(level,
                                    capPos.getX() + 0.5, capPos.getY() + 0.5, capPos.getZ() + 0.5, capItem);
                    level.addFreshEntity(itemEntity);
                }

                // If player broke the capstone directly, cancel the break event (we handle drop above)
                if (pos.equals(capPos)) {
                    event.setCanceled(true);
                }

                player.sendSystemMessage(Component.literal(
                        "Soul Altar destroyed! Capstone retains " + storedSE + " SE.").withColor(0xFFAA00));
                level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 1.5f, 0.5f);
                break;
            }
        }
    }

    /**
     * Checks if a block position is part of the 3x3x2 altar centered on the capstone.
     */
    private boolean isPartOfAltar(BlockPos brokenPos, BlockPos capstonePos) {
        BlockPos bottomCenter = capstonePos.below();

        // Top layer: capstone + 4 corners
        if (brokenPos.equals(capstonePos)) return true;
        int[][] corners = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        for (int[] c : corners) {
            if (brokenPos.equals(capstonePos.offset(c[0], 0, c[1]))) return true;
        }

        // Bottom layer: 3x3 centered on bottomCenter
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (brokenPos.equals(bottomCenter.offset(dx, 0, dz))) return true;
            }
        }

        return false;
    }

    /**
     * BEFORE a capstone block is placed, capture SE data from the item.
     * UseItemOnBlockEvent fires before the block is placed, so the item still has its data.
     */
    @SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public void onUseItemOnBlock(net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent event) {
        if (event.getUsePhase() != net.neoforged.neoforge.event.entity.player.UseItemOnBlockEvent.UsePhase.BLOCK) return;
        var useCtx = event.getUseOnContext();
        if (!(useCtx.getPlayer() instanceof ServerPlayer player)) return;

        var stack = useCtx.getItemInHand();
        if (!stack.is(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get())) return;

        int[] seData = extractStoredSE(stack);
        if (seData != null) {
            // Store by player UUID — we'll read it when the altar is activated
            // (can't use block pos since the block isn't placed yet)
            pendingPlacementSE.put(player.getUUID(), seData);
        }
    }

    /**
     * When a capstone block drops, tag the item with stored SE if the altar was just broken.
     */
    @SubscribeEvent
    public void onBlockDrop(net.neoforged.neoforge.event.level.BlockDropsEvent event) {
        if (pendingCapstoneDropSE.isEmpty()) return;
        if (event.getState().getBlock() != ModBlocks.SOUL_ALTAR_CAPSTONE.get()) return;

        long posKey = event.getPos().asLong();
        int[] seData = pendingCapstoneDropSE.remove(posKey);
        if (seData == null) return;

        // Tag all capstone item drops with the SE data
        for (var itemEntity : event.getDrops()) {
            var stack = itemEntity.getItem();
            if (stack.is(ModBlocks.SOUL_ALTAR_CAPSTONE_ITEM.get())) {
                var nbt = new net.minecraft.nbt.CompoundTag();
                nbt.putInt(SE_TAG, seData[0]);
                nbt.putInt(HW_TAG, seData[1]);
                stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                        net.minecraft.world.item.component.CustomData.of(nbt));

                // Add lore showing stored SE
                java.util.List<Component> lore = new java.util.ArrayList<>();
                lore.add(Component.literal("Stored SE: " + seData[0]).withColor(0xAA55FF));
                lore.add(Component.literal("Highest SE: " + seData[1]).withColor(0x777777));
                stack.set(net.minecraft.core.component.DataComponents.LORE,
                        new net.minecraft.world.item.component.ItemLore(lore));
            }
        }
    }

    /**
     * When creating a new altar, check if the capstone item has stored SE and restore it.
     */
    public static int[] extractStoredSE(net.minecraft.world.item.ItemStack capstoneItem) {
        if (capstoneItem == null || capstoneItem.isEmpty()) return null;
        var customData = capstoneItem.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        var nbt = customData.copyTag();
        if (nbt.contains(SE_TAG)) {
            return new int[]{nbt.getIntOr(SE_TAG, 0), nbt.getIntOr(HW_TAG, 0)};
        }
        return null;
    }
}
