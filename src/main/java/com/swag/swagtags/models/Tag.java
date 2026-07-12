package com.swag.swagtags.models;

import java.util.UUID;

public class Tag {
    private final String id;
    private final UUID ownerUUID;
    private final String suffix;
    private final TagType type;
    private final long createdTimestamp;

    public enum TagType {
        CUSTOM,
        PRESET,
        ADMIN_GIVEN
    }

    public Tag(String id, UUID ownerUUID, String suffix, TagType type, long createdTimestamp) {
        this.id = id;
        this.ownerUUID = ownerUUID;
        this.suffix = suffix;
        this.type = type;
        this.createdTimestamp = createdTimestamp;
    }

    public Tag(UUID ownerUUID, String suffix, TagType type) {
        this(UUID.randomUUID().toString().substring(0, 8), ownerUUID, suffix, type, System.currentTimeMillis());
    }

    public Tag(UUID ownerUUID, String suffix) {
        this(ownerUUID, suffix, TagType.CUSTOM);
    }

    public String getId() { return id; }
    public UUID getOwnerUUID() { return ownerUUID; }
    public UUID getPlayerUUID() { return ownerUUID; }
    public String getSuffix() { return suffix; }
    public TagType getType() { return type; }
    public long getCreatedTimestamp() { return createdTimestamp; }
    public boolean isPurchased() { return true; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Tag)) return false;
        Tag other = (Tag) obj;
        return this.id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() {
        return "Tag{id='" + id + "', owner=" + ownerUUID + ", suffix='" + suffix + "', type=" + type + "}";
    }
}
