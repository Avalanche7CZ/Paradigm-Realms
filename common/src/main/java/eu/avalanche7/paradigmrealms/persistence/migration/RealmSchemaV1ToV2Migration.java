package eu.avalanche7.paradigmrealms.persistence.migration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import eu.avalanche7.paradigmrealms.persistence.codec.MalformedStorageException;
import eu.avalanche7.paradigmrealms.persistence.data.StorageValue;

public final class RealmSchemaV1ToV2Migration implements StorageMigration {
    @Override public int fromVersion() { return 1; }
    @Override public int toVersion() { return 2; }

    @Override
    public StorageValue.ObjectValue migrate(StorageValue.ObjectValue source) {
        Map<String, StorageValue> root = new LinkedHashMap<>(source.values());
        StorageValue rawRealms = root.get("realms");
        if (!(rawRealms instanceof StorageValue.ListValue realms)) {
            throw new MalformedStorageException("root.realms", "expected list");
        }
        List<StorageValue> migrated = new ArrayList<>(realms.values().size());
        for (int i = 0; i < realms.values().size(); i++) {
            if (!(realms.values().get(i) instanceof StorageValue.ObjectValue realm)) {
                throw new MalformedStorageException("root.realms[" + i + ']', "expected object");
            }
            migrated.add(migrateRealm(realm, "root.realms[" + i + ']'));
        }
        root.put("realms", new StorageValue.ListValue(migrated));
        root.put("schema_version", new StorageValue.LongValue(2));
        return new StorageValue.ObjectValue(root);
    }

    private static StorageValue.ObjectValue migrateRealm(StorageValue.ObjectValue source, String path) {
        Map<String, StorageValue> realm = new LinkedHashMap<>(source.values());
        StorageValue id = realm.get("id");
        if (!(id instanceof StorageValue.LongValue number) || number.value() < 1) {
            throw new MalformedStorageException(path + ".id", "expected positive integer");
        }
        realm.put("record_schema", new StorageValue.LongValue(2));
        realm.putIfAbsent("display_name", new StorageValue.StringValue("Realm #" + number.value()));
        realm.putIfAbsent("description", new StorageValue.StringValue(""));
        realm.putIfAbsent("listed", new StorageValue.LongValue(0));
        realm.putIfAbsent("managers", new StorageValue.ListValue(List.of()));
        realm.putIfAbsent("bans", new StorageValue.ListValue(List.of()));
        realm.putIfAbsent("pvp", new StorageValue.LongValue(0));
        realm.putIfAbsent("explosions", new StorageValue.LongValue(0));
        realm.putIfAbsent("mob_griefing", new StorageValue.LongValue(0));
        realm.putIfAbsent("visitor_interaction", new StorageValue.LongValue(0));
        realm.putIfAbsent("visitor_containers", new StorageValue.LongValue(0));
        return new StorageValue.ObjectValue(realm);
    }
}
