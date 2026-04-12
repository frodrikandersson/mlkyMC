package com.mlkymc.radio;

import com.mlkymc.MlkyMC;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.LocationalAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages music playback for Radio blocks in music mode. Reads .wav files from
 * the server's radio_music folder, decodes to 48kHz mono PCM, and streams via
 * the voicechat API's AudioPlayer to a LocationalAudioChannel at the radio's position.
 *
 * <p>Supports .wav files (16-bit PCM). Convert .ogg/.mp3 to .wav externally.
 * 48kHz mono is preferred but the player will resample if needed.
 */
public class RadioMusicPlayer {

    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SIZE = 960; // 20ms at 48kHz

    // Active players keyed by radio block position string
    private static final Map<String, PlaybackState> activePlayers = new ConcurrentHashMap<>();

    private static Path musicFolder;
    private static List<String> playlist = new ArrayList<>();

    public static void init(Path worldDataDir) {
        musicFolder = worldDataDir.resolve("radio_music");
        try {
            Files.createDirectories(musicFolder);
        } catch (Exception e) {
            MlkyMC.LOGGER.warn("[Radio] Failed to create radio_music folder", e);
        }
        reloadPlaylist();
    }

    public static void reloadPlaylist() {
        playlist.clear();
        if (musicFolder == null || !Files.exists(musicFolder)) return;
        File[] files = musicFolder.toFile().listFiles((dir, name) ->
                name.endsWith(".wav") || name.endsWith(".ogg"));
        if (files != null) {
            for (File f : files) {
                playlist.add(f.getName());
            }
            Collections.sort(playlist);
        }
        MlkyMC.LOGGER.info("[Radio] Loaded {} songs in playlist", playlist.size());
    }

    public static List<String> getPlaylist() {
        return Collections.unmodifiableList(playlist);
    }

    public static boolean hasMusic() {
        if (playlist.isEmpty()) reloadPlaylist(); // lazy reload if empty
        return !playlist.isEmpty();
    }

