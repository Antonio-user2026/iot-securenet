package com.securenet.twin;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Fenêtre de contrôle et simulation d'actions réseau sur un appareil.
 *
 * Contenu :
 *  - 14 actions organisées par catégorie (Contrôle / Diagnostic / Panne / Sécurité)
 *  - Indicateur de robustesse (gauge animée)
 *  - Panneau de résultats avec détails
 *  - Journal des actions exécutées
 *  - Rapport d'impact sur la continuité du système
 */
public class DeviceActionDialog extends JDialog {

    private final TopologyNode     node;
    private final Runnable         onTopologyChanged; // callback pour repaint du canvas

    // UI
    private RobustnessGauge        robustnessGauge;
    private JTextArea              taResult;
    private JTextArea              taLog;
    private JLabel                 lblCurrentStatus;
    private JLabel                 lblActionStatus;
    private JProgressBar           actionProgress;
    private JButton                lastClickedBtn;
    private final List<JButton>    actionButtons = new ArrayList<>();

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm:ss");

    // ══════════════════════════════════════════════════════════════════════

    public DeviceActionDialog(Frame parent, TopologyNode node, Runnable onTopologyChanged) {
        super(parent, "Actions réseau — " + node.name, false);
        this.node = node;
        this.onTopologyChanged = onTopologyChanged;
        setSize(800, 660);
        setLocationRelativeTo(parent);
        buildUI();
        refreshStatus();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Construction UI
    // ══════════════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AppColors.BG_PANEL);
        setContentPane(root);
        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(16, 0));
        p.setBackground(AppColors.BG_DARK);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        // Icône + infos
        JLabel icon = new JLabel(node.getIcon(32));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        info.add(UIHelper.createTitleLabel(node.name));
        info.add(Box.createVerticalStrut(3));
        info.add(UIHelper.createSubtitleLabel(
            node.ip + "  ·  " + node.type.name() +
            (node.vendor != null ? "  ·  " + node.vendor : "")));

        // Statut actuel
        lblCurrentStatus = new JLabel();
        updateStatusLabel();
        info.add(Box.createVerticalStrut(5));
        info.add(lblCurrentStatus);

        // Gauge robustesse
        robustnessGauge = new RobustnessGauge();
        robustnessGauge.setPreferredSize(new Dimension(120, 0));
        robustnessGauge.setValue(DeviceActionEngine.computeRobustnessScore(node));

