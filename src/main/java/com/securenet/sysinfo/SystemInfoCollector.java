package com.securenet.sysinfo;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;

/**
 * Collecteur d'informations système pour la machine locale et les hôtes distants.
 *
 * Pour la machine locale (Linux/Arch) :
 *   - CPU : /proc/cpuinfo, /proc/stat
 *   - RAM : /proc/meminfo
 *   - Stockage : /proc/mounts + statvfs
 *   - OS : /etc/os-release, uname
 *   - Réseau : /proc/net/dev
 *
 * Pour les hôtes distants :
 *   - Simulation réaliste basée sur le type d'appareil
 *   - SNMP (future extension)
 */
public class SystemInfoCollector {

    /** Snapshot d'informations système. */
    public static class SystemInfo {
        // Identification
        public String hostname      = "unknown";
        public String ipAddress     = "—";
        public String osName        = "—";
        public String osVersion     = "—";
        public String kernelVersion = "—";
        public String architecture  = "—";
        public String uptime        = "—";

        // CPU
        public String cpuModel      = "—";
        public int    cpuCores      = 0;
        public double cpuUsagePercent = 0.0;
        public double cpuTempCelsius  = 0.0;
        public String cpuFrequency  = "—";

        // RAM
        public long   ramTotalMB    = 0;
        public long   ramUsedMB     = 0;
        public long   ramFreeMB     = 0;
        public double ramUsagePercent = 0.0;

        // Stockage
        public long   diskTotalGB   = 0;
        public long   diskUsedGB    = 0;
        public long   diskFreeGB    = 0;
        public double diskUsagePercent = 0.0;

        // Réseau
        public String macAddress    = "—";
        public long   netRxKBps     = 0;
        public long   netTxKBps     = 0;
        public int    openPorts     = 0;
        public String openPortsList = "—";

        // Processus
        public int    processCount  = 0;
        public String topProcess    = "—";

        // Batterie (IoT)
        public int    batteryPercent = -1; // -1 = pas de batterie
        public boolean isRemote      = false;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Machine locale
    // ══════════════════════════════════════════════════════════════════════

    public static SystemInfo collectLocal() {
        SystemInfo info = new SystemInfo();
        info.isRemote = false;

        try { info.hostname  = InetAddress.getLocalHost().getHostName(); } catch (Exception e) {}
        try { info.ipAddress = InetAddress.getLocalHost().getHostAddress(); } catch (Exception e) {}

        readOsRelease(info);
        readCpuInfo(info);
        readCpuUsage(info);
        readMemInfo(info);
        readDiskInfo(info);
        readUname(info);
        readUptime(info);
        readNetworkStats(info);
        readProcessCount(info);

        return info;
    }

