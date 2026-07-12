package com.swag.swagtags.models;

public class PendingTag {
    private final String suffix;
    private final int creditsEscrowed;
    private final long timestamp;

    public PendingTag(String suffix, int creditsEscrowed) {
        this(suffix, creditsEscrowed, System.currentTimeMillis());
    }

    /** Used when reloading a pending request from disk, preserving its original submission time. */
    public PendingTag(String suffix, int creditsEscrowed, long timestamp) {
        this.suffix = suffix;
        this.creditsEscrowed = creditsEscrowed;
        this.timestamp = timestamp;
    }

    public String getSuffix() { return suffix; }
    public int getCreditsEscrowed() { return creditsEscrowed; }
    public long getTimestamp() { return timestamp; }

    public long getAgeDays() {
        return (System.currentTimeMillis() - timestamp) / (24 * 60 * 60 * 1000L);
    }
}
