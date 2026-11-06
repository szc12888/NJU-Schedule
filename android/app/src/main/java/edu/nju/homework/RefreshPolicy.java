package edu.nju.homework;

/** Pure refresh timing policy with no Android dependencies. */
public final class RefreshPolicy {
    private RefreshPolicy() {}

    /**
     * Decides whether a cache refresh should start.
     *
     * @param connected whether this platform has an established login session
     * @param hasCache whether cached data is available
     * @param lastSuccess time of the last successful read, or a non-positive value
     * @param lastAttempt time of the last read attempt; non-positive values fall back to lastSuccess
     * @param now current time in the same unit as the other timestamps
     * @param interval configured minimum time between data-read attempts
     * @param retryAfterAuthentication explicit retry requested by authentication
     * @return whether a read should start
     */
    public static boolean shouldRefresh(
        boolean connected,
        boolean hasCache,
        long lastSuccess,
        long lastAttempt,
        long now,
        long interval,
        boolean retryAfterAuthentication
    ) {
        if (!connected) return false;
        if (retryAfterAuthentication) return true;
        if (!hasCache || lastSuccess <= 0L) return true;

        long reference = lastAttempt > 0L ? lastAttempt : lastSuccess;
        if (now < reference) return false;

        long elapsed = now - reference;
        long minimumInterval = Math.max(0L, interval);
        return elapsed >= minimumInterval;
    }
}
