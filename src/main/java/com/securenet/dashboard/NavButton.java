package com.securenet.dashboard;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Bouton de navigation sidebar avec icône vectorielle + label.
 */
public class NavButton extends JButton {

    private boolean active  = false;
    private boolean hovered = false;

    public NavButton(ImageIcon icon, String label) {
        super("  " + label);
        setIcon(icon);
        setFont(AppFonts.BODY);
        setForeground(AppColors.TEXT_SECONDARY);
        setHorizontalAlignment(SwingConstants.LEFT);
        setIconTextGap(10);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(new EmptyBorder(10, 16, 10, 16));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
            @Override public void mouseExited(MouseEvent e)  { hovered = false; repaint(); }
        });
    }

    public void setActive(boolean active) {
        this.active = active;
        setForeground(active ? AppColors.TEXT_PRIMARY : AppColors.TEXT_SECONDARY);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (active) {
            // Barre latérale bleue
            g2.setColor(AppColors.ACCENT_BLUE);
            g2.fillRoundRect(0, 6, 3, getHeight() - 12, 3, 3);
            // Fond actif
            g2.setColor(new Color(0, 150, 255, 20));
            g2.fillRoundRect(5, 2, getWidth() - 7, getHeight() - 4, 8, 8);
        } else if (hovered) {
            g2.setColor(new Color(255, 255, 255, 8));
            g2.fillRoundRect(5, 2, getWidth() - 7, getHeight() - 4, 8, 8);
        }
        g2.dispose();
        super.paintComponent(g);
    }
}
