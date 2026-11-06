package edu.nju.homework;

/** Synthetic boundary checks. No device, account, or school data is used. */
public final class RefreshPolicyChecks {
    private static int checks;

    private RefreshPolicyChecks() {}

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    private static boolean refresh(
        boolean connected,
        boolean hasCache,
        long lastSuccess,
        long lastAttempt,
        long now,
        long interval,
        boolean retryAfterAuthentication
    ) {
        return RefreshPolicy.shouldRefresh(
            connected,
            hasCache,
            lastSuccess,
            lastAttempt,
            now,
            interval,
            retryAfterAuthentication
        );
    }

    public static void main(String[] args) {
        check(!refresh(false, false, 0L, 0L, 100L, 10L, true),
            "disconnected never refreshes, even for an authentication retry");
        check(!refresh(false, true, 100L, 200L, 250L, 0L, false),
            "disconnected cache is never refreshed");

        check(refresh(true, false, 0L, 99L, 100L, 1000L, false),
            "connected without cache starts the first read");
        check(refresh(true, true, 0L, 99L, 100L, 1000L, false),
            "connected without prior success starts the first read after a recent failure");
        check(refresh(true, true, -1L, 99L, 100L, 1000L, false),
            "negative success marker still counts as never successful");

        check(!refresh(true, true, 100L, 140L, 149L, 10L, false),
            "successful cached data before its interval is not refreshed");
        check(refresh(true, true, 100L, 140L, 150L, 10L, false),
            "elapsed time exactly equal to the interval refreshes");
        check(refresh(true, true, 100L, 140L, 151L, 10L, false),
            "elapsed time beyond the interval refreshes");
        check(!refresh(true, true, 100L, 149L, 150L, 10L, false),
            "a recent failed attempt delays refresh using lastAttempt");

        check(!refresh(true, true, 100L, 0L, 105L, 10L, false),
            "non-positive lastAttempt falls back to lastSuccess before interval");
        check(refresh(true, true, 100L, -1L, 110L, 10L, false),
            "lastSuccess fallback refreshes exactly at interval");

        check(refresh(true, true, 100L, 140L, 140L, 100L, true),
            "authentication retry overrides a fresh cache");
        check(refresh(true, true, -1L, 140L, 140L, 100L, true),
            "authentication retry is immediate after past failed reads");

        check(refresh(true, true, 100L, 150L, 150L, -1L, false),
            "negative interval is clamped to zero");
        check(refresh(true, true, 100L, 150L, 150L, 0L, false),
            "zero interval refreshes at zero elapsed time");

        check(!refresh(true, true, 100L, 200L, 150L, 0L, false),
            "a future lastAttempt does not refresh");
        check(!refresh(true, true, 1L, 0L, Long.MIN_VALUE, Long.MAX_VALUE, false),
            "extreme clock regression does not overflow into a refresh");
        check(!refresh(true, true, 1L, Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE, false),
            "extreme bounded timestamps remain safe");

        System.out.println("PASS: " + checks + " refresh policy checks");
    }
}
