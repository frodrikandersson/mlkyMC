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
            player.level().getServer().execute(() -> {
                GhostDonationMenu.open(ghostPlayer, altarRef, altarManager);
            });
            return;
        }

        // Any player with Milky Stars (from held, jar, shulker, bundle, inventory): donate
        int available = com.mlkymc.economy.MilkyStar.count(player);
        if (available > 0 && player.isShiftKeyDown()) {
            int spaceLeft = SoulAltarManager.MAX_ALTAR_SE - altar.storedSE;
            if (spaceLeft <= 0) {
                player.sendSystemMessage(Component.literal("Altar is full! (100,000 SE)").withColor(0xFF5555));
                return;
            }
            int starsNeeded = (int) Math.ceil(spaceLeft / 30.0);
            int starsUsed = Math.min(available, starsNeeded);
            com.mlkymc.economy.MilkyStar.remove(player, starsUsed);
            int gained = altarManager.depositMilkyStars(altar, player.getName().getString(), starsUsed);
            player.sendSystemMessage(Component.literal(
                    "Donated " + starsUsed + " Milky Stars (+" + gained + " SE)")
                    .withColor(0x55FF55));
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1.0f, 1.2f);
            return;
        }

        // Non-owner: show altar info
        if (!altar.ownerUuid.equals(player.getUUID())) {
            int tier = altar.highWaterSE >= 100_000 ? 4 : altar.highWaterSE >= 75_000 ? 3
                    : altar.highWaterSE >= 50_000 ? 2 : altar.highWaterSE >= 25_000 ? 1 : 0;
            player.sendSystemMessage(Component.literal("--- Soul Altar ---").withColor(0xAA55FF));
            player.sendSystemMessage(Component.literal("Owner: " + altar.ownerName).withColor(0xFFFFFF));
            player.sendSystemMessage(Component.literal("Tier: " + tier + " | SE: " + altar.storedSE + " / 100,000").withColor(0xAAAAAA));
            player.sendSystemMessage(Component.literal("Ghosts connected: " + altar.connectedGhosts.size() + " / 3").withColor(0xAAAAAA));
            player.sendSystemMessage(Component.literal("Shift + right-click with Milky Stars to donate.").withColor(0x777777));
            return;
        }

        // Owner: open altar management GUI
        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new SoulAltarMenu(containerId, playerInv, player, altar),
                Component.literal("Soul Altar")
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
                altarManager.removeAltar(altar);
                if (ownerPlayer != null) {
                    MlkyMC.getClassManager().sendSoulSync(ownerPlayer);
                }
                player.sendSystemMessage(Component.literal(
                        "Soul Altar destroyed! Stored SE has been lost.").withColor(0xFF5555));
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
}
