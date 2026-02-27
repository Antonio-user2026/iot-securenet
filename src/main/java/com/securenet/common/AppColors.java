package com.securenet.common;

import java.awt.*;

/**
 * Palette de couleurs centralisée de l'application.
 * Thème : Dark Cybersecurity
 */
public class AppColors {

    // Backgrounds
    public static final Color BG_DARK       = new Color(18, 22, 30);
    public static final Color BG_PANEL      = new Color(25, 31, 42);
    public static final Color BG_CARD       = new Color(32, 40, 55);
    public static final Color BG_SIDEBAR    = new Color(15, 19, 26);

    // Accents
    public static final Color ACCENT_BLUE   = new Color(0, 150, 255);
    public static final Color ACCENT_GREEN  = new Color(0, 220, 130);
    public static final Color ACCENT_ORANGE = new Color(255, 160, 0);
    public static final Color ACCENT_RED    = new Color(255, 70, 70);
    public static final Color ACCENT_PURPLE = new Color(150, 100, 255);

    // Text
    public static final Color TEXT_PRIMARY   = new Color(220, 230, 245);
    public static final Color TEXT_SECONDARY = new Color(130, 150, 180);
    public static final Color TEXT_MUTED     = new Color(80, 100, 130);

    // Status
    public static final Color STATUS_OK       = ACCENT_GREEN;
    public static final Color STATUS_WARNING  = ACCENT_ORANGE;
    public static final Color STATUS_CRITICAL = ACCENT_RED;
    public static final Color STATUS_INFO     = ACCENT_BLUE;

    // Borders
    public static final Color BORDER = new Color(45, 55, 75);

    private AppColors() {}
}
