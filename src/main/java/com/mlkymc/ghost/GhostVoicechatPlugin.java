package com.mlkymc.ghost;

import com.mlkymc.MlkyMC;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Voice Chat API integration for the ghost system.
 * - Ghosts spend 5 Spectral Energy per 3 seconds of voice transmission.
 * - If out of SE, voice is blocked.
 * - Subtle particles appear at ghost location when speaking.
 * - Ghost voice has an eerie echo/reverb filter.
 */
@ForgeVoicechatPlugin
public class GhostVoicechatPlugin implements VoicechatPlugin {

    private static final int SE_COST = 5;
    private static final long COST_INTERVAL_MS = 3000; // 3 seconds

    // Echo effect parameters
    private static final int ECHO_DELAY_SAMPLES = 4800;  // ~100ms at 48kHz
    private static final float ECHO_DECAY = 0.45f;       // echo volume (45% of original)
    private static final float ECHO_DECAY_2 = 0.2f;      // second echo (20%)
    private static final int ECHO_DELAY_2_SAMPLES = 9600; // ~200ms second echo
    private static final float VOLUME_REDUCTION = 0.75f;  // slightly quieter overall

    private final Map<UUID, Long> lastCostTime = new HashMap<>();
    private final Map<UUID, short[]> echoBuffers = new HashMap<>();

    private VoicechatApi api;

    @Override
    public String getPluginId() {
        return "mlkymc_ghost_voicechat";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        MlkyMC.LOGGER.info("mlkyMC Ghost Voicechat Plugin initialized");
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(MicrophonePacketEvent.class, this::onMicrophonePacket);
    }

