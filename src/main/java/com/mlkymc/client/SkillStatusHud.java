package com.mlkymc.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unified skill status HUD. Shows active skill icons with timers
 * in the bottom-left, growing rightward.
 *
 * Each skill has a 16x16 icon PNG at textures/gui/skill_<name>.png
 * Active = green timer text, Cooldown = red timer text (under 60s shows seconds).
 * Ready/toggle-on skills show the icon with no timer.
 *
 * Synced via [MLKYMC_SKILLS:name=STATE,name=STATE,...] where STATE is:
 *   A<seconds> = active with duration
 *   C<seconds> = on cooldown
 *   S<stacks>  = stack-based debuff (e.g. Soul Fracture), shows "n/5" label in red
 *   R = ready (show icon, no timer)
 *   ON = toggle active
 *   OFF = toggle off (hide)
 *   X = hidden (don't show)
 */
public class SkillStatusHud {

    private static final int ICON_SIZE = 16;
    private static final int GAP = 3;
    private static final int BASE_X = 34;

    // Ordered map of skill name -> state
    // State: "A30" = active 30s, "C45" = cooldown 45s, "R" = ready, "ON" = toggle on, "X" = hidden
    private static final LinkedHashMap<String, String> skillStates = new LinkedHashMap<>();

    // Timestamp when each skill state was last synced (for client-side countdown)
    private static final Map<String, Long> syncTimeMs = new LinkedHashMap<>();

    @SubscribeEvent
    public void onChatReceived(ClientChatReceivedEvent event) {
        String msg = event.getMessage().getString();
        if (msg.startsWith("[MLKYMC_SKILLS:") && msg.endsWith("]")) {
            String content = msg.substring(15, msg.length() - 1);
            skillStates.clear();
            syncTimeMs.clear();
            long now = System.currentTimeMillis();

            if (!content.isEmpty()) {
                for (String entry : content.split(",")) {
                    int eq = entry.indexOf('=');
                    if (eq > 0) {
                        String name = entry.substring(0, eq);
                        String state = entry.substring(eq + 1);
                        skillStates.put(name, state);
                        syncTimeMs.put(name, now);
                    }
                }
            }
            event.setCanceled(true);
        }

        // Also handle legacy devoted life sync for backwards compat
        if (msg.startsWith("[MLKYMC_DEVOTED:") && msg.endsWith("]")) {
            event.setCanceled(true); // Consumed by new system
        }
    }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (net.minecraft.client.Minecraft.getInstance().options.hideGui) return;
        if (skillStates.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics g = event.getGuiGraphics();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int x = BASE_X;
        int y = screenH - 32;
        long now = System.currentTimeMillis();

        for (var entry : skillStates.entrySet()) {
            String name = entry.getKey();
            String state = entry.getValue();

            if (state.equals("X") || state.equals("OFF")) continue;

            // Calculate remaining seconds (client-side countdown from sync time)
            int displaySeconds = 0;
            boolean isActive = false;
            boolean isCooldown = false;
            boolean isReady = false;
            boolean isToggleOn = false;
            boolean isStackBased = false;
            boolean isNumericBadge = false;
            boolean isLetterTier = false;
            String letterTier = "";
            int stackCount = 0;

            String extraData = null; // For grave coords etc.

            if (state.startsWith("A")) {
                isActive = true;
                try {
                    String activeVal = state.substring(1);
                    int colonIdx = activeVal.indexOf(':');
                    if (colonIdx >= 0) {
                        extraData = activeVal.substring(colonIdx + 1);
                        activeVal = activeVal.substring(0, colonIdx);
                    }
                    int syncedSeconds = Integer.parseInt(activeVal);
                    long elapsed = (now - syncTimeMs.getOrDefault(name, now)) / 1000;
                    displaySeconds = Math.max(0, (int)(syncedSeconds - elapsed));
                } catch (NumberFormatException e) { isActive = false; }
            } else if (state.startsWith("C")) {
                isCooldown = true;
                try {
                    int syncedSeconds = Integer.parseInt(state.substring(1));
                    long elapsed = (now - syncTimeMs.getOrDefault(name, now)) / 1000;
                    displaySeconds = Math.max(0, (int)(syncedSeconds - elapsed));
                    if (displaySeconds == 0) continue; // Cooldown expired, hide
                } catch (NumberFormatException e) { isCooldown = false; }
            } else if (state.startsWith("S")) {
                // Stack-based debuff (Soul Fracture). Shows icon with "n/5" label in red.
                try {
                    stackCount = Integer.parseInt(state.substring(1));
                    if (stackCount <= 0) continue; // zero stacks = hide entirely
                    isStackBased = true;
                } catch (NumberFormatException ignored) { continue; }
            } else if (state.startsWith("N")) {
                // Numeric badge (Milky Curse). Fixed icon + stack number below.
                try {
                    stackCount = Integer.parseInt(state.substring(1));
                    if (stackCount <= 0) continue;
                    isNumericBadge = true;
                } catch (NumberFormatException ignored) { continue; }
            } else if (state.startsWith("L")) {
                // Letter-tier badge (Gear Score). Picks skill_<name>_<letter>.png based on tier.
                String letter = state.substring(1).toLowerCase();
                if (letter.isEmpty()) continue;
                letterTier = letter;
                isLetterTier = true;
            } else if (state.equals("R")) {
                isReady = true;
            } else if (state.equals("ON")) {
                isToggleOn = true;
            }

            // Draw icon — stack-based debuffs swap texture per stack count
            // (e.g. skill_soulfracture_1.png, _2, _3, _4, _5), numeric badges use
            // a single fixed texture, letter-tier badges swap texture per letter
            // (e.g. skill_gearscore_s.png, _a, _b...), other skills use the
            // _cooldown variant when on cooldown if one exists.
            String iconPath;
            if (isStackBased) {
                int clamped = Math.max(1, Math.min(5, stackCount));
                iconPath = "mlkymc:textures/gui/skill_" + name + "_" + clamped + ".png";
            } else if (isNumericBadge) {
                iconPath = "mlkymc:textures/gui/skill_" + name + ".png";
            } else if (isLetterTier) {
                iconPath = "mlkymc:textures/gui/skill_" + name + "_" + letterTier + ".png";
            } else {
                String iconSuffix = isCooldown ? "_cooldown" : "";
                iconPath = "mlkymc:textures/gui/skill_" + name + iconSuffix + ".png";
            }
            Identifier icon = Identifier.parse(iconPath);
            g.blit(RenderPipelines.GUI_TEXTURED, icon, x, y, 0f, 0f, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

            // Draw timer text centered below icon
            if (isActive && displaySeconds > 0) {
                String text;
                if (displaySeconds > 60) {
                    text = ((displaySeconds + 59) / 60) + "m";
                } else {
                    text = displaySeconds + "s";
                }
                int textW = mc.font.width(text);
                g.drawString(mc.font, text, x + (ICON_SIZE - textW) / 2, y + ICON_SIZE + 2, 0xFF55FF55, true);
            } else if (isCooldown && displaySeconds > 0) {
                String text;
                if (displaySeconds >= 60) {
                    text = ((displaySeconds + 59) / 60) + "m";
                } else {
                    text = displaySeconds + "s";
                }
                int textW = mc.font.width(text);
                g.drawString(mc.font, text, x + (ICON_SIZE - textW) / 2, y + ICON_SIZE + 2, 0xFFFF5555, true);
            }
            // Stack-based debuffs (Soul Fracture) have no text label — the icon
            // variant itself (skill_<name>_1..5.png) encodes the stack count.

            // Numeric badge (Milky Curse): draw plain stack number below the icon
            // in the same position as the timer text, colored purple/pink.
            if (isNumericBadge && stackCount > 0) {
                String text = String.valueOf(stackCount);
                int textW = mc.font.width(text);
                g.drawString(mc.font, text, x + (ICON_SIZE - textW) / 2, y + ICON_SIZE + 2, 0xFFAA88FF, true);
            }

            // Draw grave coordinates above the icon, colored by dimension
            if (name.equals("grave") && extraData != null) {
                // Format: X/Y/Z:dimension (e.g. 123/64/-456:minecraft:overworld)
                String dimension = "";
                // Split coords from dimension — coords use / separator, dimension follows after coords
                String[] coordAndDim = extraData.split("/");
                if (coordAndDim.length >= 3) {
                    String zAndDim = coordAndDim[2];
                    int dimSep = zAndDim.indexOf(':');
                    String zVal = dimSep >= 0 ? zAndDim.substring(0, dimSep) : zAndDim;
                    dimension = dimSep >= 0 ? zAndDim.substring(dimSep + 1) : "";
                    String coordText = coordAndDim[0] + ", " + coordAndDim[1] + ", " + zVal;

                    int color;
                    if (dimension.contains("the_nether")) {
                        color = 0xFFFF6655; // red for nether
                    } else if (dimension.contains("the_end")) {
                        color = 0xFFDD77FF; // purple for end
                    } else {
                        color = 0xFF77FF77; // green for overworld
                    }

                    int coordW = mc.font.width(coordText);
                    g.drawString(mc.font, coordText, x + (ICON_SIZE - coordW) / 2, y - 10, color, true);
                }
            }

            x += ICON_SIZE + GAP;
        }
    }
}
