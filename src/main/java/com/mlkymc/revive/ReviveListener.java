package com.mlkymc.revive;

import com.mlkymc.classes.ClassManager;
import com.mlkymc.classes.ClassData;
import com.mlkymc.classes.ProfessionType;
import com.mlkymc.ghost.GhostManager;
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

import java.util.*;

/**
 * Handles interaction near the revive statue.
 *
 * Ghost: GUI with self-revive option (resets ALL class levels, spawn at statue).
 * Alive player: GUI with options to resurrect a ghost (totem + half class levels) or reset own class.
 * Both require confirmation to prevent accidents.
 */
public class ReviveListener {

    private final GhostManager ghostManager;
    private final ReviveManager reviveManager;
    private final ClassManager classManager;

    // Track confirmation state per player
    private final Map<UUID, String> pendingConfirm = new HashMap<>();
    private final Map<UUID, Long> confirmExpiry = new HashMap<>();

    // Pending ghost resurrection requests: ghostUUID -> PendingRevive
    private final Map<UUID, PendingRevive> pendingRevives = new HashMap<>();

    private record PendingRevive(UUID reviverUUID, UUID ghostUUID, long expiryTick) {}

    /** Called from server tick to check for expired pending revives. Pass the server reference. */
    public void tickPendingRevives(long gameTick, net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        var it = pendingRevives.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            if (gameTick > entry.getValue().expiryTick) {
                it.remove();
                ServerPlayer reviver = server.getPlayerList().getPlayer(entry.getValue().reviverUUID);
                if (reviver != null) {
                    reviver.sendSystemMessage(Component.literal(
                            "Resurrection request timed out — ghost didn't respond.").withColor(0xFF5555));
                }
            }
        }
    }

    /** Check if a ghost has a pending revive request. */
    public PendingRevive getPendingRevive(UUID ghostUUID) {
        return pendingRevives.get(ghostUUID);
    }

    /** Ghost accepts the revive. */
    public void acceptRevive(ServerPlayer ghost) {
        PendingRevive pending = pendingRevives.remove(ghost.getUUID());
        if (pending == null) return;

        net.minecraft.server.MinecraftServer server = ghost.level().getServer();
        if (server == null) return;
        ServerPlayer reviver = server.getPlayerList().getPlayer(pending.reviverUUID);
        if (reviver == null) {
            ghost.sendSystemMessage(Component.literal("The reviver is no longer online.").withColor(0xFF5555));
            return;
        }

        executeGhostRevive(reviver, ghost);
    }

    /** Ghost denies the revive. */
    public void denyRevive(ServerPlayer ghost) {
        PendingRevive pending = pendingRevives.remove(ghost.getUUID());
        if (pending == null) return;

        ghost.sendSystemMessage(Component.literal("You denied the resurrection.").withColor(0xAAAAAA));

        net.minecraft.server.MinecraftServer server = ghost.level().getServer();
        if (server != null) {
            ServerPlayer reviver = server.getPlayerList().getPlayer(pending.reviverUUID);
            if (reviver != null) {
                reviver.sendSystemMessage(Component.literal(
                        ghost.getName().getString() + " denied the resurrection request.").withColor(0xFF5555));
            }
        }
    }

    public ReviveListener(GhostManager ghostManager, ReviveManager reviveManager,
                          ClassManager classManager, GraveManager graveManager) {
        this.ghostManager = ghostManager;
        this.reviveManager = reviveManager;
        this.classManager = classManager;
    }

    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        tryOpenStatueGUI(player);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;
        if (tryOpenStatueGUI(player)) {
            event.setCanceled(true);
        }
    }

    private boolean tryOpenStatueGUI(ServerPlayer player) {
        var statueLoc = reviveManager.getStatueLocation();
        if (statueLoc == null) return false;
        if (!player.level().dimension().identifier().toString().equals(statueLoc.dimension)) return false;

        double dist = player.distanceToSqr(statueLoc.x, statueLoc.y, statueLoc.z);
        if (dist > 25.0) return false;

        if (ghostManager.isGhost(player.getUUID())) {
            openGhostGUI(player);
        } else {
            openAliveGUI(player);
        }
        return true;
    }

    // =========================================================================
    // GHOST GUI: Self-revive with full class reset
    // =========================================================================

    private void openGhostGUI(ServerPlayer ghost) {
        com.mlkymc.ghost.GhostListener.allowNextMenu(ghost.getUUID());
        SimpleContainer container = new SimpleContainer(9);

        // Slot 3: Info skull
        ItemStack info = new ItemStack(Items.SKELETON_SKULL);
        info.set(DataComponents.CUSTOM_NAME, Component.literal("Resurrection Altar").withColor(0xFFAA00));
        info.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Resurrecting here will:").withColor(0xAAAAAA),
                Component.literal("  - Reset ALL class levels to 0").withColor(0xFF5555),
                Component.literal("  - Reset your chosen class").withColor(0xFF5555),
                Component.literal("  - Destroy your grave and items").withColor(0xFF5555),
                Component.literal("You will start over from zero.").withColor(0xFFAA00)
        )));
        container.setItem(3, info);

        // Slot 5: Resurrect button (green wool)
        ItemStack resurrectBtn = new ItemStack(Items.LIME_WOOL);
        resurrectBtn.set(DataComponents.CUSTOM_NAME, Component.literal("Resurrect (Reset All Levels)").withColor(0x55FF55));
        resurrectBtn.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Click to resurrect.").withColor(0xAAAAAA),
                Component.literal("Requires confirmation.").withColor(0xFFAA00)
        )));
        container.setItem(5, resurrectBtn);

        ghost.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        MenuType.GENERIC_9x1, containerId, playerInv, container, 1) {

                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
                        if (!(clicker instanceof ServerPlayer sp)) return;

                        if (slotId == 5) {
                            UUID uuid = sp.getUUID();
                            String pending = pendingConfirm.get(uuid);
                            Long expiry = confirmExpiry.get(uuid);
                            long now = sp.level().getGameTime();

                            if ("ghost_revive".equals(pending) && expiry != null && now <= expiry) {
                                // Confirmed! Execute revive
                                pendingConfirm.remove(uuid);
                                confirmExpiry.remove(uuid);
                                executeGhostSelfRevive(sp);
                                sp.closeContainer();
                            } else {
                                // First click: set pending
                                pendingConfirm.put(uuid, "ghost_revive");
                                confirmExpiry.put(uuid, now + 100); // 5 second window

                                // Update button to confirm state
                                ItemStack confirmBtn = new ItemStack(Items.RED_WOOL);
                                confirmBtn.set(DataComponents.CUSTOM_NAME,
                                        Component.literal("ARE YOU SURE? Click again to confirm!").withColor(0xFF5555));
                                confirmBtn.set(DataComponents.LORE, new ItemLore(List.of(
                                        Component.literal("ALL class levels will be lost!").withColor(0xFF5555),
                                        Component.literal("This cannot be undone!").withColor(0xFF5555)
                                )));
                                container.setItem(5, confirmBtn);
                            }
                        }
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                Component.literal("Resurrection Altar")
        ));
    }

    private void executeGhostSelfRevive(ServerPlayer ghost) {
        // Keep the grave — the revived player can go back and claim their items

        // Revive from ghost state
        ghostManager.revivePlayer(ghost);

        // Reset ALL class levels and XP
        classManager.resetPlayer(ghost.getUUID());
        classManager.sendLevelSync(ghost);

        // Teleport to statue
        var statueLoc = reviveManager.getStatueLocation();
        if (statueLoc != null) {
            ServerLevel statDim = ghost.level().getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(statueLoc.dimension)));
            if (statDim != null) {
                ghost.teleportTo(statDim, statueLoc.x, statueLoc.y, statueLoc.z,
                        java.util.Set.of(), statueLoc.yaw, statueLoc.pitch, false);
            }
        }

        ghost.setHealth(ghost.getMaxHealth());
        ghost.sendSystemMessage(Component.literal("You have been resurrected!").withColor(0x55FF55));
        ghost.sendSystemMessage(Component.literal("All class levels reset to 0. Press [K] to choose a new class.").withColor(0xFFAA00));

        if (ghost.level() instanceof ServerLevel sl) {
            sl.playSound(null, ghost.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    // =========================================================================
    // ALIVE PLAYER GUI: Resurrect ghost (totem + half levels) or reset self
    // =========================================================================

    private void openAliveGUI(ServerPlayer player) {
        SimpleContainer container = new SimpleContainer(9);

        // Slot 2: Resurrect a Ghost button
        List<ServerPlayer> ghosts = ghostManager.getOnlineGhosts();
        ItemStack resurrectBtn = new ItemStack(!ghosts.isEmpty() ? Items.TOTEM_OF_UNDYING : Items.GRAY_WOOL);
        resurrectBtn.set(DataComponents.CUSTOM_NAME,
                Component.literal("Resurrect a Ghost").withColor(!ghosts.isEmpty() ? 0x55FF55 : 0xAAAAAA));
        List<Component> resurrectLore = new java.util.ArrayList<>();
        if (ghosts.isEmpty()) {
            resurrectLore.add(Component.literal("No ghosts online").withColor(0xFF5555));
        } else {
            resurrectLore.add(Component.literal("Cost: 50% of your class levels").withColor(0xFFAA00));
            resurrectLore.add(Component.literal("+ 50% of the ghost's class levels").withColor(0xFFAA00));
            resurrectLore.add(Component.literal(ghosts.size() + " ghost(s) available").withColor(0xAAAAAA));
        }
        resurrectBtn.set(DataComponents.LORE, new ItemLore(resurrectLore));
        container.setItem(2, resurrectBtn);

        // Slot 4: Info
        ItemStack info = new ItemStack(Items.SKELETON_SKULL);
        info.set(DataComponents.CUSTOM_NAME, Component.literal("Resurrection Altar").withColor(0xFFAA00));
        info.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Resurrect a ghost:").withColor(0xAAAAAA),
                Component.literal("  Both you and the ghost lose").withColor(0xFFAA00),
                Component.literal("  50% of all class levels.").withColor(0xFFAA00),
                Component.empty(),
                Component.literal("Reset your class:").withColor(0xAAAAAA),
                Component.literal("  Choose a new class.").withColor(0x55FF55),
                Component.literal("  Resets ALL levels to 0.").withColor(0xFF5555)
        )));
        container.setItem(4, info);

        // Slot 6: Reset own class
        ItemStack resetBtn = new ItemStack(Items.BARRIER);
        resetBtn.set(DataComponents.CUSTOM_NAME, Component.literal("Reset My Class").withColor(0xFF5555));
        resetBtn.set(DataComponents.LORE, new ItemLore(List.of(
                Component.literal("Resets ALL class levels to 0.").withColor(0xFF5555),
                Component.literal("You will choose a new class.").withColor(0xFFAA00),
                Component.literal("Requires confirmation.").withColor(0xAAAAAA)
        )));
        container.setItem(6, resetBtn);

        final List<ServerPlayer> ghostList = List.copyOf(ghosts);

        player.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        MenuType.GENERIC_9x1, containerId, playerInv, container, 1) {

                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
                        if (!(clicker instanceof ServerPlayer sp)) return;
                        UUID uuid = sp.getUUID();
                        String pending = pendingConfirm.get(uuid);
                        Long expiry = confirmExpiry.get(uuid);
                        long now = sp.level().getGameTime();

                        if (slotId == 2) {
                            // Resurrect a ghost (no totem needed — cost is halving levels)
                            if (ghostList.isEmpty()) {
                                sp.sendSystemMessage(Component.literal("No ghosts online.").withColor(0xAAAAAA));
                                return;
                            }
                            sp.closeContainer();
                            openGhostSelectionGUI(sp, ghostList);

                        } else if (slotId == 6) {
                            // Reset own class
                            if ("self_reset".equals(pending) && expiry != null && now <= expiry) {
                                pendingConfirm.remove(uuid);
                                confirmExpiry.remove(uuid);
                                executeSelfReset(sp);
                                sp.closeContainer();
                            } else {
                                pendingConfirm.put(uuid, "self_reset");
                                confirmExpiry.put(uuid, now + 100); // 5 second window

                                ItemStack confirmBtn = new ItemStack(Items.RED_WOOL);
                                confirmBtn.set(DataComponents.CUSTOM_NAME,
                                        Component.literal("ARE YOU SURE? Click again!").withColor(0xFF5555));
                                confirmBtn.set(DataComponents.LORE, new ItemLore(List.of(
                                        Component.literal("ALL class levels will be lost!").withColor(0xFF5555),
                                        Component.literal("This cannot be undone!").withColor(0xFF5555)
                                )));
                                container.setItem(6, confirmBtn);
                            }
                        }
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                Component.literal("Resurrection Altar")
        ));
    }

    // =========================================================================
    // Ghost selection GUI (after clicking "Resurrect a Ghost")
    // =========================================================================

    private void openGhostSelectionGUI(ServerPlayer reviver, List<ServerPlayer> ghosts) {
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
                    Component.literal("Click to select").withColor(0x55FF55),
                    Component.literal("Cost: half your class levels").withColor(0xFFAA00)
            )));
            container.setItem(i, head);
        }

        var menuType = switch (rows) {
            case 1 -> MenuType.GENERIC_9x1;
            case 2 -> MenuType.GENERIC_9x2;
            default -> MenuType.GENERIC_9x3;
        };

        final List<ServerPlayer> ghostList = List.copyOf(ghosts);

        reviver.openMenu(new SimpleMenuProvider(
                (containerId, playerInv, p) -> new net.minecraft.world.inventory.ChestMenu(
                        menuType, containerId, playerInv, container, rows) {

                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player clicker) {
                        if (slotId < 0 || slotId >= ghostList.size()) return;
                        if (!(clicker instanceof ServerPlayer sp)) return;

                        ServerPlayer ghostToRevive = ghostList.get(slotId);
                        UUID uuid = sp.getUUID();
                        String pending = pendingConfirm.get(uuid);
                        Long expiry = confirmExpiry.get(uuid);
                        long now = sp.level().getGameTime();

                        String confirmKey = "revive_" + ghostToRevive.getUUID();

                        if (confirmKey.equals(pending) && expiry != null && now <= expiry) {
                            // Confirmed! No totem needed — cost is halving levels
                            pendingConfirm.remove(uuid);
                            confirmExpiry.remove(uuid);

                            // Store pending revive (20 second timeout = 400 ticks)
                            pendingRevives.put(ghostToRevive.getUUID(),
                                    new PendingRevive(sp.getUUID(), ghostToRevive.getUUID(), now + 400));

                            sp.sendSystemMessage(Component.literal(
                                    "Resurrection request sent to " + ghostToRevive.getName().getString() +
                                    "! Waiting for their response (20s)...").withColor(0xFFAA00));

                            // Open accept/deny GUI for the ghost
                            openGhostRevivePrompt(ghostToRevive, sp.getName().getString());
                            sp.closeContainer();
                        } else {
                            // First click: show confirmation
                            pendingConfirm.put(uuid, confirmKey);
                            confirmExpiry.put(uuid, now + 100);

                            ItemStack confirmHead = new ItemStack(Items.PLAYER_HEAD);
                            confirmHead.set(DataComponents.PROFILE,
                                    net.minecraft.world.item.component.ResolvableProfile.createResolved(ghostToRevive.getGameProfile()));
                            confirmHead.set(DataComponents.CUSTOM_NAME,
                                    Component.literal("CONFIRM: Revive " + ghostToRevive.getName().getString() + "?").withColor(0xFF5555));

                            // Show what the reviver will lose
                            ClassData reviverData = classManager.getOrCreate(sp);
                            List<Component> costLore = new java.util.ArrayList<>();
                            costLore.add(Component.literal("You will lose:").withColor(0xFF5555));
                            for (ProfessionType prof : ProfessionType.values()) {
                                int lv = reviverData.getLevel(prof);
                                if (lv > 0) {
                                    int newLv = lv / 2;
                                    costLore.add(Component.literal("  - " + prof.name() + ": " + lv + " -> " + newLv).withColor(0xFFAA00));
                                }
                            }
                            costLore.add(Component.empty());
                            costLore.add(Component.literal(ghostToRevive.getName().getString() + " will also lose:").withColor(0xFF5555));
                            ClassData ghostData = classManager.getOrCreate(ghostToRevive);
                            for (ProfessionType prof : ProfessionType.values()) {
                                int lv = ghostData.getLevel(prof);
                                if (lv > 0) {
                                    int newLv = lv / 2;
                                    costLore.add(Component.literal("  - " + prof.name() + ": " + lv + " -> " + newLv).withColor(0xFFAA00));
                                }
                            }
                            costLore.add(Component.empty());
                            costLore.add(Component.literal("Click again to confirm!").withColor(0xFF5555));
                            confirmHead.set(DataComponents.LORE, new ItemLore(costLore));
                            container.setItem(slotId, confirmHead);
                        }
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                Component.literal("Select Ghost to Revive")
        ));
    }

    /**
     * Opens an Accept/Deny GUI for the ghost receiving a resurrection request.
     */
    private void openGhostRevivePrompt(ServerPlayer ghost, String reviverName) {
        com.mlkymc.ghost.GhostListener.allowNextMenu(ghost.getUUID());
        ghost.sendSystemMessage(Component.literal(
                reviverName + " wants to resurrect you! You will lose 50% of all class levels.")
                .withColor(0xFFAA00));

        SimpleContainer container = new SimpleContainer(9);

        // Slot 2: Info
        ItemStack info = new ItemStack(Items.PAPER);
        info.set(DataComponents.CUSTOM_NAME, Component.literal("Resurrection Request").withColor(0xFFAA00));
        List<Component> infoLore = new ArrayList<>();
        infoLore.add(Component.literal(reviverName + " wants to revive you!").withColor(0xAAAAAA));
        infoLore.add(Component.literal("Cost: 50% of ALL your class levels").withColor(0xFF5555));
        infoLore.add(Component.literal("You have 20 seconds to decide.").withColor(0xAAAAAA));
        info.set(DataComponents.LORE, new ItemLore(infoLore));
        container.setItem(2, info);

        // Slot 4: Accept (green wool)
        ItemStack accept = new ItemStack(Items.LIME_WOOL);
        accept.set(DataComponents.CUSTOM_NAME, Component.literal("ACCEPT").withColor(0x55FF55));
        List<Component> acceptLore = new ArrayList<>();
        acceptLore.add(Component.literal("Accept resurrection").withColor(0xAAAAAA));
        acceptLore.add(Component.literal("You will lose 50% class levels!").withColor(0xFF5555));
        accept.set(DataComponents.LORE, new ItemLore(acceptLore));
        container.setItem(4, accept);

        // Slot 6: Deny (red wool)
        ItemStack deny = new ItemStack(Items.RED_WOOL);
        deny.set(DataComponents.CUSTOM_NAME, Component.literal("DENY").withColor(0xFF5555));
        List<Component> denyLore = new ArrayList<>();
        denyLore.add(Component.literal("Deny resurrection").withColor(0xAAAAAA));
        denyLore.add(Component.literal("Nothing happens.").withColor(0xAAAAAA));
        deny.set(DataComponents.LORE, new ItemLore(denyLore));
        container.setItem(6, deny);

        var self = this;
        ghost.openMenu(new SimpleMenuProvider(
                (syncId, inv, p) -> new net.minecraft.world.inventory.ChestMenu(MenuType.GENERIC_9x1, syncId, inv, container, 1) {
                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player clickPlayer) {
                        if (!(clickPlayer instanceof ServerPlayer sp)) return;
                        if (slotId == 4) {
                            // Accept
                            sp.closeContainer();
                            self.acceptRevive(sp);
                        } else if (slotId == 6) {
                            // Deny
                            sp.closeContainer();
                            self.denyRevive(sp);
                        }
                    }

                    @Override
                    public ItemStack quickMoveStack(Player player, int index) {
                        return ItemStack.EMPTY;
                    }
                },
                Component.literal("Resurrection Request")
        ));
    }

    private void executeGhostRevive(ServerPlayer reviver, ServerPlayer ghost) {
        // Verify still a ghost
        if (!ghostManager.isGhost(ghost.getUUID())) {
            reviver.sendSystemMessage(Component.literal("That player is no longer a ghost!").withColor(0xAAAAAA));
            return;
        }

        // No totem required — the cost is halving both players' levels

        // Halve ALL class levels for the reviver
        ClassData reviverData = classManager.getOrCreate(reviver);
        reviverData.halveLevels();
        classManager.save();
        classManager.sendLevelSync(reviver);

        // Keep the grave — the revived player needs to go back and claim their items

        // Revive the ghost and halve their class levels too
        ghostManager.revivePlayer(ghost);
        ClassData ghostData = classManager.getOrCreate(ghost);
        ghostData.halveLevels();
        classManager.save();
        classManager.sendLevelSync(ghost);
        ghost.setHealth(ghost.getMaxHealth());

        // Teleport ghost to statue
        var statueLoc = reviveManager.getStatueLocation();
        if (statueLoc != null) {
            ServerLevel statDim = reviver.level().getServer().getLevel(
                    ResourceKey.create(Registries.DIMENSION, Identifier.parse(statueLoc.dimension)));
            if (statDim != null) {
                ghost.teleportTo(statDim, statueLoc.x, statueLoc.y, statueLoc.z,
                        java.util.Set.of(), statueLoc.yaw, statueLoc.pitch, false);
            }
        }

        // Sync
        classManager.sendLevelSync(ghost);

        ghost.sendSystemMessage(Component.literal(
                "You have been resurrected by " + reviver.getName().getString() + "! Your class levels have been halved.").withColor(0x55FF55));
        reviver.sendSystemMessage(Component.literal(
                "Resurrected " + ghost.getName().getString() + "! Your class levels have been halved.").withColor(0x55FF55));

        if (reviver.level() instanceof ServerLevel sl) {
            sl.playSound(null, reviver.blockPosition(), SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        // Cleric XP for reviving
        classManager.addXp(reviver, ProfessionType.CLERIC, 50, "revive player");
    }

    private void executeSelfReset(ServerPlayer player) {
        classManager.resetPlayer(player.getUUID());
        player.sendSystemMessage(Component.literal("[MLKYMC_SYNC:NONE]").withColor(0x000000));
        classManager.sendLevelSync(player);

        player.sendSystemMessage(Component.literal("All class levels reset to 0. Press [K] to choose a new class.").withColor(0xFFAA00));

        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.5f, 1.0f);
        }
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
