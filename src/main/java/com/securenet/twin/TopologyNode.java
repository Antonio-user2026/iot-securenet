package com.securenet.twin;

import com.securenet.devices.Device;
import com.securenet.icons.AppIcons;
import javax.swing.ImageIcon;

/**
 * Nœud de la topologie réseau (Digital Twin).
 */
public class TopologyNode {

    public enum Status {
        ONLINE    ("En ligne",   "\u25CF"),
        OFFLINE   ("Hors ligne", "\u25CB"),
        SUSPICIOUS("Suspect",    "\u26A0"),
        BLOCKED   ("Bloqué",     "\u2716"),
        SCANNING  ("Scan...",    "\u21BB");

        public final String label;
        public final String symbol;
        Status(String label, String symbol) { this.label = label; this.symbol = symbol; }
    }

    public int         id;
    public String      name;
    public String      ip;
    public String      mac;
    public Device.Type type;
    public Status      status;
    public String      vendor;
    public String      firmware;
    public float       x, y;

    public TopologyNode(int id, String name, String ip, String mac,
                        Device.Type type, Status status) {
        this.id     = id;
        this.name   = name;
        this.ip     = ip;
        this.mac    = mac;
        this.type   = type;
        this.status = status;
    }

    /** Construit depuis un objet Device. */
    public static TopologyNode fromDevice(com.securenet.devices.Device d) {
        Status s = switch (d.getStatus()) {
            case ONLINE    -> Status.ONLINE;
            case OFFLINE   -> Status.OFFLINE;
            case SUSPICIOUS-> Status.SUSPICIOUS;
            case BLOCKED   -> Status.BLOCKED;
        };
        TopologyNode node = new TopologyNode(d.getId(),
            d.getName() == null ? "Device-" + d.getId() : d.getName(),
            d.getIpAddress(),
            d.getMacAddress() == null ? "—" : d.getMacAddress(),
            d.getType(), s);
        node.vendor   = d.getVendor();
        node.firmware = d.getFirmware();
        return node;
    }

    /** Retourne l'icône correspondant au type d'appareil. */
    public ImageIcon getIcon(int size) {
        if (type == null) return AppIcons.devices(size);
        return switch (type) {
            case CAMERA     -> AppIcons.camera(size);
            case SENSOR, THERMOSTAT -> AppIcons.sensor(size);
            case ROUTER     -> AppIcons.router(size);
            case GATEWAY    -> AppIcons.network(size);
            case SMART_LOCK -> AppIcons.lock(size);
            default         -> AppIcons.devices(size);
        };
    }
}
