package com.mlkymc.radio;

import com.mlkymc.MlkyMC;
import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.opus.OpusDecoder;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Simple Voice Chat plugin for the radio system. Intercepts microphone packets
 * from players near active Microphone blocks and broadcasts the audio to all
 * Speaker blocks tuned to the same frequency.
 *
 * <p>Phase 1: single frequency (default 1), no echo, fixed range per speaker.
 */
@ForgeVoicechatPlugin
public class RadioVoicechatPlugin implements VoicechatPlugin {

    private static final double MIC_PICKUP_RANGE = 4.0; // blocks from mic to capture voice
    private static final float ECHO_THRESHOLD = 32.0f; // range above which echo is applied
    private static final int ECHO_DELAY_SAMPLES = 4800; // ~100ms at 48kHz
    private static final float ECHO_DECAY = 0.3f;
    private static final float VOLUME_REDUCTION = 0.85f;

    private VoicechatApi api;
    private static VoicechatApi staticApi;
    private static VoicechatServerApi staticServerApi;
    private final Map<String, short[]> echoBuffers = new HashMap<>(); // keyed by speakerPosKey

    public static VoicechatApi getApi() { return staticApi; }
    public static VoicechatServerApi getServerApi() { return staticServerApi; }

    @Override
    public String getPluginId() {
        return "mlkymc_radio";
    }

    @Override
    public void initialize(VoicechatApi api) {
        this.api = api;
        staticApi = api;
        // On the server, the VoicechatApi instance is also a VoicechatServerApi.
        // Capture it here so RadioMusicPlayer can use it immediately without waiting
        // for someone to speak into a microphone first.
        if (api instanceof VoicechatServerApi serverApi) {
            staticServerApi = serverApi;
        }
        MlkyMC.LOGGER.info("[Radio] Voicechat plugin initialized");
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

        var rm = RadioManager.getInstance();
        if (rm == null) return;

        // Check if the player is near any active microphone
        String dim = player.level().dimension().identifier().toString();
        RadioManager.RadioEntry nearestMic = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (var mic : rm.getActiveMics()) {
            if (!mic.dimension().equals(dim)) continue;
            BlockPos micPos = mic.toBlockPos();
            double distSq = player.blockPosition().distSqr(micPos);
            if (distSq <= MIC_PICKUP_RANGE * MIC_PICKUP_RANGE && distSq < nearestDistSq) {
                nearestMic = mic;
                nearestDistSq = distSq;
            }
        }

        if (nearestMic == null) return; // Not near any active mic

        // Get all speakers on this frequency
        var speakers = rm.getSpeakersOnFrequency(nearestMic.frequency());
        if (speakers.isEmpty()) return;

        // Broadcast the audio to each speaker as a locational sound.
        // Speakers with range > 32 get an echo effect applied to the audio.
        try {
            var serverApi = (VoicechatServerApi) event.getVoicechat();
            staticServerApi = serverApi;
            var packet = event.getPacket();
            byte[] originalOpus = packet.getOpusEncodedData();

            for (var speaker : speakers) {
                var pos = api.createPosition(
                        speaker.x() + 0.5, speaker.y() + 0.5, speaker.z() + 0.5);
                float range = speaker.range();

                // Apply echo for high-range speakers
                byte[] audioData;
                if (range > ECHO_THRESHOLD) {
                    audioData = applyEcho(originalOpus, speaker.x() + "," + speaker.y() + "," + speaker.z());
                } else {
                    audioData = originalOpus;
                }

                var locPacket = packet.locationalSoundPacketBuilder()
                        .opusEncodedData(audioData)
                        .position(pos)
                        .distance(range)
                        .build();

                for (var sp : player.level().getServer().getPlayerList().getPlayers()) {
                    if (sp.getUUID().equals(player.getUUID())) continue;
                    if (!sp.level().dimension().identifier().toString().equals(speaker.dimension())) continue;

                    var conn = serverApi.getConnectionOf(sp.getUUID());
                    if (conn != null) {
                        serverApi.sendLocationalSoundPacketTo(conn, locPacket);
                    }
                }
            }
        } catch (Exception e) {
            MlkyMC.LOGGER.debug("[Radio] Failed to broadcast: {}", e.getMessage());
        }
    }

    /**
     * Apply a distance echo effect to opus audio. Decodes to PCM, mixes in a
     * delayed copy, re-encodes. Each speaker position has its own echo buffer
     * so multiple speakers don't share state.
     */
    private byte[] applyEcho(byte[] opusData, String speakerKey) {
        if (api == null || opusData == null || opusData.length == 0) return opusData;
        try {
            OpusDecoder decoder = api.createDecoder();
            short[] pcm = decoder.decode(opusData);
            decoder.close();
            if (pcm == null || pcm.length == 0) return opusData;

            int bufferSize = ECHO_DELAY_SAMPLES + pcm.length;
            short[] echoBuffer = echoBuffers.computeIfAbsent(speakerKey, k -> new short[bufferSize]);
            if (echoBuffer.length < bufferSize) {
                short[] newBuf = new short[bufferSize];
                System.arraycopy(echoBuffer, 0, newBuf, 0, Math.min(echoBuffer.length, bufferSize));
                echoBuffer = newBuf;
                echoBuffers.put(speakerKey, echoBuffer);
            }

            short[] output = new short[pcm.length];
            for (int i = 0; i < pcm.length; i++) {
                float sample = pcm[i] * VOLUME_REDUCTION;
                int echoIdx = echoBuffer.length - ECHO_DELAY_SAMPLES + i;
                if (echoIdx >= 0 && echoIdx < echoBuffer.length) {
                    sample += echoBuffer[echoIdx] * ECHO_DECAY;
                }
                output[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, sample));
            }

            // Shift buffer and append new PCM
            int shift = pcm.length;
            if (shift < echoBuffer.length) {
                System.arraycopy(echoBuffer, shift, echoBuffer, 0, echoBuffer.length - shift);
                System.arraycopy(pcm, 0, echoBuffer, echoBuffer.length - shift, shift);
            }

            OpusEncoder encoder = api.createEncoder();
            byte[] result = encoder.encode(output);
            encoder.close();
            return result;
        } catch (Exception e) {
            return opusData; // fallback to unmodified audio
        }
    }
}
