package com.securenet.devices;

import com.securenet.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service CRUD pour les appareils IoT.
 */
public class DeviceService {

    private static final DeviceService INSTANCE = new DeviceService();
    public static DeviceService getInstance() { return INSTANCE; }
    private DeviceService() {}

    // ─── CRUD ──────────────────────────────────────────────────────────────

    public List<Device> findAll() {
        List<Device> list = new ArrayList<>();
        String sql = "SELECT * FROM devices ORDER BY last_seen DESC";
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            Statement st = conn.createStatement();
            ResultSet rs = st.executeQuery(sql);
            while (rs.next()) list.add(mapRow(rs));
            rs.close(); st.close();
        } catch (SQLException e) {
            System.err.println("[DeviceService] findAll: " + e.getMessage());
        }
        return list;
    }

    public void save(Device d) {
        String sql = "INSERT INTO devices (name,ip_address,mac_address,type,status,vendor,firmware,segment)" +
                     " VALUES (?,?,?,?,?,?,?,?)";
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, d.getName());
            ps.setString(2, d.getIpAddress());
            ps.setString(3, d.getMacAddress());
            ps.setString(4, d.getType().name());
            ps.setString(5, d.getStatus().name());
            ps.setString(6, d.getVendor());
            ps.setString(7, d.getFirmware());
            ps.setString(8, d.getSegment());
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys.next()) d.setId(keys.getInt(1));
            ps.close();
        } catch (SQLException e) {
            System.err.println("[DeviceService] save: " + e.getMessage());
        }
    }

    public void update(Device d) {
        String sql = "UPDATE devices SET name=?,ip_address=?,mac_address=?,type=?,status=?," +
                     "vendor=?,firmware=?,segment=? WHERE id=?";
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, d.getName()); ps.setString(2, d.getIpAddress());
            ps.setString(3, d.getMacAddress()); ps.setString(4, d.getType().name());
            ps.setString(5, d.getStatus().name()); ps.setString(6, d.getVendor());
            ps.setString(7, d.getFirmware()); ps.setString(8, d.getSegment());
            ps.setInt(9, d.getId());
            ps.executeUpdate(); ps.close();
        } catch (SQLException e) {
            System.err.println("[DeviceService] update: " + e.getMessage());
        }
    }

    public void updateStatus(int id, Device.Status status) {
        String sql = "UPDATE devices SET status=?, last_seen=CURRENT_TIMESTAMP WHERE id=?";
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, status.name()); ps.setInt(2, id);
            ps.executeUpdate(); ps.close();
        } catch (SQLException e) {
            System.err.println("[DeviceService] updateStatus: " + e.getMessage());
        }
    }

    public void delete(int id) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            // Supprimer aussi les connexions liées (topologie)
            PreparedStatement ps1 = conn.prepareStatement(
                "DELETE FROM device_connections WHERE source_id=? OR target_id=?");
            ps1.setInt(1, id); ps1.setInt(2, id); ps1.executeUpdate(); ps1.close();
            PreparedStatement ps2 = conn.prepareStatement("DELETE FROM devices WHERE id=?");
            ps2.setInt(1, id); ps2.executeUpdate(); ps2.close();
        } catch (SQLException e) {
            System.err.println("[DeviceService] delete: " + e.getMessage());
        }
    }

    // ─── Données de démo ───────────────────────────────────────────────────

    public void insertDemoIfEmpty() {
        if (!findAll().isEmpty()) return;

        Object[][] demo = {
            {"Routeur Principal",  "192.168.1.1",   "AA:BB:CC:DD:EE:01", "ROUTER",    "ONLINE",  "Cisco",    "15.9.3"},
            {"Gateway Zigbee",     "192.168.1.2",   "AA:BB:CC:DD:EE:02", "GATEWAY",   "ONLINE",  "Philips",  "2.5.1"},
            {"Caméra Entrée",      "192.168.1.101", "AA:BB:CC:DD:EE:03", "CAMERA",    "ONLINE",  "Hikvision","V5.7.0"},
            {"Caméra Jardin",      "192.168.1.102", "AA:BB:CC:DD:EE:04", "CAMERA",    "OFFLINE", "Dahua",    "V4.2.1"},
            {"Capteur Temp. Salon","192.168.1.110", "AA:BB:CC:DD:EE:05", "SENSOR",    "ONLINE",  "Xiaomi",   "1.4.6"},
            {"Capteur Humidité",   "192.168.1.111", "AA:BB:CC:DD:EE:06", "SENSOR",    "ONLINE",  "Aqara",    "3.1.0"},
            {"Serrure Bureau",     "192.168.1.120", "AA:BB:CC:DD:EE:07", "SMART_LOCK","ONLINE",  "Yale",     "2.3.1"},
            {"Thermostat Salon",   "192.168.1.130", "AA:BB:CC:DD:EE:08", "THERMOSTAT","ONLINE",  "Nest",     "5.9.3"},
        };

        for (Object[] row : demo) {
            Device d = new Device();
            d.setName((String)    row[0]);
            d.setIpAddress((String) row[1]);
            d.setMacAddress((String) row[2]);
            d.setType(Device.Type.valueOf((String) row[3]));
            d.setStatus(Device.Status.valueOf((String) row[4]));
            d.setVendor((String) row[5]);
            d.setFirmware((String) row[6]);
            d.setSegment("default");
            save(d);
        }
    }

    // ─── Mapping ───────────────────────────────────────────────────────────

    private Device mapRow(ResultSet rs) throws SQLException {
        Device d = new Device();
        d.setId(rs.getInt("id"));
        d.setName(rs.getString("name"));
        d.setIpAddress(rs.getString("ip_address"));
        d.setMacAddress(rs.getString("mac_address"));
        try { d.setType(Device.Type.valueOf(rs.getString("type"))); }
        catch (Exception e) { d.setType(Device.Type.UNKNOWN); }
        try { d.setStatus(Device.Status.valueOf(rs.getString("status"))); }
        catch (Exception e) { d.setStatus(Device.Status.OFFLINE); }
        try { d.setVendor(rs.getString("vendor")); } catch (Exception ignored) {}
        try { d.setFirmware(rs.getString("firmware")); } catch (Exception ignored) {}
        try { d.setSegment(rs.getString("segment")); } catch (Exception ignored) {}
        return d;
    }
}
