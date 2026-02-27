package com.securenet;

import com.formdev.flatlaf.FlatDarkLaf;
import com.securenet.auth.LoginFrame;
import com.securenet.db.DBConfigDialog;
import com.securenet.db.DatabaseManager;

import javax.swing.*;
import java.io.File;

/**
 * Point d'entrée principal de IoT SecureNet Platform v3.
 *
 * Séquence de démarrage :
 *   1. Appliquer le thème FlatLaf Dark
 *   2. Afficher le SplashScreen animé (hexagones, particules, barre de chargement)
 *   3. Pendant le splash : init DB en arrière-plan
 *   4. Fade-out du splash → LoginFrame
 */
public class Main {

    public static void main(String[] args) {

        // ── 1. Thème FlatLaf Dark ──────────────────────────────────────────
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            UIManager.put("Button.arc",                   8);
            UIManager.put("Component.arc",                8);
            UIManager.put("TextComponent.arc",            8);
            UIManager.put("ScrollBar.thumbArc",           999);
            UIManager.put("ScrollBar.width",              8);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("Table.showHorizontalLines",    true);
            UIManager.put("Table.showVerticalLines",      false);
        } catch (Exception e) {
            System.err.println("[UI] FlatLaf non disponible : " + e.getMessage());
        }

        // ── 2. Lancer sur l'EDT ───────────────────────────────────────────
        SwingUtilities.invokeLater(() -> {

            // Créer et afficher le SplashScreen
            SplashScreen splash = new SplashScreen();

            // Callback appelé après le fade-out du splash
            splash.showAndLoad(() -> SwingUtilities.invokeLater(() -> {

                // ── 3. Config DB (première fois seulement) ────────────────
                boolean configExists = new File("securenet.properties").exists();
                if (!configExists) {
                    DBConfigDialog dbDlg = new DBConfigDialog(null);
                    dbDlg.setVisible(true);
                }

                // ── 4. Initialiser la base de données ─────────────────────
                DatabaseManager.getInstance().initialize();

                // ── 5. Afficher le LoginFrame ─────────────────────────────
                LoginFrame loginFrame = new LoginFrame();
                loginFrame.setVisible(true);
            }));
        });
    }
}
