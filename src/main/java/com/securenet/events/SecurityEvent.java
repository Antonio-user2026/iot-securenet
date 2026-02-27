package com.securenet.events;

import java.time.LocalDateTime;

/**
 * Événement de sécurité générique circulant sur l'EventBus.
 */
public class SecurityEvent {

    public enum Severity { INFO, WARNING, CRITICAL }
    public enum Type     { IDS_ALERT, FIREWALL_BLOCK, DEVICE_CONNECT,
                           DEVICE_DISCONNECT, SIEM_CORRELATION, SCAN_RESULT }

    private final Type          type;
    private final Severity      severity;
    private final String        sourceIP;
    private final String        description;
    private final LocalDateTime timestamp;

    public SecurityEvent(Type type, Severity severity, String sourceIP, String description) {
        this.type        = type;
        this.severity    = severity;
        this.sourceIP    = sourceIP;
        this.description = description;
        this.timestamp   = LocalDateTime.now();
    }

    public Type          getType()        { return type; }
    public Severity      getSeverity()    { return severity; }
    public String        getSourceIP()    { return sourceIP; }
    public String        getDescription() { return description; }
    public LocalDateTime getTimestamp()   { return timestamp; }

    @Override
    public String toString() {
        return String.format("[%s] %s | %s | %s | %s",
            timestamp, severity, type, sourceIP, description);
    }
}
