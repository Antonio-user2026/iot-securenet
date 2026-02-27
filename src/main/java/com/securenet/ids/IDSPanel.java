package com.securenet.ids;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.events.EventBus;
import com.securenet.events.SecurityEvent;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.format.DateTimeFormatter;
import java.util.Random;

/**
 * Panel IDS - Système de Détection d'Intrusion.
 * Écoute les événements de sécurité via EventBus.
 */
public class IDSPanel extends JPanel {

    private DefaultTableModel tableModel;
    private JLabel            lblStatus;
    private JLabel            lblTotal;
    private boolean           running = true;
    private int               total   = 0;

    // Règles de détection simulées
    private static final String[] RULES = {
        "SIG-001: Port scan > 100 pkt/s",
        "SIG-002: Brute Force SSH (>5 tentatives)",
        "SIG-003: Trafic MQTT non authentifié",
        "SIG-004: ARP Spoofing détecté",
        "SIG-005: Connexion Telnet refusée",
        "SIG-006: SYN Flood > 500 pkt/s",
        "SIG-007: DNS amplification",
    };

    public IDSPanel() {
        super(new BorderLayout(0, 0));
        setBackground(AppColors.BG_PANEL);
        buildUI();
        subscribeEvents();
    }

    private void buildUI() {
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // ─── Header ────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(new EmptyBorder(0, 0, 16, 0));

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        JLabel title = UIHelper.createTitleLabel("Détection d'intrusion (IDS)");
        lblStatus = UIHelper.createStatusBadge("● IDS ACTIF", AppColors.STATUS_OK);
        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(lblStatus);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton btnClear  = UIHelper.createSecondaryButton("Effacer");
        JButton btnToggle = UIHelper.createDangerButton("⏸ Pause");

        btnClear.addActionListener(e -> { tableModel.setRowCount(0); total = 0; updateTotal(); });
        btnToggle.addActionListener(e -> toggleIDS(btnToggle));

        right.add(btnClear);
        right.add(btnToggle);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ─── Stat cards ────────────────────────────────────────────────────
        JPanel stats = new JPanel(new GridLayout(1, 3, 12, 0));
        stats.setOpaque(false);
        stats.setBorder(new EmptyBorder(0, 0, 16, 0));

        stats.add(buildMiniCard("Règles actives", String.valueOf(RULES.length), AppColors.ACCENT_BLUE));
        lblTotal = null; // On va le gérer autrement
        JPanel totalCard = buildMiniCard("Alertes totales", "0", AppColors.ACCENT_ORANGE);
        // Récupérer le label de valeur de la carte
        stats.add(totalCard);
        stats.add(buildMiniCard("Faux positifs", "0%", AppColors.ACCENT_GREEN));

        // ─── Tableaux ──────────────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(700);
        split.setBackground(AppColors.BG_PANEL);
        split.setBorder(null);

        split.setLeftComponent(buildDetectionTable());
        split.setRightComponent(buildRulesPanel());

        add(header, BorderLayout.NORTH);
        add(stats,  BorderLayout.CENTER); // temporaire, on va organiser

        // Organiser en vertical
        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        content.add(stats, BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);

        add(header,  BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private JPanel buildMiniCard(String title, String value, Color color) {
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 6));
        JLabel lbl  = UIHelper.createSubtitleLabel(title);
        JLabel val  = UIHelper.createValueLabel(value, color);
        if ("Alertes totales".equals(title)) lblTotal = val;
        card.add(lbl, BorderLayout.NORTH);
        card.add(val, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildDetectionTable() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        panel.add(UIHelper.createTitleLabel("Événements détectés"), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(
            new String[]{"Heure", "Sévérité", "Signature", "Source IP", "Action"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(tableModel);
        table.setBackground(AppColors.BG_DARK);
        table.setForeground(AppColors.TEXT_PRIMARY);
        table.setFont(AppFonts.SMALL);
        table.setRowHeight(28);
        table.setGridColor(AppColors.BORDER);
        table.getTableHeader().setBackground(AppColors.BG_PANEL);
        table.getTableHeader().setForeground(AppColors.TEXT_SECONDARY);
        table.getTableHeader().setFont(AppFonts.SMALL);
        table.setSelectionBackground(new Color(0, 150, 255, 40));

        // Colorier par sévérité
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(0, 150, 255, 40) : AppColors.BG_DARK);
                String sev = (String) t.getModel().getValueAt(row, 1);
                if      ("CRITICAL".equals(sev)) setForeground(AppColors.ACCENT_RED);
                else if ("WARNING".equals(sev))  setForeground(AppColors.ACCENT_ORANGE);
                else                              setForeground(AppColors.ACCENT_GREEN);
                setOpaque(true);
                return this;
            }
        });

        panel.add(UIHelper.createScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildRulesPanel() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        panel.add(UIHelper.createTitleLabel("Règles de détection"), BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (String rule : RULES) model.addElement(rule);

        JList<String> ruleList = new JList<>(model);
        ruleList.setBackground(AppColors.BG_DARK);
        ruleList.setForeground(AppColors.ACCENT_GREEN);
        ruleList.setFont(AppFonts.MONO);
        ruleList.setFixedCellHeight(30);

        panel.add(UIHelper.createScrollPane(ruleList), BorderLayout.CENTER);

        JButton btnAdd = UIHelper.createPrimaryButton("+ Nouvelle règle");
        btnAdd.addActionListener(e -> {
            String rule = JOptionPane.showInputDialog(this, "Nom de la règle :");
            if (rule != null && !rule.isEmpty()) model.addElement("CUSTOM: " + rule);
        });
        panel.add(btnAdd, BorderLayout.SOUTH);

        return panel;
    }

    private void subscribeEvents() {
        EventBus.getInstance().subscribe(SecurityEvent.class, event -> {
            if (!running) return;
            SwingUtilities.invokeLater(() -> {
                String time = event.getTimestamp()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                String sig  = RULES[new Random().nextInt(RULES.length)];
                String action = event.getSeverity() == SecurityEvent.Severity.CRITICAL
                    ? "BLOQUÉ" : "ALERTE";

                tableModel.insertRow(0, new Object[]{
                    time,
                    event.getSeverity().name(),
                    sig,
                    event.getSourceIP(),
                    action
                });
                if (tableModel.getRowCount() > 200) {
                    tableModel.removeRow(tableModel.getRowCount() - 1);
                }
                total++;
                updateTotal();
            });
        });
    }

    private void updateTotal() {
        if (lblTotal != null) lblTotal.setText(String.valueOf(total));
    }

    private void toggleIDS(JButton btn) {
        running = !running;
        if (running) {
            lblStatus.setText("● IDS ACTIF");
            lblStatus.setForeground(AppColors.STATUS_OK);
            btn.setText("⏸ Pause");
        } else {
            lblStatus.setText("⏸ IDS EN PAUSE");
            lblStatus.setForeground(AppColors.STATUS_WARNING);
            btn.setText("▶ Reprendre");
        }
    }
}
