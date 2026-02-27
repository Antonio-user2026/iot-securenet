package com.securenet.db;

import com.securenet.config.AppConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;

/**
 * Gestionnaire de base de données singleton.
 * Supporte SQLite (mode dev) et MySQL (mode production).
 * Utilise HikariCP pour le pool de connexions MySQL.
 */
public class DatabaseManager {

    private static final DatabaseManager INSTANCE = new DatabaseManager();

    private HikariDataSource dataSource;
    private Connection       sqliteConnection;
    private String           currentMode;

    private DatabaseManager() {}
    public static DatabaseManager getInstance() { return INSTANCE; }

    // ══════════════════════════════════════════════════════════════════════
    //  Initialisation
    // ══════════════════════════════════════════════════════════════════════

    public void initialize() {
        currentMode = AppConfig.getInstance().getDbMode();
        System.out.println("[DB] Mode : " + currentMode.toUpperCase());
        if ("mysql".equalsIgnoreCase(currentMode)) {
            initMySQL();
        } else {
            initSQLite();
        }
        createTables();
        System.out.println("[DB] Base de données initialisée.");
    }

    private void initSQLite() {
        try {
            Class.forName("org.sqlite.JDBC");
            sqliteConnection = DriverManager.getConnection("jdbc:sqlite:securenet.db");
            try (Statement st = sqliteConnection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
            }
        } catch (Exception e) {
            throw new RuntimeException("Impossible d'initialiser SQLite", e);
        }
    }

    private void initMySQL() {
        AppConfig cfg = AppConfig.getInstance();
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true",
            cfg.getDbHost(), cfg.getDbPort(), cfg.getDbName()));
        config.setUsername(cfg.getDbUser());
        config.setPassword(cfg.getDbPassword());
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setPoolName("SecureNetPool");
        try {
            dataSource = new HikariDataSource(config);
            System.out.println("[DB] MySQL connecté : " + cfg.getDbHost());
        } catch (Exception e) {
            System.err.println("[DB] MySQL non disponible, fallback SQLite : " + e.getMessage());
            currentMode = "sqlite";
            initSQLite();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Connexion
    // ══════════════════════════════════════════════════════════════════════

    public Connection getConnection() throws SQLException {
        if ("mysql".equalsIgnoreCase(currentMode) && dataSource != null) {
            return dataSource.getConnection();
        }
        return sqliteConnection;
    }

    /** Helper pour exécuter un bloc sans gérer la connexion manuellement. */
    public void exec(SQLConsumer<Connection> consumer) {
        boolean mySQL = isMySQL();
        try {
            Connection conn = getConnection();
            consumer.accept(conn);
            if (mySQL) conn.close(); // retourner au pool HikariCP
        } catch (Exception e) {
            System.err.println("[DB] exec: " + e.getMessage());
        }
    }

    @FunctionalInterface
    public interface SQLConsumer<T> { void accept(T t) throws Exception; }

    public boolean isMySQL()  { return "mysql".equalsIgnoreCase(currentMode); }
    public boolean isSQLite() { return !isMySQL(); }
    public String  getMode()  { return currentMode; }

    // ══════════════════════════════════════════════════════════════════════
    //  Test MySQL externe
    // ══════════════════════════════════════════════════════════════════════

    public static boolean testMySQLConnection(String host, int port,
                                               String db, String user, String pass) {
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?useSSL=false&serverTimezone=UTC&connectTimeout=3000&allowPublicKeyRetrieval=true",
            host, port, db);
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            return c.isValid(2);
        } catch (Exception e) { return false; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  DDL — Création des tables (SQLite + MySQL)
    // ══════════════════════════════════════════════════════════════════════

    private void createTables() {
        boolean mysql = isMySQL();
        String ai   = mysql ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        String dt   = mysql ? "DATETIME DEFAULT NOW()" : "DATETIME DEFAULT CURRENT_TIMESTAMP";
        String txt  = mysql ? "VARCHAR(255)" : "TEXT";
        String ins  = mysql ? "INSERT IGNORE" : "INSERT OR IGNORE";

        String[] ddl = {
            // users
            "CREATE TABLE IF NOT EXISTS users (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  username " + txt + " NOT NULL UNIQUE," +
            "  password " + txt + " NOT NULL," +
            "  role     " + txt + " NOT NULL DEFAULT 'analyst'," +
            "  email    " + txt + "," +
            "  full_name " + txt + "," +
            "  avatar_color " + txt + " DEFAULT '#0096FF'," +
            "  created_at " + dt + ")",

            ins + " INTO users (username,password,role,full_name) VALUES " +
            "('admin','8c6976e5b5410415bde908bd4dee15dfb167a9c873fc4bb8a81f6f2ab448a918','admin','Administrateur')",

            // devices
            "CREATE TABLE IF NOT EXISTS devices (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  name " + txt + "," +
            "  ip_address " + txt + " NOT NULL," +
            "  mac_address " + txt + "," +
            "  type " + txt + "," +
            "  status " + txt + " DEFAULT 'OFFLINE'," +
            "  vendor " + txt + "," +
            "  firmware " + txt + "," +
            "  segment " + txt + " DEFAULT 'default'," +
            "  pos_x REAL DEFAULT 0," +
            "  pos_y REAL DEFAULT 0," +
            "  last_seen " + dt + ")",

            // device_connections (topologie)
            "CREATE TABLE IF NOT EXISTS device_connections (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  source_id INTEGER NOT NULL," +
            "  target_id INTEGER NOT NULL," +
            "  link_type " + txt + " DEFAULT 'ethernet'," +
            "  bandwidth INTEGER DEFAULT 100)",

            // security_events
            "CREATE TABLE IF NOT EXISTS security_events (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  type " + txt + "," +
            "  severity " + txt + "," +
            "  source_ip " + txt + "," +
            "  description " + txt + "," +
            "  timestamp " + dt + ")",

            // firewall_rules
            "CREATE TABLE IF NOT EXISTS firewall_rules (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  source_ip " + txt + "," +
            "  dest_ip " + txt + "," +
            "  port INTEGER," +
            "  protocol " + txt + "," +
            "  action " + txt + "," +
            "  enabled INTEGER DEFAULT 1," +
            "  created_at " + dt + ")",

            // siem_alerts
            "CREATE TABLE IF NOT EXISTS siem_alerts (" +
            "  id INTEGER PRIMARY KEY " + ai + "," +
            "  level " + txt + "," +
            "  source " + txt + "," +
            "  description " + txt + "," +
            "  acknowledged INTEGER DEFAULT 0," +
            "  timestamp " + dt + ")"
        };

        exec(conn -> {
            try (Statement stmt = conn.createStatement()) {
                for (String sql : ddl) stmt.execute(sql);
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
        try {
            if (sqliteConnection != null && !sqliteConnection.isClosed())
                sqliteConnection.close();
        } catch (SQLException ignored) {}
    }
}
