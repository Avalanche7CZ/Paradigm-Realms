package eu.avalanche7.paradigmrealms.persistence.migration;

import eu.avalanche7.paradigmrealms.persistence.data.StorageValue;

public interface StorageMigration {
    int fromVersion();

    int toVersion();

    StorageValue.ObjectValue migrate(StorageValue.ObjectValue source);
}
