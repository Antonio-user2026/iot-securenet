package com.securenet.dashboard;

import com.securenet.auth.User;
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
 * Panel principal du Dashboard.
 * Affiche les KPI réseau, les dernières alertes et un graphique d'activité.
 */
public class DashboardPanel extends JPanel {

    private final User currentUser;

    // Stat cards
    private StatCard cardDevices;
    private StatCard cardAlerts;
    private StatCard cardBlocked;
    private StatCard cardScore;

    // Tableau des dernières alertes
    private DefaultTableModel alertTableModel;
    private JTable            alertTable;

    // Compteurs simulés
    private int deviceCount  = 12;
    private int alertCount   = 0;
    private int blockedCount = 0;

    // Générateur de simulation
    private final Random rnd = new Random();
    private final String[] ATTACK_TYPES = {
        "Port Scan", "Brute Force SSH", "DDoS SYN", "ARP Spoofing",
        "MQTT Injection", "Telnet Intrusion", "DNS Spoofing"
    };
    private final String[] IPS = {
        "192.168.1.101", "10.0.0.55", "172.16.0.22", "192.168.0.254",
        "10.0.1.15",  "192.168.2.30"
    };

    public DashboardPanel(User user) {
        super(new BorderLayout(0, 0));
        this.currentUser = user;
        setBackground(AppColors.BG_PANEL);
        buildUI();
        startSimulation();
        subscribeToEvents();
    }

