package com.mlkymc.grave;

import com.mlkymc.MlkyMC;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Handles grave creation on death and grave interaction (right-click to loot).
 */
public class GraveListener {

    private final GraveManager graveManager;

    public GraveListener(GraveManager graveManager) {
        this.graveManager = graveManager;
    }

    // Death handling (grave creation + death sync) is now in GhostListener.onPlayerDeath
    // to avoid duplicate grave creation and double death sync broadcasts.

    /**
     * Prevent left-click breaking of grave head blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = event.getPos();
        String dim = level.dimension().identifier().toString();
        GraveData grave = graveManager.getGrave(dim, pos);
        if (grave != null) {
            event.setCanceled(true);
            player.sendSystemMessage(Component.literal("You can't break a grave! Right-click to interact.").withColor(0xFF5555));
        }
    }

    /**
     * Prevent explosions from destroying grave blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onExplosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String dim = level.dimension().identifier().toString();
        event.getAffectedBlocks().removeIf(pos -> graveManager.getGrave(dim, pos) != null);
    }

    /**
     * Prevent pistons from pushing/pulling grave blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPistonPush(net.neoforged.neoforge.event.level.PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String dim = level.dimension().identifier().toString();

        // Check the block being pushed and adjacent blocks
        var pos = event.getPos().relative(event.getDirection());
        if (graveManager.getGrave(dim, pos) != null) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevent liquid flow from destroying grave blocks.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onFluidPlace(net.neoforged.neoforge.event.level.BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        String dim = level.dimension().identifier().toString();

        if (graveManager.getGrave(dim, event.getPos()) != null) {
            event.setCanceled(true);
        }
    }

    /**
     * Prevent player head drops near grave positions (stops duped heads from water/lava).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onItemSpawn(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;
        if (!(event.getEntity() instanceof net.minecraft.world.entity.item.ItemEntity itemEntity)) return;
        if (!itemEntity.getItem().is(net.minecraft.world.item.Items.PLAYER_HEAD)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        String dim = level.dimension().identifier().toString();
        BlockPos pos = itemEntity.blockPosition();

        // Check if this head dropped near any grave
        for (GraveData grave : graveManager.getAllGraves()) {
            if (!grave.dimension.equals(dim)) continue;
            if (grave.pos.closerToCenterThan(pos.getCenter(), 5)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /**
     * Right-click a grave block to interact.
     * - Owner (alive, not ghost): claim items or remove empty grave
     * - Owner (ghost): cannot interact until revived
     * - Other player: can view/loot after 10 minutes
     */
    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        String dim = level.dimension().identifier().toString();
        GraveData grave = graveManager.getGrave(dim, pos);
        if (grave == null) return;

        event.setCanceled(true);

        boolean isOwner = player.getUUID().equals(grave.ownerUUID);
        long currentTime = level.getGameTime();

        // Ghost owner cannot interact with own grave
        var ghostManager = MlkyMC.getGhostManager();
        if (isOwner && ghostManager != null && ghostManager.isGhost(player.getUUID())) {
            player.sendSystemMessage(Component.literal("You must be revived before you can reclaim your grave.").withColor(0xFF5555));
            return;
        }

        if (isOwner) {
            // Owner: check if grave has items
            if (grave.items.isEmpty() && grave.xpPoints <= 0) {
                // Empty grave — just remove the head block
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                graveManager.removeGrave(dim, pos);
                player.sendSystemMessage(Component.literal("Empty grave removed.").withColor(0xAAAAAA));
            } else {
                // Grave with items — claim everything
                graveManager.claimGrave(player, grave, level);
                player.sendSystemMessage(Component.literal("Grave claimed! Items restored.").withColor(0x55FF55));
            }
            // Clear grave timer from HUD immediately
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("[MLKYMC_GRAVES:]").withColor(0x000000));
            // Force skill sync to remove grave icon — send empty grave state
            // The next full sync will confirm, but this clears it instantly
            player.level().getServer().execute(() -> {
                var ph = MlkyMC.getPowerHandler();
                if (ph != null) {
                    var cm = MlkyMC.getClassManager();
                    var psh = MlkyMC.getPassiveSkillHandler();
                    if (cm != null) {
                        ph.syncSkillStatuses(player.level().getServer(), cm, psh, null);
                    }
                }
            });
        } else if (grave.canOtherPlayerAccess(currentTime)) {
            // Others can loot after 10 minutes — open inventory GUI, head stays
            openGraveLootGUI(player, grave, dim, pos);
        } else {
            long remainingTicks = 12000 - (currentTime - grave.deathTime);
            int remainingSeconds = Math.max(0, (int) (remainingTicks / 20));
            int mins = remainingSeconds / 60;
            int secs = remainingSeconds % 60;
            player.sendSystemMessage(Component.literal("This is " + grave.ownerName + "'s grave. Accessible in " +
                    mins + "m " + secs + "s.").withColor(0xFF5555));
        }
    }

    /**
     * Opens a chest GUI showing the grave's contents for non-owner looting.
     * Items taken from the GUI are removed from the grave data.
     * The grave head block stays in the world.
     */
    private void openGraveLootGUI(ServerPlayer looter, GraveData grave, String dim, BlockPos pos) {
        int itemCount = grave.items.size();
        int rows = Math.min(6, Math.max(1, (itemCount + 8) / 9)); // 1-6 rows

        net.minecraft.world.SimpleContainer container = new net.minecraft.world.SimpleContainer(rows * 9);
        for (int i = 0; i < Math.min(itemCount, rows * 9); i++) {
            container.setItem(i, grave.items.get(i).copy());
        }

        // Listen for changes — when items are taken, update the grave data
        container.addListener(c -> {
            grave.items.clear();
            for (int i = 0; i < c.getContainerSize(); i++) {
                net.minecraft.world.item.ItemStack stack = c.getItem(i);
                if (!stack.isEmpty()) {
                    grave.items.add(stack.copy());
                }
            }
            graveManager.save();
        });

        net.minecraft.world.inventory.MenuType<?> menuType = switch (rows) {
            case 1 -> net.minecraft.world.inventory.MenuType.GENERIC_9x1;
            case 2 -> net.minecraft.world.inventory.MenuType.GENERIC_9x2;
            case 3 -> net.minecraft.world.inventory.MenuType.GENERIC_9x3;
            case 4 -> net.minecraft.world.inventory.MenuType.GENERIC_9x4;
            case 5 -> net.minecraft.world.inventory.MenuType.GENERIC_9x5;
            default -> net.minecraft.world.inventory.MenuType.GENERIC_9x6;
        };

        looter.openMenu(new net.minecraft.world.SimpleMenuProvider(
                (syncId, inv, p) -> new net.minecraft.world.inventory.ChestMenu(menuType, syncId, inv, container, rows),
                net.minecraft.network.chat.Component.literal(grave.ownerName + "'s Grave")
        ));
    }
}
