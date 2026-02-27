package com.securenet.dashboard;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Carte de statistique pour le Dashboard.
 * Affiche : icon, valeur, libellé, tendance.
 */
public class StatCard extends JPanel {

    private final JLabel lblValue;
    private final JLabel lblTrend;

    public StatCard(String icon, String title, String value, String trend, Color accentColor) {
        super(new BorderLayout(0, 6));
        setBackground(AppColors.BG_CARD);
        setBorder(UIHelper.createCardBorder());

        // Icône + titre
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setOpaque(false);

        JLabel lblIcon = new JLabel(icon);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 20));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(AppFonts.SMALL);
        lblTitle.setForeground(AppColors.TEXT_SECONDARY);

        header.add(lblIcon);
        header.add(lblTitle);

        // Valeur principale
        lblValue = UIHelper.createValueLabel(value, accentColor);

        // Tendance
        lblTrend = new JLabel(trend);
        lblTrend.setFont(AppFonts.SMALL);
        lblTrend.setForeground(AppColors.TEXT_MUTED);

        add(header,   BorderLayout.NORTH);
        add(lblValue, BorderLayout.CENTER);
        add(lblTrend, BorderLayout.SOUTH);
    }

    public void updateValue(String value) {
        SwingUtilities.invokeLater(() -> lblValue.setText(value));
    }

    public void updateTrend(String trend) {
        SwingUtilities.invokeLater(() -> lblTrend.setText(trend));
    }
}
