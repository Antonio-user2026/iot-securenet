package com.securenet.common;

import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Factory de composants Swing stylisés réutilisables.
 */
public class UIHelper {

    private UIHelper() {}

    // ─── Panels ────────────────────────────────────────────────────────────

    public static JPanel createCardPanel() {
        JPanel p = new JPanel();
        p.setBackground(AppColors.BG_CARD);
        p.setBorder(createCardBorder());
        return p;
    }

    public static JPanel createCardPanel(LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(AppColors.BG_CARD);
        p.setBorder(createCardBorder());
        return p;
    }

    public static Border createCardBorder() {
        return new CompoundBorder(
            new LineBorder(AppColors.BORDER, 1, true),
            new EmptyBorder(12, 16, 12, 16));
    }

    // ─── Labels ────────────────────────────────────────────────────────────

    public static JLabel createTitleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.TITLE_MEDIUM);
        l.setForeground(AppColors.TEXT_PRIMARY);
        return l;
    }

    public static JLabel createSubtitleLabel(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }

    public static JLabel createValueLabel(String text, Color color) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.TITLE_LARGE);
        l.setForeground(color);
        return l;
    }

    // ─── Buttons ───────────────────────────────────────────────────────────

    public static JButton createPrimaryButton(String text) {
        return styledButton(text, null, AppColors.ACCENT_BLUE, Color.WHITE);
    }

    public static JButton createPrimaryButton(String text, ImageIcon icon) {
        return styledButton(text, icon, AppColors.ACCENT_BLUE, Color.WHITE);
    }

    public static JButton createDangerButton(String text) {
        return styledButton(text, null, AppColors.ACCENT_RED, Color.WHITE);
    }

    public static JButton createDangerButton(String text, ImageIcon icon) {
        return styledButton(text, icon, AppColors.ACCENT_RED, Color.WHITE);
    }

    public static JButton createSuccessButton(String text) {
        return styledButton(text, null, AppColors.ACCENT_GREEN, AppColors.BG_DARK);
    }

    public static JButton createSecondaryButton(String text) {
        return styledButton(text, null, AppColors.BG_CARD, AppColors.TEXT_PRIMARY);
    }

    public static JButton createSecondaryButton(String text, ImageIcon icon) {
        return styledButton(text, icon, AppColors.BG_CARD, AppColors.TEXT_PRIMARY);
    }

    private static JButton styledButton(String text, ImageIcon icon,
                                         Color bg, Color fg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getBackground());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        if (icon != null) { btn.setIcon(icon); btn.setIconTextGap(6); }
        btn.setFont(AppFonts.BODY_BOLD);
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int h = btn.getPreferredSize().height;
        btn.setPreferredSize(new Dimension(btn.getPreferredSize().width + 8, Math.max(36, h)));

        Color hoverBg = bg.brighter();
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(hoverBg); }
            @Override public void mouseExited(MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    // ─── Text fields ───────────────────────────────────────────────────────

    public static JTextField createTextField(String placeholder) {
        JTextField tf = new JTextField();
        tf.setFont(AppFonts.BODY);
        tf.setBackground(AppColors.BG_DARK);
        tf.setForeground(AppColors.TEXT_PRIMARY);
        tf.setCaretColor(AppColors.TEXT_PRIMARY);
        tf.setBorder(new CompoundBorder(
            new LineBorder(AppColors.BORDER, 1, true),
            new EmptyBorder(6, 10, 6, 10)));
        tf.putClientProperty("JTextField.placeholderText", placeholder);
        return tf;
    }

    public static JPasswordField createPasswordField(String placeholder) {
        JPasswordField pf = new JPasswordField();
        pf.setFont(AppFonts.BODY);
        pf.setBackground(AppColors.BG_DARK);
        pf.setForeground(AppColors.TEXT_PRIMARY);
        pf.setCaretColor(AppColors.TEXT_PRIMARY);
        pf.setBorder(new CompoundBorder(
            new LineBorder(AppColors.BORDER, 1, true),
            new EmptyBorder(6, 10, 6, 10)));
        pf.putClientProperty("JTextField.placeholderText", placeholder);
        return pf;
    }

    // ─── Status badge ──────────────────────────────────────────────────────

    public static JLabel createStatusBadge(String text, Color color) {
        JLabel lbl = new JLabel(" " + text + " ") {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(color.getRed(), color.getGreen(),
                                      color.getBlue(), 35));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 999, 999);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setFont(AppFonts.SMALL);
        lbl.setForeground(color);
        lbl.setOpaque(false);
        lbl.setBorder(new EmptyBorder(2, 8, 2, 8));
        return lbl;
    }

    // ─── Separator ─────────────────────────────────────────────────────────

    public static JSeparator createSeparator() {
        JSeparator s = new JSeparator();
        s.setForeground(AppColors.BORDER);
        s.setBackground(AppColors.BORDER);
        return s;
    }

    // ─── ScrollPane ────────────────────────────────────────────────────────

    public static JScrollPane createScrollPane(Component view) {
        JScrollPane sp = new JScrollPane(view);
        sp.setBackground(AppColors.BG_PANEL);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(view.getBackground());
        return sp;
    }
}