    private void buildUI() {
        setBorder(new EmptyBorder(20, 20, 20, 20));
        JPanel inner = new JPanel(new BorderLayout(0, 20));
        inner.setOpaque(false);

        inner.add(buildHeader(),    BorderLayout.NORTH);
        inner.add(buildKPIRow(),    BorderLayout.NORTH);
        inner.add(buildMainArea(),  BorderLayout.CENTER);

        // On empile header + KPI avec un BoxLayout vertical
        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setOpaque(false);
        top.add(buildHeader());
        top.add(Box.createVerticalStrut(16));
        top.add(buildKPIRow());

        add(top,             BorderLayout.NORTH);
        add(buildMainArea(), BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JLabel lblWelcome = new JLabel("Bonjour, " + currentUser.getUsername() + " 👋");
        lblWelcome.setFont(AppFonts.TITLE_LARGE);
        lblWelcome.setForeground(AppColors.TEXT_PRIMARY);

        JLabel lblSub = UIHelper.createSubtitleLabel("Supervision réseau IoT en temps réel");

        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(lblWelcome);
        left.add(Box.createVerticalStrut(2));
        left.add(lblSub);

        JButton btnScan = UIHelper.createPrimaryButton("🔍  Nouveau scan");
        btnScan.addActionListener(e -> runQuickScan());

        panel.add(left,    BorderLayout.WEST);
        panel.add(btnScan, BorderLayout.EAST);
        return panel;
    }

    private JPanel buildKPIRow() {
        JPanel row = new JPanel(new GridLayout(1, 4, 12, 0));
        row.setOpaque(false);

        cardDevices = new StatCard("📡", "Appareils connectés",
                String.valueOf(deviceCount),   "↑ +2 cette semaine", AppColors.ACCENT_BLUE);
        cardAlerts  = new StatCard("🚨", "Alertes actives",
                "0",                            "Aucune alerte",      AppColors.ACCENT_ORANGE);
        cardBlocked = new StatCard("🔥", "IPs bloquées",
                "0",                            "Règles actives",     AppColors.ACCENT_RED);
        cardScore   = new StatCard("🛡", "Score sécurité",
                "87%",                          "Bon niveau",         AppColors.ACCENT_GREEN);

        row.add(cardDevices);
        row.add(cardAlerts);
        row.add(cardBlocked);
        row.add(cardScore);
        return row;
    }

    private JPanel buildMainArea() {
        JPanel area = new JPanel(new GridLayout(1, 2, 16, 0));
        area.setOpaque(false);

        area.add(buildAlertTable());
        area.add(buildActivityLog());
        return area;
    }

    private JPanel buildAlertTable() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));

        JLabel title = UIHelper.createTitleLabel("🚨 Alertes récentes");
        panel.add(title, BorderLayout.NORTH);

        String[] columns = { "Horodatage", "Sévérité", "Type", "Source IP" };
        alertTableModel = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        alertTable = new JTable(alertTableModel);
        styleTable(alertTable);

        panel.add(UIHelper.createScrollPane(alertTable), BorderLayout.CENTER);

        // Bouton vider
        JButton btnClear = UIHelper.createSecondaryButton("Effacer");
        btnClear.addActionListener(e -> alertTableModel.setRowCount(0));
        panel.add(btnClear, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildActivityLog() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        JLabel title = UIHelper.createTitleLabel("📋 Journal d'activité");
        panel.add(title, BorderLayout.NORTH);

        JTextArea logArea = new JTextArea();
        logArea.setFont(AppFonts.MONO);
        logArea.setBackground(AppColors.BG_DARK);
        logArea.setForeground(AppColors.ACCENT_GREEN);
        logArea.setEditable(false);
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        // Pré-remplir avec des logs système
        logArea.setText(
            "[OK]  Système démarré\n" +
            "[OK]  Base de données initialisée\n" +
            "[OK]  Module IDS actif\n" +
            "[OK]  Firewall chargé (0 règles)\n" +
            "[OK]  SIEM opérationnel\n" +
            "[>>] En attente d'événements réseau...\n"
        );

        JScrollPane sp = UIHelper.createScrollPane(logArea);
        panel.add(sp, BorderLayout.CENTER);

        // Exposer logArea pour la mise à jour par simulation
        putClientProperty("logArea", logArea);

        return panel;
    }

    // ─── Style table ───────────────────────────────────────────────────────

    private void styleTable(JTable table) {
        table.setBackground(AppColors.BG_DARK);
        table.setForeground(AppColors.TEXT_PRIMARY);
        table.setFont(AppFonts.SMALL);
        table.setRowHeight(28);
        table.setGridColor(AppColors.BORDER);
        table.setShowGrid(true);
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
                if ("CRITICAL".equals(sev))      setForeground(AppColors.ACCENT_RED);
                else if ("WARNING".equals(sev))   setForeground(AppColors.ACCENT_ORANGE);
                else                               setForeground(AppColors.ACCENT_GREEN);
                setOpaque(true);
                return this;
            }
        });
    }

    // ─── Simulation réseau (démo) ──────────────────────────────────────────

    private void startSimulation() {
        // Génère des événements aléatoires toutes les 3-8 secondes
        Timer simTimer = new Timer(0, null);
        simTimer.addActionListener(e -> {
            simTimer.setDelay(3000 + rnd.nextInt(5000));
            generateFakeEvent();
        });
        simTimer.start();
    }

    private void generateFakeEvent() {
        String ip   = IPS[rnd.nextInt(IPS.length)];
        String type = ATTACK_TYPES[rnd.nextInt(ATTACK_TYPES.length)];
        SecurityEvent.Severity sev = rnd.nextInt(10) > 6
            ? SecurityEvent.Severity.CRITICAL : SecurityEvent.Severity.WARNING;

        SecurityEvent event = new SecurityEvent(
            SecurityEvent.Type.IDS_ALERT, sev, ip, type
        );
        EventBus.getInstance().publish(event);
    }

    private void subscribeToEvents() {
        EventBus.getInstance().subscribe(SecurityEvent.class, event -> {
            SwingUtilities.invokeLater(() -> {
                // Mettre à jour le tableau
                String time = event.getTimestamp()
                    .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                alertTableModel.insertRow(0, new Object[] {
                    time,
                    event.getSeverity().name(),
                    event.getDescription(),
                    event.getSourceIP()
                });
                // Limiter à 100 lignes
                if (alertTableModel.getRowCount() > 100) {
                    alertTableModel.removeRow(alertTableModel.getRowCount() - 1);
                }

                // Mettre à jour les KPI
                alertCount++;
                if (event.getSeverity() == SecurityEvent.Severity.CRITICAL) {
                    blockedCount++;
                    cardBlocked.updateValue(String.valueOf(blockedCount));
                }
                cardAlerts.updateValue(String.valueOf(alertCount));
                cardAlerts.updateTrend("↑ " + alertCount + " alertes");
            });
        });
    }

    private void runQuickScan() {
        JOptionPane.showMessageDialog(this,
            "Scan réseau lancé...\n" +
            "Résultat : " + deviceCount + " appareils détectés.\n\n" +
            "→ Allez dans 'Appareils IoT' pour les détails.",
            "Scan réseau", JOptionPane.INFORMATION_MESSAGE);
    }
}
