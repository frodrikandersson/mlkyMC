package com.mlkymc.compat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * Integration with MrCrayfish's Refurbished Furniture mod. Uses direct imports
 * (refurbished_furniture is a compileOnly dependency) but all calls are guarded
 * behind {@link #isAvailable()} so mlkymc runs fine without the mod installed.
 *
 * <p>Phase 1: DeliveryService.sendMail — route marketplace items to player mailboxes.
 * <p>Phase 2: Computer.installProgram — register mlkymc Marketplace on the Computer desktop.
 */
public final class FurnitureCompat {

    private static boolean checked = false;
    private static boolean available = false;

    private FurnitureCompat() {}

    /** Returns true if the Refurbished Furniture mod is present. */
    public static boolean isAvailable() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("com.mrcrayfish.furniture.refurbished.mail.DeliveryService");
                available = true;
                com.mlkymc.MlkyMC.LOGGER.info("[FurnitureCompat] Refurbished Furniture detected — mailbox delivery + Computer marketplace enabled");
            } catch (ClassNotFoundException e) {
                available = false;
            }
        }
        return available;
    }

    /**
     * Replace the built-in "Coming Soon" Marketplace program on the Computer desktop
     * with our mlkymc Marketplace that opens the real MarketMenu. Uses reflection to
     * force-replace the entry in Computer's internal programs map, since installProgram
     * uses putIfAbsent and MrCrayfish already registered their placeholder.
     */
    @SuppressWarnings("unchecked")
    public static void registerComputerProgram() {
        if (!isAvailable()) return;
        try {
            var computer = com.mrcrayfish.furniture.refurbished.computer.Computer.get();
            // The built-in Marketplace uses ID "refurbished_furniture:marketplace".
            // We replace that exact entry so the existing icon launches our program.
            var id = net.minecraft.resources.Identifier.parse("refurbished_furniture:marketplace");

            // Force-replace via reflection on the internal programs Map
            var programsField = computer.getClass().getDeclaredField("programs");
            programsField.setAccessible(true);
            var programs = (java.util.Map<net.minecraft.resources.Identifier,
                    java.util.function.BiFunction<net.minecraft.resources.Identifier,
                            com.mrcrayfish.furniture.refurbished.blockentity.IComputer,
                            com.mrcrayfish.furniture.refurbished.computer.Program>>) programsField.get(computer);
            programs.put(id, (programId, comp) -> new MlkymcMarketProgram(programId, comp));

            com.mlkymc.MlkyMC.LOGGER.info("[FurnitureCompat] Replaced built-in Marketplace with mlkymc Marketplace on Computer desktop");
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.warn("[FurnitureCompat] Failed to replace Computer Marketplace program", e);
        }
    }

    /**
     * Check if a player has a mailbox with their exact name placed in the world.
     * Used to gate Computer-based marketplace purchases — buying via Computer
     * requires a mailbox so the items have somewhere to be delivered.
     */
    public static boolean hasNamedMailbox(MinecraftServer server, net.minecraft.server.level.ServerPlayer player) {
        if (!isAvailable() || server == null) return false;
        try {
            var optional = com.mrcrayfish.furniture.refurbished.mail.DeliveryService.get(server);
            if (optional.isEmpty()) return false;
            String playerName = player.getName().getString();
            for (var mailbox : optional.get().getMailboxes()) {
                if (mailbox instanceof com.mrcrayfish.furniture.refurbished.mail.Mailbox m) {
                    if (m.getCustomName().isPresent() && m.getCustomName().get().equals(playerName)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Send an item to a player's mailbox via DeliveryService.
     * Looks up the player's mailbox by matching the owner UUID against all registered
     * mailboxes, then calls sendMail with the mailbox's own UUID (which is what the
     * API actually requires — NOT the player UUID).
     *
     * @return true if delivery succeeded, false if it failed, the mod is absent,
     *         or the player has no mailbox.
     */
    public static boolean sendToMailbox(MinecraftServer server, UUID playerUuid, ItemStack stack) {
        if (!isAvailable() || server == null) return false;
        try {
            var optional = com.mrcrayfish.furniture.refurbished.mail.DeliveryService.get(server);
            if (optional.isEmpty()) return false;

            var service = optional.get();

            // Find the mailbox owned by this player. DeliveryService.sendMail takes the
            // MAILBOX UUID, not the player UUID. We need to iterate registered mailboxes
            // to find one owned by the target player.
            UUID mailboxId = null;
            for (var mailbox : service.getMailboxes()) {
                if (mailbox instanceof com.mrcrayfish.furniture.refurbished.mail.Mailbox m) {
                    if (m.owner().isPresent() && m.owner().get().equals(playerUuid)) {
                        mailboxId = m.getId();
                        break;
                    }
                }
            }
            if (mailboxId == null) return false; // Player has no mailbox

            var result = service.sendMail(mailboxId, stack);
            return result.success();
        } catch (Exception e) {
            com.mlkymc.MlkyMC.LOGGER.warn("[FurnitureCompat] Failed to send mail", e);
            return false;
        }
    }
}
