package com.mlkymc.revive;

import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import com.mlkymc.ghost.GhostManager;
import com.mlkymc.grave.GraveData;
import com.mlkymc.grave.GraveManager;
import com.mlkymc.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Handles interaction near the revive statue.
 *
 * Ghost: right-click near statue to self-revive (resets class levels, choose new class).
 * Alive player: right-click near statue to open GUI to revive online ghosts.
 *   - Requires Totem of Resurrection
 *   - Non-Cleric: costs 50% player XP (min 10 levels)
 *   - Cleric: no XP cost
 *   - Revived ghost keeps class levels and receives grave items
 */
public class ReviveListener {

    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;
    private final ClassManager classManager;
    private final GraveManager graveManager;

    public ReviveListener(GhostManager ghostManager, ReviveManager reviveManager,
                          ClassManager classManager, GraveManager graveManager) {
        this.ghostManager = ghostManager;
        this.reviveManager = reviveManager;
        this.classManager = classManager;
        this.graveManager = graveManager;
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Check if near statue
        var statueLoc = reviveManager.getStatueLocation();
        if (statueLoc == null) return;
        if (!player.level().dimension().identifier().toString().equals(statueLoc.dimension)) return;

        double dist = player.distanceToSqr(statueLoc.x, statueLoc.y, statueLoc.z);
        if (dist > 25.0) return; // Within 5 blocks

        if (ghostManager.isGhost(player.getUUID())) {
            handleGhostSelfRevive(player);
        } else {
            handleAlivePlayerRevive(player);
        }
    }

    // =========================================================================
    // GHOST: Self-revive at statue (resets class, fresh start)
    // =========================================================================

