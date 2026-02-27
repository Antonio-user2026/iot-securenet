package com.securenet.config;

import java.io.*;
import java.util.Properties;

/**
 * Configuration de l'application.
 * Lit / écrit un fichier securenet.properties dans le répertoire courant.
 *
 * Clés supportées :
 *   db.mode          = sqlite | mysql
 *   db.host          = localhost
 *   db.port          = 3306
 *   db.name          = securenet
 *   db.user          = root
 *   db.password      = (vide par défaut)
 *   app.first_login  = true  (force dialog changement mdp admin)
 */
public class AppConfig {

    private static final AppConfig INSTANCE = new AppConfig();
    private static final String CONFIG_FILE = "securenet.properties";

    private final Properties props = new Properties();

    private AppConfig() { load(); }
    public static AppConfig getInstance() { return INSTANCE; }

    // ─── Lecture ───────────────────────────────────────────────────────────
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public boolean getBoolean(String key, boolean def) {
        return Boolean.parseBoolean(props.getProperty(key, String.valueOf(def)));
    }

    public int getInt(String key, int def) {
        try { return Integer.parseInt(props.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    // ─── Écriture ──────────────────────────────────────────────────────────
    public void set(String key, String value) {
        props.setProperty(key, value);
        save();
    }

    public void set(String key, boolean value) { set(key, String.valueOf(value)); }

    // ─── Helpers DB ────────────────────────────────────────────────────────
    public String getDbMode()     { return get("db.mode",     "sqlite"); }
    public String getDbHost()     { return get("db.host",     "localhost"); }
    public int    getDbPort()     { return getInt("db.port",  3306); }
    public String getDbName()     { return get("db.name",     "securenet"); }
    public String getDbUser()     { return get("db.user",     "root"); }
    public String getDbPassword() { return get("db.password", ""); }

    public boolean isFirstLogin() { return getBoolean("app.first_login", true); }
    public void markFirstLoginDone() { set("app.first_login", false); }

    // ─── I/O ───────────────────────────────────────────────────────────────
    private void load() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) {
            try (InputStream is = new FileInputStream(file)) {
                props.load(is);
            } catch (IOException e) {
                System.err.println("[Config] Erreur lecture : " + e.getMessage());
            }
        } else {
            // Valeurs par défaut
            props.setProperty("db.mode",        "sqlite");
            props.setProperty("db.host",        "localhost");
            props.setProperty("db.port",        "3306");
            props.setProperty("db.name",        "securenet");
            props.setProperty("db.user",        "root");
            props.setProperty("db.password",    "");
            props.setProperty("app.first_login","true");
            save();
        }
    }

    public void save() {
        try (OutputStream os = new FileOutputStream(CONFIG_FILE)) {
            props.store(os, "IoT SecureNet Configuration");
        } catch (IOException e) {
            System.err.println("[Config] Erreur écriture : " + e.getMessage());
        }
    }
}