    /**
     * Start playing the given song index at a radio block position.
     */
    public static void startPlaying(VoicechatServerApi serverApi, de.maxhenkel.voicechat.api.VoicechatApi api,
                                     BlockPos pos, ServerLevel level, int songIndex, float volume) {
        String key = posKey(pos);
        stopPlaying(key);

        if (playlist.isEmpty()) return;
        int idx = songIndex % playlist.size();
        String filename = playlist.get(idx);
        Path filePath = musicFolder.resolve(filename);

        if (!Files.exists(filePath)) return;

        try {
            // Load audio file and convert to 48kHz mono 16-bit PCM
            short[] fullPcm = loadAudioFile(filePath);
            if (fullPcm == null || fullPcm.length == 0) return;

            // Create locational audio channel at the radio's position
            var vcLevel = api.fromServerLevel(level);
            var vcPos = api.createPosition(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            UUID channelId = UUID.randomUUID();
            LocationalAudioChannel channel = serverApi.createLocationalAudioChannel(channelId, vcLevel, vcPos);
            channel.setDistance(32.0f); // audible within 32 blocks

            // Create frame supplier — volume is applied per-frame so it can change live
            final short[] audio = fullPcm;
            final int[] offset = {0};
            OpusEncoder encoder = api.createEncoder();
            PlaybackState state = new PlaybackState(null, encoder, channelId,
                    idx, filename, volume, pos, level, serverApi, api);

            AudioPlayer player = serverApi.createAudioPlayer(channel, encoder, () -> {
                if (offset[0] >= audio.length) return null; // end of song
                int remaining = audio.length - offset[0];
                int len = Math.min(FRAME_SIZE, remaining);
                short[] frame = new short[FRAME_SIZE];
                float vol = state.volume[0];
                for (int i = 0; i < len; i++) {
                    frame[i] = (short) Math.max(Short.MIN_VALUE,
                            Math.min(Short.MAX_VALUE, audio[offset[0] + i] * vol));
                }
                offset[0] += len;
                return frame;
            });

            state.player = player; // back-fill reference
            state.audio = audio;
            state.offset = offset;

            // Create relay channels for speakers connected via nearby microphones
            state.relayPlayers = createRelayChannels(serverApi, api, pos, level, audio, offset, state.volume);

            player.setOnStopped(() -> {
                encoder.close();
                // Stop relay players
                for (var relay : state.relayPlayers) {
                    try { relay.stopPlaying(); } catch (Exception ignored) {}
                }
                activePlayers.remove(key);
                // Auto-advance to next song if not manually stopped
                if (!state.stoppedManually && !playlist.isEmpty()) {
                    int nextIdx = (state.songIndex + 1) % playlist.size();
                    // Update the block entity's song index
                    if (state.level.getBlockEntity(state.blockPos) instanceof SpeakerBlockEntity spk) {
                        spk.setSongIndex(nextIdx);
                    }
                    startPlaying(serverApi, api, state.blockPos, state.level, nextIdx, state.volume[0]);
                }
            });

            player.startPlaying();
            for (var relay : state.relayPlayers) {
                relay.startPlaying();
            }
            activePlayers.put(key, state);

            MlkyMC.LOGGER.info("[Radio] Now playing '{}' at {}", filename, pos);
        } catch (Exception e) {
            MlkyMC.LOGGER.warn("[Radio] Failed to start playback: {}", e.getMessage());
        }
    }

    /**
     * Rebuild relay channels for all active music players. Call this when
     * a speaker or microphone is added/removed so relays stay in sync.
     */
    public static void refreshAllRelays() {
        for (var state : activePlayers.values()) {
            // Stop old relays
            for (var relay : state.relayPlayers) {
                try { relay.stopPlaying(); } catch (Exception ignored) {}
            }
            // Recreate from current speaker state
            state.relayPlayers = createRelayChannels(
                    state.serverApi, state.api, state.blockPos, state.level,
                    state.audio, state.offset, state.volume);
            for (var relay : state.relayPlayers) {
                relay.startPlaying();
            }
        }
    }

    private static final double MIC_PICKUP_RANGE_SQ = 4.0 * 4.0; // 4 blocks

    /**
     * Creates additional LocationalAudioChannels at each speaker position that
     * should receive relayed music from a nearby microphone. Returns the list
     * of relay AudioPlayers (empty if no relay needed).
     */
    private static List<AudioPlayer> createRelayChannels(
            VoicechatServerApi serverApi, de.maxhenkel.voicechat.api.VoicechatApi api,
            BlockPos radioPos, ServerLevel level, short[] audio, int[] offset, float[] volume) {

        List<AudioPlayer> relays = new ArrayList<>();
        var rm = RadioManager.getInstance();
        if (rm == null) return relays;

        String dim = level.dimension().identifier().toString();

        // Find any active mic within range of this radio
        RadioManager.RadioEntry nearestMic = null;
        for (var mic : rm.getActiveMics()) {
            if (!mic.dimension().equals(dim)) continue;
            if (radioPos.distSqr(mic.toBlockPos()) <= MIC_PICKUP_RANGE_SQ) {
                nearestMic = mic;
                break;
            }
        }
        if (nearestMic == null) return relays;

        // Get speakers on that mic's frequency
        var speakers = rm.getSpeakersOnFrequency(nearestMic.frequency());
        if (speakers.isEmpty()) return relays;

        var vcLevel = api.fromServerLevel(level);

        for (var speaker : speakers) {
            try {
                var vcPos = api.createPosition(speaker.x() + 0.5, speaker.y() + 0.5, speaker.z() + 0.5);
                UUID relayId = UUID.randomUUID();
                LocationalAudioChannel relayCh = serverApi.createLocationalAudioChannel(relayId, vcLevel, vcPos);
                if (relayCh == null) continue;
                relayCh.setDistance(speaker.range());

                OpusEncoder relayEnc = api.createEncoder();
                // Each relay gets its own offset starting at the primary's current position
                final int[] relayOffset = {offset[0]};
                AudioPlayer relayPlayer = serverApi.createAudioPlayer(relayCh, relayEnc, () -> {
                    if (relayOffset[0] >= audio.length) return null;
                    int remaining = audio.length - relayOffset[0];
                    int len = Math.min(FRAME_SIZE, remaining);
                    short[] frame = new short[FRAME_SIZE];
                    float vol = volume[0];
                    for (int i = 0; i < len; i++) {
                        frame[i] = (short) Math.max(Short.MIN_VALUE,
                                Math.min(Short.MAX_VALUE, audio[relayOffset[0] + i] * vol));
                    }
                    relayOffset[0] += len;
                    return frame;
                });

                relayPlayer.setOnStopped(relayEnc::close);
                relays.add(relayPlayer);
            } catch (Exception e) {
                MlkyMC.LOGGER.debug("[Radio] Failed to create relay channel: {}", e.getMessage());
            }
        }

        return relays;
    }

    public static void stopPlaying(String key) {
        PlaybackState state = activePlayers.remove(key);
        if (state != null) {
            state.stoppedManually = true;
            try {
                state.player.stopPlaying();
            } catch (Exception ignored) {}
            for (var relay : state.relayPlayers) {
                try { relay.stopPlaying(); } catch (Exception ignored) {}
            }
            try {
                state.encoder.close();
            } catch (Exception ignored) {}
        }
    }

    public static void stopPlaying(BlockPos pos) {
        stopPlaying(posKey(pos));
    }

    public static boolean isPlaying(BlockPos pos) {
        var state = activePlayers.get(posKey(pos));
        return state != null && state.player.isPlaying();
    }

    public static String getCurrentSong(BlockPos pos) {
        var state = activePlayers.get(posKey(pos));
        return state != null ? state.songName : null;
    }

    public static int getCurrentSongIndex(BlockPos pos) {
        var state = activePlayers.get(posKey(pos));
        return state != null ? state.songIndex : 0;
    }

    public static void stopAll() {
        for (var key : new ArrayList<>(activePlayers.keySet())) {
            stopPlaying(key);
        }
    }

    /**
     * Load a .wav audio file and convert to 48kHz mono 16-bit PCM.
     */
    private static short[] loadAudioFile(Path filePath) {
        try {
            AudioInputStream ais = AudioSystem.getAudioInputStream(filePath.toFile());
            AudioFormat srcFormat = ais.getFormat();

            // Convert to 48kHz mono 16-bit PCM if needed
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);

            AudioInputStream converted;
            if (!srcFormat.matches(targetFormat)) {
                // Two-step conversion: first to PCM, then resample
                AudioFormat intermediate = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        srcFormat.getSampleRate(), 16,
                        srcFormat.getChannels(), srcFormat.getChannels() * 2,
                        srcFormat.getSampleRate(), false);
                if (!srcFormat.getEncoding().equals(AudioFormat.Encoding.PCM_SIGNED)) {
                    ais = AudioSystem.getAudioInputStream(intermediate, ais);
                }
                converted = AudioSystem.getAudioInputStream(targetFormat, ais);
            } else {
                converted = ais;
            }

            byte[] bytes = converted.readAllBytes();
            converted.close();

            // Convert bytes to shorts
            short[] pcm = new short[bytes.length / 2];
            for (int i = 0; i < pcm.length; i++) {
                pcm[i] = (short) ((bytes[i * 2] & 0xFF) | (bytes[i * 2 + 1] << 8));
            }
            return pcm;
        } catch (Exception e) {
            MlkyMC.LOGGER.warn("[Radio] Failed to load audio file '{}': {}", filePath.getFileName(), e.getMessage());
            return null;
        }
    }

    private static String posKey(BlockPos pos) {
        return pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** Update volume on an actively playing radio without restarting the song. */
    public static void setVolume(BlockPos pos, float volume) {
        var state = activePlayers.get(posKey(pos));
        if (state != null) {
            state.volume[0] = Math.max(0f, Math.min(2f, volume));
        }
    }

    private static class PlaybackState {
        AudioPlayer player; // non-final: back-filled after AudioPlayer creation
        final OpusEncoder encoder;
        final UUID channelId;
        final int songIndex;
        final String songName;
        final float[] volume; // mutable — applied per-frame
        final BlockPos blockPos;
        final ServerLevel level;
        final VoicechatServerApi serverApi;
        final de.maxhenkel.voicechat.api.VoicechatApi api;
        short[] audio;   // raw PCM for relay recreation
        int[] offset;     // shared playback offset
        List<AudioPlayer> relayPlayers = new ArrayList<>();
        boolean stoppedManually = false;

        PlaybackState(AudioPlayer player, OpusEncoder encoder,
                      UUID channelId, int songIndex, String songName, float volume,
                      BlockPos blockPos, ServerLevel level,
                      VoicechatServerApi serverApi, de.maxhenkel.voicechat.api.VoicechatApi api) {
            this.player = player;
            this.encoder = encoder;
            this.channelId = channelId;
            this.songIndex = songIndex;
            this.songName = songName;
            this.volume = new float[]{volume};
            this.blockPos = blockPos;
            this.level = level;
            this.serverApi = serverApi;
            this.api = api;
        }
    }
}
