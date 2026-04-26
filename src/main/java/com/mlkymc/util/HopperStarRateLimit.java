package com.mlkymc.util;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player hourly cap on Milky Stars granted via automated (hopper-based)
 * extraction paths — composter hoppers, furnace hoppers, etc.
 *
 * <p>A player gets at most {@link #CAP_PER_HOUR} stars per rolling hour from
 * automated sources. Manual harvesting (right-click) is unaffected.
 */
public final class HopperStarRateLimit {

    private static final long WINDOW_MS = 3_600_000L; // 1 hour
    public static final int CAP_PER_HOUR = 100;

    private static final Map<UUID, Deque<Long>> grantTimes = new ConcurrentHashMap<>();

    private HopperStarRateLimit() {}

    /**
     * Attempt to grant one automated Milky Star to a player.
     * @return true if the grant is allowed (increments the per-player counter),
     *         false if the player is already at or above the hourly cap.
     */
    public static boolean tryGrant(UUID uuid) {
        long now = System.currentTimeMillis();
        var times = grantTimes.computeIfAbsent(uuid, u -> new ArrayDeque<>());
        synchronized (times) {
            // Purge expired entries
            while (!times.isEmpty() && times.peekFirst() < now - WINDOW_MS) {
                times.pollFirst();
            }
            if (times.size() >= CAP_PER_HOUR) return false;
            times.addLast(now);
            return true;
        }
    }

    /** Current number of stars granted to this player within the rolling hour window. */
    public static int currentCount(UUID uuid) {
        long now = System.currentTimeMillis();
        var times = grantTimes.get(uuid);
        if (times == null) return 0;
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < now - WINDOW_MS) {
                times.pollFirst();
            }
            return times.size();
        }
    }
}
