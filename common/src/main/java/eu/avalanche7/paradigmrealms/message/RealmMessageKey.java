package eu.avalanche7.paradigmrealms.message;

import java.util.Map;

public enum RealmMessageKey {
    BACKUP_CAPTURE_STARTED(
            MessageChannel.CHAT,
            "<color:aqua>Creating a backup of {realm_name} (realm #{realm_id})…</color>\n"
                    + "<color:gray>Building and container changes will be paused briefly.</color>",
            "Creating a backup of {realm_name} (realm #{realm_id}). "
                    + "Building and container changes will pause briefly."),
    BACKUP_PROGRESS(
            MessageChannel.ACTION_BAR,
            "<color:aqua>Saving realm backup… {captured}/{total} storage records</color>",
            "Saving realm backup... {captured}/{total} storage records"),
    BACKUP_LOCKED(
            MessageChannel.ACTION_BAR,
            "<color:yellow>This realm is briefly read-only while a backup is captured.</color>",
            "This realm is briefly read-only while a backup is captured."),
    BACKUP_COMPLETED(
            MessageChannel.CHAT,
            "<color:green>Backup of {realm_name} (realm #{realm_id}) completed.</color>\n"
                    + "<color:gray>{records} storage records • {size} • capture {duration}</color>",
            "Backup of {realm_name} (realm #{realm_id}) completed. "
                    + "{records} storage records | {size} | capture {duration}"),
    BACKUP_AUTOMATIC_COMPLETED(
            MessageChannel.CHAT,
            "<color:gray>An automatic backup of your realm was completed.</color>",
            "An automatic backup of your realm was completed."),
    BACKUP_AUTOMATIC_FAILED(
            MessageChannel.ADMIN_CHAT,
            "<color:red>Automatic backup for realm {realm_id} failed.</color>\n"
                    + "<color:gray>Check the server log for the internal failure code.</color>",
            "Automatic backup for realm {realm_id} failed. Check the server log."),
    BACKUP_FAILED(
            MessageChannel.CHAT,
            "<color:red>The realm backup could not be completed.</color>\n"
                    + "<color:gray>Your realm was unlocked and no existing data was changed.</color>",
            "The realm backup could not be completed. Your realm was unlocked and no existing data was changed.");

    private final MessageChannel channel;
    private final String template;
    private final String fallback;

    RealmMessageKey(MessageChannel channel, String template, String fallback) {
        this.channel = channel;
        this.template = template;
        this.fallback = fallback;
    }

    public MessageChannel channel() {
        return channel;
    }

    public String template() {
        return template;
    }

    public String fallback(Map<String, String> values) {
        String result = fallback;
        for (Map.Entry<String, String> value : values.entrySet()) {
            result = result.replace('{' + value.getKey() + '}', value.getValue());
        }
        return result;
    }
}
