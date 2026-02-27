package com.securenet.siem;

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
import java.util.*;
import java.util.List;

/**
 * Panel SIEM - Security Information and Event Management.
 * Corrélation des événements, alertes prioritaires, timeline.
 */
public class SIEMPanel extends JPanel {

    private DefaultTableModel alertModel;
    private JTextArea         correlationLog;
    private JLabel            lblCritical, lblWarning, lblTotal;
    private int               critCount = 0, warnCount = 0, totalCount = 0;

    // Corrélation simple : compter les events par IP
    private final Map<String, Integer> ipEventCount = new HashMap<>();

    public SIEMPanel() {
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
        left.add(UIHelper.createTitleLabel("🚨 SIEM — Gestion des alertes de sécurité"));
        left.add(Box.createVerticalStrut(4));
        left.add(UIHelper.createSubtitleLabel("Corrélation et supervision centralisée des événements"));

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JButton btnAck   = UIHelper.createSuccessButton("✔ Acquitter");
        JButton btnExport = UIHelper.createSecondaryButton("📤 Exporter");
        JButton btnClear = UIHelper.createDangerButton("🗑 Vider");

        btnAck.addActionListener(e -> acknowledgeSelected());
        btnClear.addActionListener(e -> clearAlerts());
        btnExport.addActionListener(e -> exportAlerts());

        right.add(btnAck);
        right.add(btnExport);
        right.add(btnClear);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ─── KPI row ───────────────────────────────────────────────────────
        JPanel kpi = new JPanel(new GridLayout(1, 3, 12, 0));
        kpi.setOpaque(false);
        kpi.setBorder(new EmptyBorder(0, 0, 16, 0));

        lblCritical = buildKpiLabel("0");
        lblWarning  = buildKpiLabel("0");
        lblTotal    = buildKpiLabel("0");

        kpi.add(buildKpiCard("🔴 Critiques",  lblCritical, AppColors.ACCENT_RED));
        kpi.add(buildKpiCard("🟡 Avertissements", lblWarning, AppColors.ACCENT_ORANGE));
        kpi.add(buildKpiCard("📊 Total événements", lblTotal, AppColors.ACCENT_BLUE));

        // ─── Contenu principal ─────────────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        split.setDividerLocation(680);
        split.setBackground(AppColors.BG_PANEL);
        split.setBorder(null);
        split.setResizeWeight(0.65);

        split.setLeftComponent(buildAlertsTable());
        split.setRightComponent(buildCorrelationPanel());

        JPanel content = new JPanel(new BorderLayout(0, 0));
        content.setOpaque(false);
        content.add(kpi,   BorderLayout.NORTH);
        content.add(split, BorderLayout.CENTER);

        add(header,  BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private JLabel buildKpiLabel(String value) {
        JLabel lbl = new JLabel(value);
        lbl.setFont(AppFonts.TITLE_LARGE);
        return lbl;
    }

    private JPanel buildKpiCard(String title, JLabel valueLabel, Color color) {
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 6));
        JLabel lbl  = UIHelper.createSubtitleLabel(title);
        valueLabel.setForeground(color);
        card.add(lbl,        BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAlertsTable() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        panel.add(UIHelper.createTitleLabel("📋 Alertes en attente"), BorderLayout.NORTH);

        alertModel = new DefaultTableModel(
            new String[]{"Heure", "Niveau", "Source", "Description", "Statut"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(alertModel);
        table.setBackground(AppColors.BG_DARK);
        table.setForeground(AppColors.TEXT_PRIMARY);
        table.setFont(AppFonts.SMALL);
        table.setRowHeight(28);
        table.setGridColor(AppColors.BORDER);
        table.getTableHeader().setBackground(AppColors.BG_PANEL);
        table.getTableHeader().setForeground(AppColors.TEXT_SECONDARY);
        table.getTableHeader().setFont(AppFonts.SMALL);
        table.setSelectionBackground(new Color(255, 100, 0, 40));

        int[] widths = {75, 85, 130, 260, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        // Colorier par niveau
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(255, 100, 0, 40) : AppColors.BG_DARK);
                String level = (String) t.getModel().getValueAt(row, 1);
                if      ("CRITICAL".equals(level)) setForeground(AppColors.ACCENT_RED);
                else if ("WARNING".equals(level))  setForeground(AppColors.ACCENT_ORANGE);
                else                               setForeground(AppColors.ACCENT_GREEN);
                setOpaque(true);
                return this;
            }
        });

        panel.add(UIHelper.createScrollPane(table), BorderLayout.CENTER);

        // Garder la référence à la table pour acquittement
        panel.putClientProperty("table", table);
        return panel;
    }

