package com.swag.swagtags.models;

import java.util.UUID;

public class LoanedTag {
    private final String tagId;
    private final UUID ownerUUID;
    private final UUID loanedToUUID;
    private final long expiryTime;

    public LoanedTag(String tagId, UUID ownerUUID, UUID loanedToUUID, long expiryTime) {
        this.tagId = tagId;
        this.ownerUUID = ownerUUID;
        this.loanedToUUID = loanedToUUID;
        this.expiryTime = expiryTime;
    }

    public String getTagId() { return tagId; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public UUID getLoanedToUUID() { return loanedToUUID; }
    public long getExpiryTime() { return expiryTime; }

    public boolean isExpired() {
        return System.currentTimeMillis() > expiryTime;
    }

    public long getRemainingMs() {
        return Math.max(0, expiryTime - System.currentTimeMillis());
    }
}
