package com.securenet.devices;

import com.securenet.icons.AppIcons;

import javax.swing.*;

/**
 * Modèle d'un appareil IoT.
 */
public class Device {

    public enum Type   { CAMERA, SENSOR, ROUTER, GATEWAY, SMART_LOCK, THERMOSTAT, UNKNOWN }
    public enum Status { ONLINE, OFFLINE, SUSPICIOUS, BLOCKED }

    private int    id;
    private String name;
    private String ipAddress;
    private String macAddress;
    private Type   type;
    private Status status;
    private String vendor;
    private String firmware;
    private String segment;

    public Device() {}

    public Device(String name, String ipAddress, String macAddress, Type type) {
        this.name       = name;
        this.ipAddress  = ipAddress;
        this.macAddress = macAddress;
        this.type       = type;
        this.status     = Status.ONLINE;
        this.segment    = "default";
    }

    // ─── Getters / Setters ─────────────────────────────────────────────────
    public int    getId()         { return id; }
    public void   setId(int id)   { this.id = id; }
    public String getName()        { return name; }
    public void   setName(String n){ this.name = n; }
    public String getIpAddress()   { return ipAddress; }
    public void   setIpAddress(String ip) { this.ipAddress = ip; }
    public String getMacAddress()  { return macAddress; }
    public void   setMacAddress(String m) { this.macAddress = m; }
    public Type   getType()        { return type; }
    public void   setType(Type t)  { this.type = t; }
    public Status getStatus()      { return status; }
    public void   setStatus(Status s){ this.status = s; }
    public String getVendor()      { return vendor; }
    public void   setVendor(String v){ this.vendor = v; }
    public String getFirmware()    { return firmware; }
    public void   setFirmware(String f){ this.firmware = f; }
    public String getSegment()     { return segment; }
    public void   setSegment(String s){ this.segment = s; }

    // ─── Icône vectorielle ─────────────────────────────────────────────────
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
