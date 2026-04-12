package com.mlkymc.guide;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.ArrayList;

/**
 * Creates and gives the mlkyMC Guidebook — a written book explaining all
 * of the mod's systems. Given on first join and re-obtainable via /mlkymc guide.
 */
public final class GuidebookHelper {

    private GuidebookHelper() {}

    public static ItemStack createGuidebook() {
        var pages = new ArrayList<Filterable<Component>>();

        // ---- Page 1: Welcome ----
        pages.add(page(
                "Welcome to mlkyMC!\n\n" +
                "This book explains the core systems of the server.\n\n" +
                "Lost this book? Use:\n" +
                "  /mlkymc guide\n\n" +
                "Press [K] to open the\nClass Selection screen."
        ));

        // ---- Page 2: Classes Overview ----
        pages.add(page(
                "CLASS SYSTEM\n\n" +
                "Choose 1 of 5 classes.\nYour choice is permanent.\n\n" +
                "Adventurer - Combat\n" +
                "Cleric - Healing\n" +
                "Farmhand - Farming\n" +
                "MineCrafter - Mining\n" +
                "Smith - Smelting\n\n" +
                "Each class has unique\nskills, passives, and\ncraftable items."
        ));

        // ---- Page 3: Debuff System ----
        pages.add(page(
                "DEBUFF SYSTEM\n\n" +
                "All non-chosen class\nprofessions start with\na -50% debuff.\n\n" +
                "Level up any profession\nto reduce its debuff.\n\n" +
                "Choosing a class zeroes\nthat class's debuff\nimmediately."
        ));

        // ---- Page 4: Soul Fracture ----
        pages.add(page(
                "SOUL FRACTURE\n\n" +
                "Applied when a Cleric\nuses free resurrection\n(within 5 minutes).\n\n" +
                "Per stack (max 5):\n" +
                "  -1 max HP\n" +
                "  -15% XP gain\n\n" +
                "Does NOT decay with\ntime or sleep.\n\n" +
                "Cure: Transmutation\nCauldron (next page)."
        ));

        // ---- Page 5: Transmutation ----
        pages.add(page(
                "TRANSMUTATION CAULDRON\n\n" +
                "Requires: Cleric\nprofession Lv30\n(any chosen class!)\n\n" +
                "How to use:\n" +
                "1. Fill cauldron with\n   water\n" +
                "2. Drop items in (Q key)\n" +
                "3. Jump in and stand\n" +
                "4. Recipe fires in ~1s\n\n" +
                "Sneak in cauldron to\nretrieve mis-dropped\nitems."
        ));

        // ---- Page 6: Transmutation Recipes ----
        pages.add(page(
                "TRANSMUTATION RECIPES\n\n" +
                "Soul Cleanse (Lv30):\n" +
                "  64 Milky Stars\n" +
                "  = -1 Soul Fracture\n\n" +
                "Tempered Plate (Lv30):\n" +
                "  16 Raw Iron +\n" +
                "  16 Milky Stars\n\n" +
                "Living Essence (Lv30):\n" +
                "  16 Wheat +\n" +
                "  16 Milky Stars"
        ));

        // ---- Page 7: More Recipes ----
        pages.add(page(
                "MORE RECIPES\n\n" +
                "Waystone Shard (Lv30):\n" +
                "  8 Bone + 8 Rotten\n" +
                "  Flesh + 8 Stars\n\n" +
                "Resonant Core (Lv35):\n" +
                "  32 Redstone + 1 Ender\n" +
                "  Pearl + 8 Stars\n\n" +
                "Blessed Ember (Lv45):\n" +
                "  4 Diamonds +\n" +
                "  32 Milky Stars"
        ));

        // ---- Page 8: Soul Altar ----
        pages.add(page(
                "SOUL ALTAR\n(Cleric exclusive)\n\n" +
                "Bottom layer (3x3):\n" +
                "  8 Soulstone Brick edges\n" +
                "  1 Conduit Core center\n\n" +
                "Top layer:\n" +
                "  4 Soul Pillars corners\n" +
                "  1 Capstone center\n\n" +
                "Right-click Capstone\nto activate.\nMax altar SE: 100,000"
        ));

        // ---- Page 9: Elite Mobs ----
        pages.add(page(
                "ELITE MOBS\n\n" +
                "Some hostile mobs spawn\nas elites with special\nabilities. Glow + colored\nname = elite.\n\n" +
                "Types include:\n" +
                "Blight - 20% HP dmg\n" +
                "Volatile - Explodes\n" +
                "Binder - Slows you\n" +
                "Stormcaller - Lightning\n" +
                "Pack Leader - Spawns\n" +
                "  minion swarm"
        ));

        // ---- Page 10: More Elites ----
        pages.add(page(
                "MORE ELITES\n\n" +
                "Watcher - Buffs allies\n" +
                "Leech - Heals from death\n" +
                "Necromancer - Raises dead\n" +
                "Shielded - Tanky + armor\n" +
                "Berserker - Enrages as\n" +
                "  HP drops\n" +
                "Corrosive - Melts armor\n" +
                "Splitter - Splits on death\n" +
                "Screamer - Rallies mobs\n" +
                "Poisonous - Poison trail\n" +
                "Enraged - Buffs mobs\n" +
                "  on death"
        ));

        // ---- Page 11: Horse Buffs ----
        pages.add(page(
                "HORSE BUFF SYSTEM\n\n" +
                "Farmhand Lv30+ can buff\nhorses with enhanced food.\n\n" +
                "How:\n" +
                "  Sneak + right-click\n" +
                "  horse with buffed food\n\n" +
                "Transfers: Health,\nSpeed, Jump only.\n\n" +
                "Breeding: foals inherit\n50% of parent buffs\npermanently. Fades over\ngenerations."
        ));

        // ---- Page 12: Armor Debuffs ----
        pages.add(page(
                "ARMOR ENCUMBRANCE\n\n" +
                "Diamond (full set):\n" +
                "  -4% move speed\n" +
                "  -4% attack speed\n" +
                "  -8% mining speed\n\n" +
                "Netherite (full set):\n" +
                "  -10% move speed\n" +
                "  -8% attack speed\n" +
                "  -20% mining speed\n\n" +
                "Netherite keeps fire\nimmunity as tradeoff.\nSwap to iron for mining!"
        ));

        // ---- Page 13: Computer Marketplace ----
        pages.add(page(
                "COMPUTER MARKETPLACE\n\n" +
                "MrCrayfish Computer block\nhas a Marketplace app.\n\n" +
                "Buy/sell from anywhere!\n\n" +
                "Requirements:\n" +
                "  - Mailbox with your\n    exact player name\n" +
                "  - 1 Milky Star fee\n    per transaction\n\n" +
                "Items delivered to your\nMailbox automatically."
        ));

        // ---- Page 14: Fletcher's Forge ----
        pages.add(page(
                "FLETCHER'S FORGE\n(MineCrafter exclusive)\n\n" +
                "Right-click a Fletching\nTable to open.\n\n" +
                "Place Smith-forged gear +\nTempered Plate to apply\na +1% to +30% modifier\non all Smith attributes.\n\n" +
                "Rerolls cost more each\ntime. Higher reroll count\n= better odds at rare\nmodifiers."
        ));

        var bookContent = new WrittenBookContent(
                Filterable.passThrough("mlkyMC Guidebook"),
                "mlkyMC", 0, pages, true
        );

        var book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, bookContent);
        return book;
    }

    private static Filterable<Component> page(String text) {
        return Filterable.passThrough(Component.literal(text));
    }

    /** Give the guidebook to a player, replacing any existing copy. */
    public static void giveGuidebook(ServerPlayer player) {
        var guidebook = createGuidebook();
        var inv = player.getInventory();

        // Replace existing guidebook if found
        for (int i = 0; i < inv.getContainerSize(); i++) {
            var existing = inv.getItem(i);
            if (!existing.isEmpty() && existing.is(Items.WRITTEN_BOOK)) {
                var content = existing.get(DataComponents.WRITTEN_BOOK_CONTENT);
                if (content != null && content.title().raw().equals("mlkyMC Guidebook")) {
                    inv.setItem(i, guidebook);
                    return;
                }
            }
        }

        // No existing copy — add to inventory or drop
        if (!inv.add(guidebook)) {
            player.drop(guidebook, false);
        }
    }
}