    private static void readOsRelease(SystemInfo info) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/etc/os-release"));
            for (String line : lines) {
                if (line.startsWith("PRETTY_NAME=")) {
                    info.osName = line.split("=", 2)[1].replace("\"", "");
                } else if (line.startsWith("VERSION_ID=")) {
                    info.osVersion = line.split("=", 2)[1].replace("\"", "");
                }
            }
        } catch (Exception e) {
            info.osName = System.getProperty("os.name", "Linux");
        }
    }

    private static void readCpuInfo(SystemInfo info) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/cpuinfo"));
            int cores = 0;
            for (String line : lines) {
                if (line.startsWith("model name") && info.cpuModel.equals("—")) {
                    info.cpuModel = line.split(":", 2)[1].trim();
                }
                if (line.startsWith("processor")) cores++;
                if (line.startsWith("cpu MHz") && info.cpuFrequency.equals("—")) {
                    try {
                        double mhz = Double.parseDouble(line.split(":", 2)[1].trim());
                        info.cpuFrequency = String.format("%.1f GHz", mhz / 1000.0);
                    } catch (Exception ignored) {}
                }
            }
            info.cpuCores = Math.max(1, cores);
        } catch (Exception e) {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            info.cpuModel  = "CPU " + os.getArch();
            info.cpuCores  = os.getAvailableProcessors();
            info.architecture = os.getArch();
        }
    }

    private static void readCpuUsage(SystemInfo info) {
        try {
            // Lire /proc/stat deux fois avec 100ms d'intervalle
            long[] s1 = parseStat();
            Thread.sleep(150);
            long[] s2 = parseStat();
            long idle1  = s1[3], idle2  = s2[3];
            long total1 = sum(s1),  total2 = sum(s2);
            double usage = 100.0 * (1.0 - (double)(idle2 - idle1) / (total2 - total1));
            info.cpuUsagePercent = Math.max(0, Math.min(100, usage));
        } catch (Exception e) {
            OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
            info.cpuUsagePercent = os.getSystemLoadAverage() * 10;
        }

        // Température CPU (thermal zones Linux)
        try {
            File thermalDir = new File("/sys/class/thermal");
            if (thermalDir.exists()) {
                for (File zone : thermalDir.listFiles()) {
                    File tempFile = new File(zone, "temp");
                    if (tempFile.exists()) {
                        String tempStr = Files.readString(tempFile.toPath()).trim();
                        info.cpuTempCelsius = Double.parseDouble(tempStr) / 1000.0;
                        break;
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private static long[] parseStat() throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("/proc/stat"));
        String[] parts = lines.get(0).trim().split("\\s+");
        long[] vals = new long[parts.length - 1];
        for (int i = 1; i < parts.length; i++) vals[i-1] = Long.parseLong(parts[i]);
        return vals;
    }

    private static long sum(long[] arr) {
        long s = 0; for (long v : arr) s += v; return s;
    }

    private static void readMemInfo(SystemInfo info) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/meminfo"));
            long total = 0, free = 0, buffers = 0, cached = 0;
            for (String line : lines) {
                if      (line.startsWith("MemTotal:"))  total   = parseKb(line);
                else if (line.startsWith("MemFree:"))   free    = parseKb(line);
                else if (line.startsWith("Buffers:"))   buffers = parseKb(line);
                else if (line.startsWith("Cached:"))    cached  = parseKb(line);
            }
            info.ramTotalMB = total / 1024;
            long actualFree = free + buffers + cached;
            info.ramFreeMB  = actualFree / 1024;
            info.ramUsedMB  = info.ramTotalMB - info.ramFreeMB;
            if (info.ramTotalMB > 0) {
                info.ramUsagePercent = 100.0 * info.ramUsedMB / info.ramTotalMB;
            }
        } catch (Exception e) {
            info.ramTotalMB  = Runtime.getRuntime().totalMemory() / (1024 * 1024);
            info.ramUsedMB   = (Runtime.getRuntime().totalMemory() -
                                Runtime.getRuntime().freeMemory()) / (1024*1024);
            info.ramFreeMB   = info.ramTotalMB - info.ramUsedMB;
            info.ramUsagePercent = 100.0 * info.ramUsedMB / info.ramTotalMB;
        }
    }

    private static long parseKb(String line) {
        try { return Long.parseLong(line.split("\\s+")[1]); } catch (Exception e) { return 0; }
    }

    private static void readDiskInfo(SystemInfo info) {
        try {
            File root = new File("/");
            info.diskTotalGB = root.getTotalSpace() / (1024*1024*1024);
            info.diskFreeGB  = root.getFreeSpace()  / (1024*1024*1024);
            info.diskUsedGB  = info.diskTotalGB - info.diskFreeGB;
            if (info.diskTotalGB > 0) {
                info.diskUsagePercent = 100.0 * info.diskUsedGB / info.diskTotalGB;
            }
        } catch (Exception ignored) {}
    }

    private static void readUname(SystemInfo info) {
        try {
            Process p = Runtime.getRuntime().exec("uname -r");
            info.kernelVersion = new String(p.getInputStream().readAllBytes()).trim();
            info.architecture  = System.getProperty("os.arch", "x86_64");
        } catch (Exception e) {
            info.kernelVersion = System.getProperty("os.version", "—");
            info.architecture  = System.getProperty("os.arch", "—");
        }
    }

    private static void readUptime(SystemInfo info) {
        try {
            String raw  = Files.readString(Paths.get("/proc/uptime")).trim().split(" ")[0];
            long secs   = (long) Double.parseDouble(raw);
            long days   = secs / 86400;
            long hours  = (secs % 86400) / 3600;
            long mins   = (secs % 3600) / 60;
            if (days > 0)  info.uptime = days  + "j " + hours + "h " + mins + "m";
            else if (hours > 0) info.uptime = hours + "h " + mins + "m";
            else           info.uptime = mins  + "m";
        } catch (Exception e) {
            long upMs = ManagementFactory.getRuntimeMXBean().getUptime();
            info.uptime = (upMs / 3600000) + "h " + ((upMs % 3600000) / 60000) + "m";
        }
    }

    private static void readNetworkStats(SystemInfo info) {
        try {
            List<String> lines = Files.readAllLines(Paths.get("/proc/net/dev"));
            long rx = 0, tx = 0;
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("eth") || line.startsWith("enp") ||
                    line.startsWith("wlan") || line.startsWith("wlp")) {
                    String[] parts = line.split("\\s+");
                    if (parts.length > 9) {
                        rx += Long.parseLong(parts[1]);
                        tx += Long.parseLong(parts[9]);
                    }
                }
            }
            info.netRxKBps = rx / 1024;
            info.netTxKBps = tx / 1024;
        } catch (Exception ignored) {}
    }

    private static void readProcessCount(SystemInfo info) {
        try {
            File proc = new File("/proc");
            if (proc.exists()) {
                int count = 0;
                for (File f : proc.listFiles()) {
                    try { Integer.parseInt(f.getName()); count++; }
                    catch (NumberFormatException ignored) {}
                }
                info.processCount = count;
            }
        } catch (Exception ignored) {}
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Appareil distant (simulation réaliste par type)
    // ══════════════════════════════════════════════════════════════════════

    private static final Random rnd = new Random();

    public static SystemInfo simulateRemote(String ip, String name,
                                              com.securenet.devices.Device.Type type,
                                              String vendor) {
        SystemInfo info = new SystemInfo();
        info.isRemote  = true;
        info.ipAddress = ip;
        info.hostname  = name;

        return switch (type) {
            case ROUTER    -> simulateRouter(info, vendor);
            case GATEWAY   -> simulateGateway(info, vendor);
            case CAMERA    -> simulateCamera(info, vendor);
            case SENSOR, THERMOSTAT -> simulateSensor(info, vendor, type);
            case SMART_LOCK -> simulateSmartLock(info, vendor);
            default        -> simulateGeneric(info, vendor);
        };
    }

    private static SystemInfo simulateRouter(SystemInfo info, String v) {
        info.osName = "Cisco IOS / OpenWrt";
        info.cpuModel = "MIPS 74Kc V5.0  @ 880 MHz";
        info.cpuCores = 2;
        info.cpuUsagePercent  = 15 + rnd.nextInt(20);
        info.cpuTempCelsius   = 55 + rnd.nextInt(15);
        info.ramTotalMB = 256; info.ramUsedMB = 60 + rnd.nextInt(80);
        info.ramFreeMB  = info.ramTotalMB - info.ramUsedMB;
        info.ramUsagePercent  = 100.0 * info.ramUsedMB / info.ramTotalMB;
        info.diskTotalGB = 0; info.diskUsedGB = 0;
        info.uptime      = (10 + rnd.nextInt(200)) + "j " + rnd.nextInt(24) + "h";
        info.netRxKBps   = 1024 + rnd.nextInt(10000);
        info.netTxKBps   = 512  + rnd.nextInt(5000);
        info.openPorts   = 4;
        info.openPortsList = "22 (SSH), 80 (HTTP), 443 (HTTPS), 8080 (Admin)";
        info.processCount = 45 + rnd.nextInt(30);
        return info;
    }

    private static SystemInfo simulateCamera(SystemInfo info, String v) {
        info.osName = "Embedded Linux (HiSilicon)";
        info.cpuModel = "ARM Cortex-A7 @ 900 MHz";
        info.cpuCores = 2;
        info.cpuUsagePercent  = 40 + rnd.nextInt(30);
        info.cpuTempCelsius   = 60 + rnd.nextInt(20);
        info.ramTotalMB = 128; info.ramUsedMB = 70 + rnd.nextInt(40);
        info.ramFreeMB  = info.ramTotalMB - info.ramUsedMB;
        info.ramUsagePercent  = 100.0 * info.ramUsedMB / info.ramTotalMB;
        info.diskTotalGB = 32;  // carte SD
        info.diskUsedGB  = 15 + rnd.nextInt(15);
        info.diskFreeGB  = info.diskTotalGB - info.diskUsedGB;
        info.diskUsagePercent = 100.0 * info.diskUsedGB / info.diskTotalGB;
        info.uptime      = rnd.nextInt(90) + "j " + rnd.nextInt(24) + "h";
        info.netRxKBps   = 50 + rnd.nextInt(100);
        info.netTxKBps   = 2048 + rnd.nextInt(8192);  // streaming vidéo
        info.openPorts   = 3;
        info.openPortsList = "554 (RTSP), 80 (HTTP), 8000 (SDK)";
        info.processCount = 20 + rnd.nextInt(10);
        return info;
    }

    private static SystemInfo simulateSensor(SystemInfo info, String v,
                                               com.securenet.devices.Device.Type type) {
        info.osName = "FreeRTOS / Zephyr";
        info.cpuModel = type == com.securenet.devices.Device.Type.THERMOSTAT
            ? "ARM Cortex-M4 @ 120 MHz" : "ESP32 @ 240 MHz";
        info.cpuCores = 1;
        info.cpuUsagePercent  = 5 + rnd.nextInt(15);
        info.cpuTempCelsius   = 35 + rnd.nextInt(10);
        info.ramTotalMB = 4;
        info.ramUsedMB  = 1 + rnd.nextInt(2);
        info.ramFreeMB  = info.ramTotalMB - info.ramUsedMB;
        info.ramUsagePercent  = 100.0 * info.ramUsedMB / info.ramTotalMB;
        info.diskTotalGB = 0;  // flash interne : quelques MB
        info.uptime      = rnd.nextInt(180) + "j " + rnd.nextInt(24) + "h";
        info.batteryPercent = 30 + rnd.nextInt(70);
        info.netRxKBps   = 1; info.netTxKBps = 1;
        info.openPorts   = 1;
        info.openPortsList = "1883 (MQTT)";
        info.processCount = 5 + rnd.nextInt(5);
        return info;
    }

    private static SystemInfo simulateGateway(SystemInfo info, String v) {
        info.osName = "Embedded Linux";
        info.cpuModel = "ARM Cortex-A53 @ 1.2 GHz";
        info.cpuCores = 4;
        info.cpuUsagePercent  = 20 + rnd.nextInt(30);
        info.cpuTempCelsius   = 48 + rnd.nextInt(15);
        info.ramTotalMB = 512;
        info.ramUsedMB  = 150 + rnd.nextInt(200);
        info.ramFreeMB  = info.ramTotalMB - info.ramUsedMB;
        info.ramUsagePercent  = 100.0 * info.ramUsedMB / info.ramTotalMB;
        info.diskTotalGB = 8; info.diskUsedGB = 2 + rnd.nextInt(4);
        info.diskFreeGB  = info.diskTotalGB - info.diskUsedGB;
        info.diskUsagePercent = 100.0 * info.diskUsedGB / info.diskTotalGB;
        info.uptime      = (rnd.nextInt(300)) + "j";
        info.netRxKBps   = 200 + rnd.nextInt(500);
        info.netTxKBps   = 100 + rnd.nextInt(300);
        info.openPorts   = 5;
        info.openPortsList = "80, 443, 1883 (MQTT), 5683 (CoAP), 8883 (MQTT-TLS)";
        info.processCount = 35 + rnd.nextInt(20);
        return info;
    }

    private static SystemInfo simulateSmartLock(SystemInfo info, String v) {
        info.osName = "FreeRTOS";
        info.cpuModel = "ARM Cortex-M0+ @ 64 MHz";
        info.cpuCores = 1;
        info.cpuUsagePercent  = 2 + rnd.nextInt(8);
        info.ramTotalMB = 1; info.ramUsedMB = 0;
        info.ramFreeMB  = 1; info.ramUsagePercent = 40.0 + rnd.nextInt(20);
        info.uptime      = rnd.nextInt(365) + "j";
        info.batteryPercent = 50 + rnd.nextInt(50);
        info.openPorts   = 1;
        info.openPortsList = "BLE (Bluetooth LE)";
        return info;
    }

    private static SystemInfo simulateGeneric(SystemInfo info, String v) {
        info.osName = "Linux Embedded";
        info.cpuModel = "ARM Cortex-A7 @ 1 GHz";
        info.cpuCores = 2;
        info.cpuUsagePercent  = 20 + rnd.nextInt(40);
        info.cpuTempCelsius   = 45 + rnd.nextInt(20);
        info.ramTotalMB = 256; info.ramUsedMB = 80 + rnd.nextInt(100);
        info.ramFreeMB  = info.ramTotalMB - info.ramUsedMB;
        info.ramUsagePercent  = 100.0 * info.ramUsedMB / info.ramTotalMB;
        info.diskTotalGB = 4;
        info.uptime      = rnd.nextInt(100) + "j";
        info.netRxKBps   = 10 + rnd.nextInt(100);
        info.netTxKBps   = 5  + rnd.nextInt(50);
        return info;
    }
}
