package com.mlkymc.compat;

import com.mrcrayfish.furniture.refurbished.blockentity.IComputer;
import com.mrcrayfish.furniture.refurbished.computer.Program;
import com.mlkymc.MlkyMC;
import com.mlkymc.market.MarketMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;

/**
 * MrCrayfish Computer program that opens the mlkymc Marketplace GUI.
 *
 * <p>When the player clicks the mlkymc Marketplace icon on the Computer desktop,
 * this program launches on the server. On its first tick it opens the standard
 * {@link MarketMenu} as a chest-style container menu for the player, then closes
 * itself. The Computer screen is replaced by the market GUI.
 */
public class MlkymcMarketProgram extends Program {

    // Uses the same ID as MrCrayfish's built-in Marketplace so we replace it in-place
    public static final Identifier ID = Identifier.parse("refurbished_furniture:marketplace");

    private boolean opened = false;

    public MlkymcMarketProgram(Identifier id, IComputer computer) {
        super(id, computer);
    }

    @Override
    public Component getTitle() {
        return Component.literal("mlkyMC Market");
    }

    @Override
    public void tick() {
        if (opened) return;
        opened = true;

        if (!getComputer().isServer()) return;
        var user = getComputer().getUser();
        if (!(user instanceof ServerPlayer sp)) return;

        var manager = MlkyMC.getMarketManager();
        if (manager == null) return;

        sp.openMenu(new SimpleMenuProvider(
                (containerId, inv, p) -> MarketMenu.computerMarketplace(containerId, inv, sp, manager, 0),
                Component.literal("Marketplace")
        ));
    }
}