    private void onMicrophonePacket(MicrophonePacketEvent event) {
        var connection = event.getSenderConnection();
        if (connection == null) return;

        var vcPlayer = connection.getPlayer();
        if (vcPlayer == null) return;

        Object playerObj = vcPlayer.getPlayer();
        if (!(playerObj instanceof ServerPlayer player)) return;

        var ghostManager = MlkyMC.getGhostManager();
        if (ghostManager == null || !ghostManager.isGhost(player.getUUID())) return;

        var gdm = MlkyMC.getGhostDataManager();
        if (gdm == null) return;

        var ghostData = gdm.getOrCreate(player.getUUID());

        if (ghostData.spectralEnergy < SE_COST) {
            // Cancel the event AND replace audio with silence as fallback
            event.cancel();
            try {
                var packet = event.getPacket();
                byte[] opusData = packet.getOpusEncodedData();
                if (opusData != null) {
                    packet.setOpusEncodedData(new byte[0]);
                }
            } catch (Exception ignored) {}
            // Only warn once per silence period (not every packet)
            Long lastWarn = lastCostTime.get(player.getUUID());
            long now2 = System.currentTimeMillis();
            if (lastWarn == null || now2 - lastWarn >= 5000) {
                lastCostTime.put(player.getUUID(), now2);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Not enough Spectral Energy to speak! The living can't hear you. (" +
                        ghostData.spectralEnergy + "/" + SE_COST + " SE)").withColor(0xFF5555));
            }
            return;
        }

        // Deduct SE every 3 seconds of transmission
        long now = System.currentTimeMillis();
        Long lastCost = lastCostTime.get(player.getUUID());
        if (lastCost == null || now - lastCost >= COST_INTERVAL_MS) {
            boolean firstSpeak = lastCost == null;
            ghostData.addSpectralEnergy(-SE_COST);
            lastCostTime.put(player.getUUID(), now);
            gdm.sendSpectralSync(player, ghostData);

            if (player.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SOUL,
                        player.getX(), player.getY() + 1.5, player.getZ(),
                        3, 0.2, 0.3, 0.2, 0.01);
            }

            // Inform on first voice use and when running low
            if (firstSpeak) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Speaking costs 5 SE every 3 seconds. Nearby living players see soul particles.").withColor(0xAAAAAA));
            }
            if (ghostData.spectralEnergy <= 15 && ghostData.spectralEnergy > 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Low Spectral Energy! (" + ghostData.spectralEnergy + " SE remaining)").withColor(0xFFAA00));
            } else if (ghostData.spectralEnergy <= 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Out of Spectral Energy — voice silenced!").withColor(0xFF5555));
            }
        }

        // Cancel the default transmission and manually send as locational sound
        // This ensures the modified audio actually reaches nearby players
        event.cancel();

        var packet = event.getPacket();
        applyGhostEcho(event, player.getUUID());

        // Send as locational sound to all nearby living players
        try {
            var serverApi = (de.maxhenkel.voicechat.api.VoicechatServerApi) event.getVoicechat();
            var pos = api.createPosition(player.getX(), player.getY() + 1.0, player.getZ());
            var locPacket = packet.locationalSoundPacketBuilder()
                    .opusEncodedData(packet.getOpusEncodedData())
                    .position(pos)
                    .distance(15.0f) // 15 block range
                    .build();

            // Send to all connected non-ghost players
            for (var sp : player.level().getServer().getPlayerList().getPlayers()) {
                if (sp.getUUID().equals(player.getUUID())) continue;
                var ghostMgr = MlkyMC.getGhostManager();
                if (ghostMgr != null && ghostMgr.isGhost(sp.getUUID())) continue;

                var conn = serverApi.getConnectionOf(sp.getUUID());
                if (conn != null) {
                    serverApi.sendLocationalSoundPacketTo(conn, locPacket);
                }
            }
        } catch (Exception e) {
            MlkyMC.LOGGER.debug("Ghost voice send failed: {}", e.getMessage());
        }
    }

    /**
     * Applies a ghostly echo/reverb effect to the voice packet.
     * Decodes Opus → PCM, mixes in delayed echo, re-encodes.
     */
    private void applyGhostEcho(MicrophonePacketEvent event, UUID ghostUuid) {
        if (api == null) return;

        try {
            var packet = event.getPacket();
            byte[] opusData = packet.getOpusEncodedData();
            if (opusData == null || opusData.length == 0) return;

            // Decode Opus to raw PCM (short[])
            OpusDecoder decoder = api.createDecoder();
            short[] pcm = decoder.decode(opusData);
            decoder.close();

            if (pcm == null || pcm.length == 0) return;

            // Get or create echo buffer for this ghost
            int bufferSize = ECHO_DELAY_2_SAMPLES + pcm.length;
            short[] echoBuffer = echoBuffers.computeIfAbsent(ghostUuid,
                    k -> new short[bufferSize]);

            // Resize buffer if needed
            if (echoBuffer.length < bufferSize) {
                short[] newBuffer = new short[bufferSize];
                System.arraycopy(echoBuffer, 0, newBuffer, 0,
                        Math.min(echoBuffer.length, bufferSize));
                echoBuffer = newBuffer;
                echoBuffers.put(ghostUuid, echoBuffer);
            }

            // Apply echo: mix current PCM with delayed versions from the buffer
            short[] output = new short[pcm.length];
            for (int i = 0; i < pcm.length; i++) {
                float sample = pcm[i] * VOLUME_REDUCTION;

                // First echo (100ms delay)
                int echoIdx1 = echoBuffer.length - ECHO_DELAY_SAMPLES + i;
                if (echoIdx1 >= 0 && echoIdx1 < echoBuffer.length) {
                    sample += echoBuffer[echoIdx1] * ECHO_DECAY;
                }

                // Second echo (200ms delay, quieter)
                int echoIdx2 = echoBuffer.length - ECHO_DELAY_2_SAMPLES + i;
                if (echoIdx2 >= 0 && echoIdx2 < echoBuffer.length) {
                    sample += echoBuffer[echoIdx2] * ECHO_DECAY_2;
                }

                // Clamp to short range
                output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            }

            // Shift echo buffer: discard oldest, append new PCM
            int shift = pcm.length;
            if (shift < echoBuffer.length) {
                System.arraycopy(echoBuffer, shift, echoBuffer, 0, echoBuffer.length - shift);
            }
            System.arraycopy(pcm, 0, echoBuffer, echoBuffer.length - shift, shift);

            // Re-encode to Opus
            OpusEncoder encoder = api.createEncoder();
            byte[] newOpus = encoder.encode(output);
            encoder.close();

            if (newOpus != null) {
                packet.setOpusEncodedData(newOpus);
            }
        } catch (Exception e) {
            // Silently fail — audio will just play without the effect
        }
    }
}