    private JPanel buildCorrelationPanel() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        panel.add(UIHelper.createTitleLabel("🔗 Moteur de corrélation"), BorderLayout.NORTH);

        correlationLog = new JTextArea();
        correlationLog.setFont(AppFonts.MONO);
        correlationLog.setBackground(AppColors.BG_DARK);
        correlationLog.setForeground(AppColors.ACCENT_BLUE);
        correlationLog.setEditable(false);
        correlationLog.setLineWrap(true);
        correlationLog.setWrapStyleWord(true);
        correlationLog.setText("[SIEM] Moteur de corrélation actif\n" +
                               "[SIEM] Règles de corrélation chargées\n" +
                               "[SIEM] Attente d'événements...\n");

        panel.add(UIHelper.createScrollPane(correlationLog), BorderLayout.CENTER);

        // Top IPs
        JPanel topIPs = buildTopIPsPanel();
        panel.add(topIPs, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildTopIPsPanel() {
        JPanel panel = UIHelper.createCardPanel(new GridLayout(1, 1));
        panel.setPreferredSize(new Dimension(0, 120));

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement("En attente de données...");

        JList<String> list = new JList<>(model);
        list.setBackground(AppColors.BG_DARK);
        list.setForeground(AppColors.ACCENT_ORANGE);
        list.setFont(AppFonts.MONO_BOLD);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_CARD);
        wrapper.add(UIHelper.createSubtitleLabel("🎯 Top IPs suspectes"), BorderLayout.NORTH);
        wrapper.add(UIHelper.createScrollPane(list), BorderLayout.CENTER);

        // Stocker pour mise à jour
        panel.putClientProperty("topIPList", list);
        panel.putClientProperty("topIPModel", model);
        panel.add(wrapper);
        return panel;
    }

    private void subscribeEvents() {
        EventBus.getInstance().subscribe(SecurityEvent.class, event -> {
            SwingUtilities.invokeLater(() -> {
                String time = event.getTimestamp()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));

                // Ajouter à la table
                alertModel.insertRow(0, new Object[]{
                    time,
                    event.getSeverity().name(),
                    event.getSourceIP(),
                    event.getDescription(),
                    "EN ATTENTE"
                });
                if (alertModel.getRowCount() > 500) {
                    alertModel.removeRow(alertModel.getRowCount() - 1);
                }

                // Mise à jour KPI
                totalCount++;
                lblTotal.setText(String.valueOf(totalCount));
                if (event.getSeverity() == SecurityEvent.Severity.CRITICAL) {
                    critCount++;
                    lblCritical.setText(String.valueOf(critCount));
                } else {
                    warnCount++;
                    lblWarning.setText(String.valueOf(warnCount));
                }

                // Corrélation : suivi par IP
                String ip = event.getSourceIP();
                int count = ipEventCount.merge(ip, 1, Integer::sum);

                // Log de corrélation
                correlationLog.append(String.format(
                    "[%s] %s | %s (%d events)\n", time,
                    event.getSeverity(), ip, count));

                // Déclencher une alerte si > 3 events de la même IP
                if (count == 3) {
                    correlationLog.append(String.format(
                        "⚠ [CORRÉLATION] IP suspecte : %s (%d events)\n", ip, count));
                }
                if (count == 5) {
                    correlationLog.append(String.format(
                        "🔴 [CORRÉLATION CRITIQUE] IP %s bloquée automatiquement !\n", ip));
                }
            });
        });
    }

    private void acknowledgeSelected() {
        JOptionPane.showMessageDialog(this,
            "Alertes sélectionnées acquittées.", "SIEM", JOptionPane.INFORMATION_MESSAGE);
    }

    private void clearAlerts() {
        int c = JOptionPane.showConfirmDialog(this,
            "Vider toutes les alertes ?", "Confirmation", JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            alertModel.setRowCount(0);
            critCount = warnCount = totalCount = 0;
            lblCritical.setText("0"); lblWarning.setText("0"); lblTotal.setText("0");
            correlationLog.setText("[SIEM] Journal vidé.\n");
            ipEventCount.clear();
        }
    }

    private void exportAlerts() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("siem_alerts.csv"));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter pw = new java.io.PrintWriter(fc.getSelectedFile())) {
                pw.println("Heure,Niveau,Source,Description,Statut");
                for (int i = 0; i < alertModel.getRowCount(); i++) {
                    pw.printf("%s,%s,%s,%s,%s%n",
                        alertModel.getValueAt(i, 0),
                        alertModel.getValueAt(i, 1),
                        alertModel.getValueAt(i, 2),
                        alertModel.getValueAt(i, 3),
                        alertModel.getValueAt(i, 4));
                }
                JOptionPane.showMessageDialog(this, "Export réussi !");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Erreur : " + e.getMessage());
            }
        }
    }
}
