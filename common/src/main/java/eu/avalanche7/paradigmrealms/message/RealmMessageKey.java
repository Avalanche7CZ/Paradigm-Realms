package eu.avalanche7.paradigmrealms.message;

import java.util.Map;

public enum RealmMessageKey {
    BACKUP_CAPTURE_STARTED(
            MessageChannel.CHAT),
    BACKUP_PROGRESS(
            MessageChannel.ACTION_BAR),
    BACKUP_LOCKED(
            MessageChannel.ACTION_BAR),
    BACKUP_COMPLETED(
            MessageChannel.CHAT),
    BACKUP_AUTOMATIC_COMPLETED(
            MessageChannel.CHAT),
    BACKUP_AUTOMATIC_FAILED(
            MessageChannel.ADMIN_CHAT),
    BACKUP_FAILED(
            MessageChannel.CHAT);

    private final MessageChannel channel;

    RealmMessageKey(MessageChannel channel) {
        this.channel = channel;
    }

    public MessageChannel channel() {
        return channel;
    }

    public String key() {
        return "backup." + name().substring("BACKUP_".length()).toLowerCase(java.util.Locale.ROOT);
    }

    public String template() {
        return PlayerMessages.text(key());
    }

    public String fallback(Map<String, String> values) {
        return PlayerMessages.text(key(), values);
    }
}
