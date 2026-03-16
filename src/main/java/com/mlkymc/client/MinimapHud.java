package com.mlkymc.client;

import com.mlkymc.classes.ClassType;
import com.mlkymc.classes.ProfessionType;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

/**
 * Adventurer minimap HUD. Uses a dynamic texture for terrain — single draw call.
 * Updates terrain every 2 seconds. Mob/player dots drawn as small fills.
 */
public class MinimapHud {
    private static final Identifier FRAME =
            Identifier.parse("mlkymc:textures/gui/minimap_frame.png");

    private static final int MAP_SIZE = 74;
    private static final int FRAME_SIZE = 80;
    private static final int SCAN_RADIUS = 32;
    private static final int MARGIN = 5;

    private static boolean enabled = false;
    private static DynamicTexture mapTexture;
    private static Identifier mapTextureId;
    private static int lastScanX = Integer.MIN_VALUE;
    private static int lastScanZ = Integer.MIN_VALUE;
    private static long lastScanTick = 0;

    public static void toggle() { enabled = !enabled; }
    public static boolean isEnabled() { return enabled; }

    @SubscribeEvent
    public void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!enabled) return;
        if (ClientClassData.getChosenClass() != ClassType.ADVENTURER) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Initialize texture once
        if (mapTexture == null) {
            NativeImage img = new NativeImage(MAP_SIZE, MAP_SIZE, false);
            mapTexture = new DynamicTexture(() -> "mlkymc_minimap", img);
            mapTextureId = Identifier.parse("mlkymc:minimap_dynamic");
            mc.getTextureManager().register(mapTextureId, mapTexture);
        }

        GuiGraphics g = event.getGuiGraphics();
        int screenW = mc.getWindow().getGuiScaledWidth();

        int frameX = screenW - FRAME_SIZE - MARGIN;
        int frameY = MARGIN;
        int mapX = frameX + (FRAME_SIZE - MAP_SIZE) / 2;
        int mapY = frameY + (FRAME_SIZE - MAP_SIZE) / 2;

        Player player = mc.player;
        Level level = mc.level;
        int px = player.getBlockX();
        int pz = player.getBlockZ();

        // Rebuild every frame if moved, or every tick regardless
        long gameTick = level.getGameTime();
        if (px != lastScanX || pz != lastScanZ || gameTick != lastScanTick) {
            rebuildTexture(level, px, pz);
            lastScanX = px;
            lastScanZ = pz;
            lastScanTick = gameTick;
        }

        // Draw map as single textured quad
        g.blit(RenderPipelines.GUI_TEXTURED, mapTextureId,
                mapX, mapY, 0, 0, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE, MAP_SIZE);

        // Player dot (center, white)
        int centerX = mapX + MAP_SIZE / 2;
        int centerY = mapY + MAP_SIZE / 2;
        g.fill(centerX - 1, centerY - 1, centerX + 2, centerY + 2, 0xFFFFFFFF);

        // Lv20: Mob dots (red)
        int advLevel = ClientClassData.getLevel(ProfessionType.ADVENTURER);
        if (advLevel >= 20) {
            for (Monster mob : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(SCAN_RADIUS))) {
                int dotX = mapX + (int) ((mob.getX() - px + SCAN_RADIUS) * MAP_SIZE / (SCAN_RADIUS * 2));
                int dotY = mapY + (int) ((mob.getZ() - pz + SCAN_RADIUS) * MAP_SIZE / (SCAN_RADIUS * 2));
                if (dotX >= mapX && dotX < mapX + MAP_SIZE - 1 && dotY >= mapY && dotY < mapY + MAP_SIZE - 1) {
                    g.fill(dotX, dotY, dotX + 3, dotY + 3, 0xFFFF3333);
                }
            }
        }

        // Lv50: Player dots (green, standing still only)
        if (advLevel >= 50 && player.getDeltaMovement().horizontalDistance() < 0.01) {
            for (Player other : level.getEntitiesOfClass(Player.class, player.getBoundingBox().inflate(SCAN_RADIUS))) {
                if (other != player && !other.isShiftKeyDown()) {
                    int dotX = mapX + (int) ((other.getX() - px + SCAN_RADIUS) * MAP_SIZE / (SCAN_RADIUS * 2));
                    int dotY = mapY + (int) ((other.getZ() - pz + SCAN_RADIUS) * MAP_SIZE / (SCAN_RADIUS * 2));
                    if (dotX >= mapX && dotX < mapX + MAP_SIZE - 1 && dotY >= mapY && dotY < mapY + MAP_SIZE - 1) {
                        g.fill(dotX, dotY, dotX + 3, dotY + 3, 0xFF33FF33);
                    }
                }
            }
        }

        // Frame overlay
        g.blit(RenderPipelines.GUI_TEXTURED, FRAME,
                frameX, frameY, 0, 0, FRAME_SIZE, FRAME_SIZE, 128, 128, 128, 128);
    }

    private void rebuildTexture(Level level, int px, int pz) {
        if (mapTexture == null) return;
        NativeImage img = mapTexture.getPixels();
        if (img == null) return;

        Minecraft mc = Minecraft.getInstance();
        int playerY = mc.player != null ? mc.player.getBlockY() : 64;

        // Check if player is underground (block above head is solid)
        boolean underground = !level.getBlockState(new BlockPos(px, playerY + 2, pz)).isAir();

        for (int dz = 0; dz < MAP_SIZE; dz++) {
            for (int dx = 0; dx < MAP_SIZE; dx++) {
                int worldX = px - SCAN_RADIUS + dx * (SCAN_RADIUS * 2) / MAP_SIZE;
                int worldZ = pz - SCAN_RADIUS + dz * (SCAN_RADIUS * 2) / MAP_SIZE;

                int scanY;
                if (underground) {
                    // Underground: scan at player's Y level, find nearest solid below
                    scanY = playerY;
                    for (int y = playerY; y > playerY - 8; y--) {
                        if (!level.getBlockState(new BlockPos(worldX, y, worldZ)).isAir()) {
                            scanY = y;
                            break;
                        }
                    }
                } else {
                    // Surface: use heightmap as before
                    scanY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, worldX, worldZ) - 1;
                }
                // Find a visible block (skip air, scan down)
                BlockState state = level.getBlockState(new BlockPos(worldX, scanY, worldZ));
                if (state.isAir()) {
                    for (int y = scanY - 1; y > scanY - 5; y--) {
                        state = level.getBlockState(new BlockPos(worldX, y, worldZ));
                        if (!state.isAir()) break;
                    }
                }

                int color = 0xFF000000 | getColorForBlock(state, level, worldX, scanY, worldZ);

                img.setPixel(dx, dz, color);
            }
        }

        mapTexture.upload();
    }

    private int getColorForBlock(BlockState state, Level level, int x, int y, int z) {
        String id = state.getBlock().getDescriptionId();

        // Water — blue
        if (id.contains("water")) return 0x3F76E4;
        // Lava — orange
        if (id.contains("lava")) return 0xD4631E;
        // Sand — tan
        if (id.contains("sand") && !id.contains("stone")) return 0xDBD3A0;
        // Glass — light cyan (transparent blocks)
        if (id.contains("glass")) return 0xA0C8D8;
        // Ice — light blue
        if (id.contains("ice")) return 0x8FB8D8;
        // Leaves — dark green
        if (id.contains("leaves") || id.contains("leaf")) return 0x3B6B23;
        // Torch/fire/lantern — yellow (not red!)
        if (id.contains("torch") || id.contains("fire") || id.contains("lantern") || id.contains("candle")) return 0xDDCC44;
        // Flowers/small plants — skip (show block below)
        if (id.contains("flower") || id.contains("tallgrass") || id.contains("fern")) return 0x4B8B23;
        // Snow — white
        if (id.contains("snow")) return 0xF0F0F0;

        // Use MapColor for everything else
        try {
            MapColor mapColor = state.getMapColor(level, new BlockPos(x, y, z));
            if (mapColor != null && mapColor.col != 0) {
                return mapColor.col;
            }
        } catch (Exception ignored) {}

        return 0x333333;
    }

    /**
     * Convert RGB int to ABGR format used by NativeImage.
     */
    private int toABGR(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return 0xFF000000 | (b << 16) | (g << 8) | r;
    }
}