        p.add(icon,             BorderLayout.WEST);
        p.add(info,             BorderLayout.CENTER);
        p.add(robustnessGauge,  BorderLayout.EAST);
        return p;
    }

    // ── Centre : actions + résultats ──────────────────────────────────────

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 0));
        center.setBackground(AppColors.BG_PANEL);
        center.setBorder(new EmptyBorder(14, 14, 8, 14));

        // ── Panneau gauche : boutons d'actions ────────────────────────────
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setBackground(AppColors.BG_PANEL);
        left.setPreferredSize(new Dimension(240, 0));

        addActionCategory(left, "CONTRÔLE RÉSEAU", new DeviceActionEngine.ActionType[]{
            DeviceActionEngine.ActionType.ACTIVATE,
            DeviceActionEngine.ActionType.BLOCK,
            DeviceActionEngine.ActionType.SHUTDOWN,
            DeviceActionEngine.ActionType.RESTART
        });
        addActionCategory(left, "DIAGNOSTIC", new DeviceActionEngine.ActionType[]{
            DeviceActionEngine.ActionType.SCAN_PORTS,
            DeviceActionEngine.ActionType.PING,
            DeviceActionEngine.ActionType.TRACE_ROUTE
        });
        addActionCategory(left, "SIMULATION DE PANNE", new DeviceActionEngine.ActionType[]{
            DeviceActionEngine.ActionType.SIMULATE_CRASH,
            DeviceActionEngine.ActionType.SIMULATE_OVERLOAD,
            DeviceActionEngine.ActionType.SIMULATE_DISCONNECT
        });
        addActionCategory(left, "SÉCURITÉ / ROBUSTESSE", new DeviceActionEngine.ActionType[]{
            DeviceActionEngine.ActionType.ISOLATE,
            DeviceActionEngine.ActionType.PATCH,
            DeviceActionEngine.ActionType.RESET_FIREWALL,
            DeviceActionEngine.ActionType.CHANGE_VLAN
        });

        JScrollPane leftScroll = UIHelper.createScrollPane(left);
        leftScroll.setPreferredSize(new Dimension(240, 0));
        leftScroll.getViewport().setBackground(AppColors.BG_PANEL);

        // ── Panneau droit : résultats + log ───────────────────────────────
        JPanel right = new JPanel(new BorderLayout(0, 10));
        right.setBackground(AppColors.BG_PANEL);

        // Barre de progression action en cours
        JPanel progBar = buildProgressBar();

        // Zone résultats
        JPanel resultCard = UIHelper.createCardPanel(new BorderLayout(0, 6));
        JLabel rTitle = UIHelper.createTitleLabel("Résultats de l'action");
        rTitle.setIcon(AppIcons.ids(14));
        resultCard.add(rTitle, BorderLayout.NORTH);

        taResult = new JTextArea();
        taResult.setFont(AppFonts.MONO);
        taResult.setBackground(AppColors.BG_DARK);
        taResult.setForeground(new Color(170, 220, 170));
        taResult.setEditable(false);
        taResult.setLineWrap(true);
        taResult.setText("Sélectionnez et exécutez une action pour voir les résultats détaillés.");
        resultCard.add(UIHelper.createScrollPane(taResult), BorderLayout.CENTER);

        // Journal
        JPanel logCard = UIHelper.createCardPanel(new BorderLayout(0, 6));
        logCard.setPreferredSize(new Dimension(0, 180));
        JLabel lTitle = UIHelper.createTitleLabel("Journal d'activité");
        lTitle.setIcon(AppIcons.siem(13));
        logCard.add(lTitle, BorderLayout.NORTH);

        taLog = new JTextArea();
        taLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
        taLog.setBackground(new Color(8, 12, 18));
        taLog.setForeground(new Color(80, 180, 100));
        taLog.setEditable(false);
        taLog.setLineWrap(true);
        taLog.setText("[" + now() + "] Actions disponibles chargées.\n");
        logCard.add(UIHelper.createScrollPane(taLog), BorderLayout.CENTER);

        right.add(progBar,    BorderLayout.NORTH);
        right.add(resultCard, BorderLayout.CENTER);
        right.add(logCard,    BorderLayout.SOUTH);

        center.add(leftScroll, BorderLayout.WEST);
        center.add(right,      BorderLayout.CENTER);
        return center;
    }

    private JPanel buildProgressBar() {
        JPanel p = new JPanel(new BorderLayout(8, 0));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(0, 0, 6, 0));

        lblActionStatus = new JLabel("En attente...");
        lblActionStatus.setFont(AppFonts.SMALL);
        lblActionStatus.setForeground(AppColors.TEXT_MUTED);

        actionProgress = new JProgressBar(0, 100);
        actionProgress.setPreferredSize(new Dimension(0, 4));
        actionProgress.setBackground(AppColors.BG_DARK);
        actionProgress.setForeground(AppColors.ACCENT_BLUE);
        actionProgress.setBorderPainted(false);
        actionProgress.setStringPainted(false);
        actionProgress.setValue(0);

        p.add(lblActionStatus,  BorderLayout.NORTH);
        p.add(actionProgress,   BorderLayout.SOUTH);
        return p;
    }

    // ── Footer ─────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setBackground(AppColors.BG_DARK);
        footer.setBorder(new EmptyBorder(10, 20, 12, 20));

        JLabel hint = UIHelper.createSubtitleLabel(
            "Les événements sont transmis vers IDS · SIEM · Firewall en temps réel");
        hint.setIcon(AppIcons.shield(12));

        JButton btnClose = UIHelper.createSecondaryButton("Fermer");
        btnClose.addActionListener(e -> dispose());

        footer.add(hint,     BorderLayout.WEST);
        footer.add(btnClose, BorderLayout.EAST);
        return footer;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Boutons d'actions par catégorie
    // ══════════════════════════════════════════════════════════════════════

    private void addActionCategory(JPanel panel, String category,
                                    DeviceActionEngine.ActionType[] actions) {
        // Titre catégorie
        JLabel cat = new JLabel(category);
        cat.setFont(new Font("Segoe UI", Font.BOLD, 10));
        cat.setForeground(AppColors.TEXT_MUTED);
        cat.setAlignmentX(Component.LEFT_ALIGNMENT);
        cat.setBorder(new EmptyBorder(10, 4, 4, 4));
        panel.add(cat);

        for (DeviceActionEngine.ActionType action : actions) {
            JButton btn = createActionButton(action);
            actionButtons.add(btn);
            panel.add(btn);
            panel.add(Box.createVerticalStrut(4));
        }
    }

    private JButton createActionButton(DeviceActionEngine.ActionType action) {
        Color color = colorFor(action);

        JButton btn = new JButton() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getBackground();

                // Fond semi-transparent coloré
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                    isEnabled() ? 22 : 10));
                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 8, 8));

                // Bordure colorée
                g2.setStroke(new BasicStroke(1f));
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                    isEnabled() ? 70 : 30));
                g2.draw(new RoundRectangle2D.Float(0.5f, 0.5f, getWidth()-1, getHeight()-1, 8, 8));

                // Barre verticale gauche (accent)
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(),
                    isEnabled() ? 200 : 60));
                g2.fillRoundRect(0, 4, 3, getHeight() - 8, 3, 3);

                g2.dispose();
                super.paintComponent(g);
            }
        };

        btn.setText("<html><b style='color:" + toHex(color) + "'>"
            + action.label + "</b>"
            + "<br><font color='#607090' size='1'>" + action.description + "</font></html>");
        btn.setBackground(color);
        btn.setForeground(AppColors.TEXT_PRIMARY);
        btn.setFont(AppFonts.SMALL);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);
        btn.setHorizontalAlignment(JButton.LEFT);
        btn.setBorder(new EmptyBorder(6, 10, 6, 10));

        // Hover
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                if (btn.isEnabled()) btn.repaint();
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) { btn.repaint(); }
        });

        btn.addActionListener(e -> runAction(action, btn));
        return btn;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Exécution d'une action
    // ══════════════════════════════════════════════════════════════════════

    private void runAction(DeviceActionEngine.ActionType action, JButton btn) {
        // Désactiver tous les boutons pendant l'exécution
        setAllButtonsEnabled(false);
        lastClickedBtn = btn;

        // Démarrer l'animation de progression
        lblActionStatus.setText("⏳ " + action.label + " en cours...");
        lblActionStatus.setForeground(AppColors.ACCENT_BLUE);
        actionProgress.setValue(0);
        actionProgress.setForeground(colorFor(action));

        // Animer la barre pendant l'action
        Timer progAnim = new Timer(30, null);
        progAnim.addActionListener(ev -> {
            int v = actionProgress.getValue();
            if (v < 85) actionProgress.setValue(v + 1);
        });
        progAnim.start();

        logAction("[" + now() + "] " + action.label + " → " + node.name + " (" + node.ip + ")");

        // Exécuter l'action via le moteur
        DeviceActionEngine.execute(action, node,
            null,
            result -> {
                progAnim.stop();
                actionProgress.setValue(100);

                // Afficher le résultat
                taResult.setText(
                    "┌─ ACTION : " + result.type.label.toUpperCase() + "\n" +
                    "├─ Appareil  : " + result.node.name + "  (" + result.node.ip + ")\n" +
                    "├─ Durée     : " + result.durationMs + "ms\n" +
                    "├─ Résultat  : " + (result.success ? "✓ SUCCÈS" : "✗ ÉCHEC") + "\n" +
                    "└─ Message   : " + result.message + "\n\n" +
                    "── DÉTAILS ────────────────────────────────────────\n" +
                    result.details
                );
                taResult.setCaretPosition(0);

                // Log
                logAction("[" + now() + "] " + result.message);

                // Mise à jour du statut
                updateStatusLabel();
                int newScore = DeviceActionEngine.computeRobustnessScore(node);
                robustnessGauge.animateTo(newScore);

                lblActionStatus.setText("✓ " + action.label + " terminé");
                lblActionStatus.setForeground(AppColors.ACCENT_GREEN);

                // Notifier le canvas topologique
                if (onTopologyChanged != null) onTopologyChanged.run();

                // Réactiver les boutons
                setAllButtonsEnabled(true);

                // Reset de la barre après 2s
                new Timer(2000, ev -> {
                    ((Timer) ev.getSource()).stop();
                    actionProgress.setValue(0);
                    lblActionStatus.setText("En attente...");
                    lblActionStatus.setForeground(AppColors.TEXT_MUTED);
                }).start();
            }
        );
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers
    // ══════════════════════════════════════════════════════════════════════

    private void updateStatusLabel() {
        Color sc = switch (node.status) {
            case ONLINE     -> AppColors.STATUS_OK;
            case OFFLINE    -> AppColors.TEXT_MUTED;
            case SUSPICIOUS -> AppColors.STATUS_WARNING;
            case BLOCKED    -> AppColors.STATUS_CRITICAL;
            case SCANNING   -> AppColors.ACCENT_BLUE;
        };
        lblCurrentStatus.setForeground(sc);
        lblCurrentStatus.setText(node.status.symbol + "  " + node.status.label);
        lblCurrentStatus.setFont(AppFonts.SMALL);
    }

    private void refreshStatus() {
        updateStatusLabel();
    }

    private void setAllButtonsEnabled(boolean enabled) {
        for (JButton btn : actionButtons) {
            btn.setEnabled(enabled);
        }
    }

    private void logAction(String msg) {
        SwingUtilities.invokeLater(() -> {
            taLog.append(msg + "\n");
            taLog.setCaretPosition(taLog.getDocument().getLength());
        });
    }

    private String now() {
        return LocalTime.now().format(TIME_FMT);
    }

    private Color colorFor(DeviceActionEngine.ActionType action) {
        return switch (action) {
            case ACTIVATE                 -> AppColors.ACCENT_GREEN;
            case BLOCK, ISOLATE           -> AppColors.ACCENT_RED;
            case SHUTDOWN, SIMULATE_CRASH,
                 SIMULATE_DISCONNECT      -> AppColors.ACCENT_ORANGE;
            case RESTART, SCAN_PORTS,
                 TRACE_ROUTE, PING        -> AppColors.ACCENT_BLUE;
            case SIMULATE_OVERLOAD        -> AppColors.ACCENT_ORANGE;
            case PATCH, RESET_FIREWALL,
                 CHANGE_VLAN              -> AppColors.ACCENT_PURPLE;
        };
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Gauge de robustesse (composant graphique)
    // ══════════════════════════════════════════════════════════════════════

    static class RobustnessGauge extends JPanel {

        private int   value   = 100;
        private int   display = 100;
        private Timer anim;

        RobustnessGauge() {
            setOpaque(false);
        }

        void setValue(int v) {
            this.value = v;
            this.display = v;
            repaint();
        }

        void animateTo(int target) {
            if (anim != null) anim.stop();
            int[] step = {0};
            int start = display, steps = 20;
            anim = new Timer(16, e -> {
                step[0]++;
                display = start + (target - start) * step[0] / steps;
                repaint();
                if (step[0] >= steps) { display = target; ((Timer)e.getSource()).stop(); }
            });
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            float cx = w / 2f, cy = h / 2f + 5;
            float r = Math.min(w * 0.42f, h * 0.36f);

            // Titre
            g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
            g2.setColor(AppColors.TEXT_MUTED);
            FontMetrics fm = g2.getFontMetrics();
            String title = "ROBUSTESSE";
            g2.drawString(title, cx - fm.stringWidth(title) / 2f, 16);

            // Arc background
            g2.setStroke(new BasicStroke(7f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(40, 50, 70));
            g2.drawArc((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2),
                215, -250);

            // Arc valeur
            Color gaugeColor = display > 70 ? AppColors.ACCENT_GREEN
                : display > 40 ? AppColors.ACCENT_ORANGE : AppColors.ACCENT_RED;
            int sweep = -(int)(250 * display / 100.0);
            if (sweep != 0) {
                g2.setColor(gaugeColor);
                g2.drawArc((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2),
                    215, sweep);
            }

            // Valeur centrale
            g2.setFont(new Font("Segoe UI", Font.BOLD, 20));
            g2.setColor(gaugeColor);
            String val = display + "%";
            FontMetrics fmV = g2.getFontMetrics();
            g2.drawString(val, cx - fmV.stringWidth(val) / 2f, cy + 7);

            // Sous-texte
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            g2.setColor(AppColors.TEXT_MUTED);
            String label = display > 70 ? "Robuste" : display > 40 ? "Dégradé" : "Critique";
            FontMetrics fmL = g2.getFontMetrics();
            g2.drawString(label, cx - fmL.stringWidth(label) / 2f, cy + 20);

            g2.dispose();
        }
    }
}
