package eu.avalanche7.paradigmrealms.persistence.data;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public sealed interface StorageValue permits StorageValue.ObjectValue, StorageValue.ListValue,
        StorageValue.StringValue, StorageValue.LongValue, StorageValue.DoubleValue {

    record ObjectValue(Map<String, StorageValue> values) implements StorageValue {
        public ObjectValue {
            Objects.requireNonNull(values, "values");
            values = Map.copyOf(values);
        }

        public StorageValue get(String key) {
            return values.get(key);
        }
    }

    record ListValue(List<StorageValue> values) implements StorageValue {
        public ListValue {
            Objects.requireNonNull(values, "values");
            values = List.copyOf(values);
        }
    }

    record StringValue(String value) implements StorageValue {
        public StringValue {
            Objects.requireNonNull(value, "value");
        }
    }

    record LongValue(long value) implements StorageValue {
    }

    record DoubleValue(double value) implements StorageValue {
        public DoubleValue {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("stored double must be finite");
            }
        }
    }
}
