package com.securenet.scanner;

import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Scanner réseau local (LAN).
 * Détecte les hôtes actifs sur le sous-réseau courant via ICMP ping + ARP.
 * Fonctionne sur Linux/Arch sans droits root en combinant :
 *  - InetAddress.isReachable (ICMP/TCP echo)
 *  - Parsing de /proc/net/arp pour les entrées MAC et noms
 *  - Résolution DNS inverse (hostname)
 */
public class NetworkScanner {

    /** Résultat d'un hôte découvert. */
    public static class DiscoveredHost {
        public final String ip;
        public final String hostname;
        public final String mac;
        public final String vendor;
        public final int    pingMs;

        public DiscoveredHost(String ip, String hostname, String mac,
                               String vendor, int pingMs) {
            this.ip       = ip;
            this.hostname = hostname;
            this.mac      = mac;
            this.vendor   = vendor;
            this.pingMs   = pingMs;
        }

        /** Label affiché dans la combobox de sélection d'IP. */
        public String toDisplayLabel() {
            String name = (hostname != null && !hostname.equals(ip))
                ? hostname : "unknown";
            return String.format("%s  —  %s  [%dms]", ip, name, pingMs);
        }
    }

    private final List<DiscoveredHost> discovered = new ArrayList<>();
    private volatile boolean           scanning   = false;
    private Consumer<DiscoveredHost>   onFound;
    private Runnable                   onDone;

    public void setOnFound(Consumer<DiscoveredHost> cb) { this.onFound = cb; }
    public void setOnDone(Runnable cb)                  { this.onDone  = cb; }
    public List<DiscoveredHost> getDiscovered()         { return discovered; }
    public boolean isScanning()                         { return scanning; }

    /** Lance le scan du réseau local de manière asynchrone. */
    public void scanAsync() {
        if (scanning) return;
        scanning = true;
        discovered.clear();

        Thread scanThread = new Thread(() -> {
            try {
                String localSubnet = detectLocalSubnet();
                if (localSubnet == null) {
                    // Fallback : scanner 192.168.1.x
                    localSubnet = "192.168.1";
                }
                scanSubnet(localSubnet);
            } finally {
                scanning = false;
                if (onDone != null) onDone.run();
            }
        });
        scanThread.setDaemon(true);
        scanThread.start();
    }

    /** Détecte le préfixe du sous-réseau local (ex: "192.168.1"). */
    private String detectLocalSubnet() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InterfaceAddress addr : ni.getInterfaceAddresses()) {
                    InetAddress ia = addr.getAddress();
                    if (ia instanceof Inet4Address && !ia.isLoopbackAddress()) {
                        String ip = ia.getHostAddress();
                        String[] parts = ip.split("\\.");
                        if (parts.length == 4) {
                            return parts[0] + "." + parts[1] + "." + parts[2];
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Scanner] detectSubnet: " + e.getMessage());
        }
        return null;
    }

    /** Scanne les 254 adresses du sous-réseau en parallèle. */
    private void scanSubnet(String prefix) {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        CountDownLatch  latch = new CountDownLatch(254);

        // Charger la table ARP (Linux)
        Map<String, String> arpTable = loadArpTable();

        for (int i = 1; i <= 254; i++) {
            final String ip = prefix + "." + i;
            pool.submit(() -> {
                try {
                    long t0 = System.currentTimeMillis();
                    InetAddress addr = InetAddress.getByName(ip);
                    boolean reachable = addr.isReachable(600);
                    int pingMs = (int)(System.currentTimeMillis() - t0);

                    if (!reachable) {
                        // Deuxième tentative avec un timeout plus court
                        reachable = addr.isReachable(300);
                        pingMs = (int)(System.currentTimeMillis() - t0);
                    }

                    if (reachable) {
                        String hostname = resolveHostname(addr);
                        String mac      = arpTable.getOrDefault(ip, "—");
                        String vendor   = lookupVendor(mac);
                        DiscoveredHost host = new DiscoveredHost(
                            ip, hostname, mac, vendor, pingMs);
                        discovered.add(host);
                        if (onFound != null) onFound.accept(host);
                    }
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        try { latch.await(20, TimeUnit.SECONDS); }
        catch (InterruptedException ignored) {}
        pool.shutdownNow();

        // Trier par IP
        discovered.sort(Comparator.comparing(h -> {
            try {
                String[] p = h.ip.split("\\.");
                return Integer.parseInt(p[3]);
            } catch (Exception e) { return 999; }
        }));
    }

    /** Lit /proc/net/arp pour obtenir MAC → IP (Linux). */
    private Map<String, String> loadArpTable() {
        Map<String, String> table = new HashMap<>();
        try {
            List<String> lines = java.nio.file.Files.readAllLines(
                java.nio.file.Paths.get("/proc/net/arp"));
            for (String line : lines) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length >= 4 && !parts[3].equals("00:00:00:00:00:00")
                        && !parts[3].equals("HW")) {
                    table.put(parts[0], parts[3].toUpperCase());
                }
            }
        } catch (Exception e) {
            // Non Linux ou pas de permissions → table vide
        }
        return table;
    }

    /** Résolution DNS inverse avec timeout court. */
    private String resolveHostname(InetAddress addr) {
        try {
            String canonical = addr.getCanonicalHostName();
            return canonical.equals(addr.getHostAddress()) ? addr.getHostAddress() : canonical;
        } catch (Exception e) {
            return addr.getHostAddress();
        }
    }

    /**
     * Lookup vendeur basé sur le préfixe OUI de l'adresse MAC.
     * Contient les préfixes les plus courants en IoT.
     */
    private String lookupVendor(String mac) {
        if (mac == null || mac.equals("—") || mac.length() < 8) return "Unknown";
        String oui = mac.replace(":", "").substring(0, 6).toUpperCase();
        return switch (oui) {
            case "B827EB", "DCA632", "E45F01" -> "Raspberry Pi";
            case "001122", "A4CF12" -> "Cisco";
            case "0050F2", "00155D" -> "Microsoft";
            case "ACDE48", "F4F5D8" -> "Apple";
            case "40A36B", "74DA38" -> "Samsung";
            case "B4E62D", "606BBD" -> "Xiaomi";
            case "9C9D7E", "98F170" -> "Hikvision";
            case "001A7D", "00E04C" -> "Dahua";
            case "000C29", "005056" -> "VMware";
            case "080027" -> "VirtualBox";
            case "A8032A", "B05134" -> "Philips Hue";
            case "18B430", "647FDA" -> "Nest (Google)";
            case "D83134", "8C8590" -> "TP-Link";
            case "F09FC2", "14CC20" -> "Aqara";
            case "0024E4", "FC3FDB" -> "Yale";
            default -> "Unknown";
        };
    }

    /** Scan rapide de la machine locale pour obtenir ses propres infos. */
    public static DiscoveredHost getLocalHost() {
        try {
            InetAddress local = InetAddress.getLocalHost();
            long t0 = System.currentTimeMillis();
            local.isReachable(200);
            int pingMs = (int)(System.currentTimeMillis() - t0);
            return new DiscoveredHost(
                local.getHostAddress(),
                local.getHostName(),
                "—",
                "Local Machine",
                pingMs);
        } catch (Exception e) {
            return new DiscoveredHost("127.0.0.1", "localhost", "—", "Local", 0);
        }
    }
}