    private void handleGhostSelfRevive(ServerPlayer ghost) {
        ghostManager.revivePlayer(ghost);
        classManager.resetPlayer(ghost.getUUID());

        // Sync class reset to client
        ghost.sendSystemMessage(Component.literal("[MLKYMC_SYNC:NONE]").withColor(0x000000));

        ghost.setHealth(ghost.getMaxHealth());
        ghost.sendSystemMessage(Component.literal("You have been resurrected at the altar!").withColor(0x55FF55));
        ghost.sendSystemMessage(Component.literal("Your class levels have been reset to 0.").withColor(0xFFAA00));
        ghost.sendSystemMessage(Component.literal("Press [K] to choose a new class.").withColor(0x55FFFF));

        if (ghost.level() instanceof ServerLevel sl) {
            sl.playSound(null, ghost.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    // =========================================================================
    // ALIVE PLAYER: Revive a ghost via GUI (totem + XP cost)
    // =========================================================================

    private void handleAlivePlayerRevive(ServerPlayer reviver) {
        // Check for Totem of Resurrection
        int totemSlot = findTotemSlot(reviver);
        if (totemSlot == -1) {
            reviver.sendSystemMessage(Component.literal(
                    "You need a Totem of Resurrection to revive players here!").withColor(0xFF5555));
            return;
        }

        // Get online ghosts
        List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
        if (ghosts.isEmpty()) {
            reviver.sendSystemMessage(Component.literal("No ghosts online to revive.").withColor(0xAAAAAA));
            return;
        }

        // Open selection GUI (chest menu with ghost player heads)
        int rows = Math.min(3, Math.max(1, (int) Math.ceil(ghosts.size() / 9.0)));
        int slotCount = rows * 9;

        SimpleContainer container = new SimpleContainer(slotCount);

        for (int i = 0; i < ghosts.size() && i < slotCount; i++) {
            ServerPlayer ghost = ghosts.get(i);
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE,
                    net.minecraft.world.item.component.ResolvableProfile.createResolved(ghost.getGameProfile()));
            head.set(DataComponents.CUSTOM_NAME,
                    Component.literal(ghost.getName().getString()).withColor(0xAAAAAA));
            head.set(DataComponents.LORE, new ItemLore(List.of(
                    Component.literal("Click to revive").withColor(0x55FF55)
            )));
            container.setItem(i, head);
        }

        final int finalTotemSlot = totemSlot;
        final List<ServerPlayer> ghostList = List.copyOf(ghosts);

        var menuType = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            default -> MenuType.GENERIC_9x3;
        };

        reviver.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        menuType, containerId, playerInv, container, rows) {

                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
                        if (slotId < 0 || slotId >= ghostList.size()) return;
                        if (!(clicker instanceof ServerPlayer reviverPlayer)) return;

                        ServerPlayer ghostToRevive = ghostList.get(slotId);

                        // Verify still a ghost
                        if (!ghostManager.isGhost(ghostToRevive.getUUID())) {
                            reviverPlayer.sendSystemMessage(Component.literal(
                                    "That player is no longer a ghost!").withColor(0xAAAAAA));
                            reviverPlayer.closeContainer();
                            return;
                        }

                        // Consume totem
                        var totemStack = reviverPlayer.getInventory().getItem(finalTotemSlot);
                        if (!totemStack.is(ModItems.TOTEM_OF_RESURRECTION.get())) {
                            reviverPlayer.sendSystemMessage(Component.literal(
                                    "Totem not found!").withColor(0xFF5555));
                            reviverPlayer.closeContainer();
                            return;
                        }
                        totemStack.shrink(1);

                        // XP cost: non-Cleric pays 50% XP (min 10 levels)
                        var data = classManager.getOrCreate(reviverPlayer);
                        if (data.getChosenClass() != ClassType.CLERIC) {
                            int xpCost = Math.max(10, reviverPlayer.experienceLevel / 2);
                            reviverPlayer.giveExperienceLevels(-xpCost);
                            reviverPlayer.sendSystemMessage(Component.literal(
                                    "Used " + xpCost + " XP levels to resurrect.").withColor(0xFFAA00));
                        }

                        // Revive the ghost (keeps class levels!)
                        ghostManager.revivePlayer(ghostToRevive);
                        ghostToRevive.setHealth(ghostToRevive.getMaxHealth());

                        // Return grave items and remove the grave
                        GraveData grave = graveManager.getGraveByOwner(ghostToRevive.getUUID());
                        if (grave != null) {
                            ServerLevel graveDim = reviverPlayer.level().getServer().getLevel(
                                    ResourceKey.create(Registries.DIMENSION,
                                            Identifier.parse(grave.dimension)));
                            if (graveDim != null) {
                                graveManager.claimGrave(ghostToRevive, grave, graveDim);
                            }
                        }

                        // Sync class data to revived player
                        classManager.sendLevelSync(ghostToRevive);

                        // Teleport ghost to statue
                        var statueLoc = reviveManager.getStatueLocation();
                        if (statueLoc != null) {
                            ServerLevel statDim = reviverPlayer.level().getServer().getLevel(
                                    ResourceKey.create(Registries.DIMENSION,
                                            Identifier.parse(statueLoc.dimension)));
                            if (statDim != null) {
                                ghostToRevive.teleportTo(statDim, statueLoc.x, statueLoc.y, statueLoc.z,
                                        java.util.Set.of(), statueLoc.yaw, statueLoc.pitch, false);
                            }
                        }

                        ghostToRevive.sendSystemMessage(Component.literal(
                                "You have been resurrected by " + reviverPlayer.getName().getString() + "!")
                                .withColor(0x55FF55));
                        ghostToRevive.sendSystemMessage(Component.literal(
                                "Your items have been returned.").withColor(0x55FFFF));

                        reviverPlayer.sendSystemMessage(Component.literal(
                                "Resurrected " + ghostToRevive.getName().getString() + "!")
                                .withColor(0x55FF55));

                        if (reviverPlayer.level() instanceof ServerLevel sl) {
                            sl.playSound(null, reviverPlayer.blockPosition(),
                                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                        }

                        // Cleric XP for reviving
                        classManager.addXp(reviverPlayer, ProfessionType.CLERIC, 50);

                        reviverPlayer.closeContainer();
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                Component.literal("Resurrect a Player")
        ));
    }

    private int findTotemSlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).is(ModItems.TOTEM_OF_RESURRECTION.get())) {
                return i;
            }
        }
        return -1;
    }
}
