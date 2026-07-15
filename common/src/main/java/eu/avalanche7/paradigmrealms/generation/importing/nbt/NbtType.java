package eu.avalanche7.paradigmrealms.generation.importing.nbt;

public enum NbtType {
    END(0), BYTE(1), SHORT(2), INT(3), LONG(4), FLOAT(5), DOUBLE(6), BYTE_ARRAY(7),
    STRING(8), LIST(9), COMPOUND(10), INT_ARRAY(11), LONG_ARRAY(12), NUMBER(99);

    private final int id;
    NbtType(int id) { this.id = id; }
    public int id() { return id; }
    public boolean accepts(NbtType actual) {
        return this == actual || (this == NUMBER && switch (actual) {
            case BYTE, SHORT, INT, LONG, FLOAT, DOUBLE -> true;
            default -> false;
        });
    }
    public static NbtType byId(int id) {
        for (NbtType type : values()) if (type.id == id && type != NUMBER) return type;
        throw new IllegalArgumentException("unsupported NBT type " + id);
    }
}
