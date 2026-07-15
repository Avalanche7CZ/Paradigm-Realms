package eu.avalanche7.paradigmrealms.operations;

public interface OperationalAuditSink extends AutoCloseable {
    void append(OperationalAuditEvent event, boolean durable);
    @Override void close();

    static OperationalAuditSink disabled() {
        return new OperationalAuditSink() {
            @Override public void append(OperationalAuditEvent event, boolean durable) {}
            @Override public void close() {}
        };
    }
}
