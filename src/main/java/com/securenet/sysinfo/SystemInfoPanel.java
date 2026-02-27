package com.securenet.sysinfo;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.devices.Device;
import com.securenet.icons.AppIcons;
import com.securenet.sysinfo.SystemInfoCollector.SystemInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/**
 * Fenêtre d'informations système d'un appareil (CPU, RAM, ROM, réseau...).
 * Affichée depuis le Digital Twin ou le Device Manager.
 */
public class SystemInfoPanel extends JDialog {

    private final Device   device;
    private final boolean  isLocalHost;

    // Composants live
    private JLabel lblCpuUsage, lblRamUsage, lblDiskUsage;
    private JLabel lblCpuTemp;
    private GaugePanel gaugeCpu, gaugeRam, gaugeDisk;
    private JTextArea taDetails;
    private JLabel lblStatus;
    private Timer  refreshTimer;

    public SystemInfoPanel(Frame parent, Device device, boolean isLocalHost) {
        super(parent, "Informations système — " + device.getName(), false);
        this.device      = device;
        this.isLocalHost = isLocalHost;
        setSize(680, 640);
        setLocationRelativeTo(parent);
        buildUI();
        loadInfo();

        // Auto-refresh toutes les 3 secondes pour la machine locale
        if (isLocalHost) {
            refreshTimer = new Timer(3000, e -> loadInfo());
            refreshTimer.start();
        }
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                if (refreshTimer != null) refreshTimer.stop();
            }
        });
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AppColors.BG_PANEL);
        setContentPane(root);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildContent(), BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(14, 0));
        p.setBackground(AppColors.BG_DARK);
        p.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel icon = new JLabel(device.getIcon(36));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);

        JLabel lName = new JLabel(device.getName() == null ? "Appareil" : device.getName());
        lName.setFont(AppFonts.TITLE_MEDIUM);
        lName.setForeground(AppColors.TEXT_PRIMARY);

        JLabel lIP = UIHelper.createSubtitleLabel(
            device.getIpAddress() + "  ·  " + device.getType().name() +
            (device.getVendor() != null && !device.getVendor().isEmpty()
                ? "  ·  " + device.getVendor() : ""));

        lblStatus = UIHelper.createStatusBadge(
            isLocalHost ? "Machine locale" : "Appareil distant (simulé)",
            isLocalHost ? AppColors.ACCENT_GREEN : AppColors.ACCENT_BLUE);

        info.add(lName);
        info.add(Box.createVerticalStrut(3));
        info.add(lIP);
        info.add(Box.createVerticalStrut(4));
        info.add(lblStatus);

        JButton btnRefresh = UIHelper.createSecondaryButton("", AppIcons.refresh(14));
        btnRefresh.setToolTipText("Actualiser");
        btnRefresh.setPreferredSize(new Dimension(36, 36));
        btnRefresh.addActionListener(e -> loadInfo());

        p.add(icon,       BorderLayout.WEST);
        p.add(info,       BorderLayout.CENTER);
        p.add(btnRefresh, BorderLayout.EAST);
        return p;
    }

    // ── Contenu principal ──────────────────────────────────────────────────

    private JPanel buildContent() {
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBackground(AppColors.BG_PANEL);
        content.setBorder(new EmptyBorder(16, 16, 8, 16));

        // Ligne des gauges (CPU, RAM, Disque)
        JPanel gaugeRow = new JPanel(new GridLayout(1, 3, 12, 0));
        gaugeRow.setOpaque(false);

        gaugeCpu  = new GaugePanel("CPU",     AppColors.ACCENT_BLUE);
        gaugeRam  = new GaugePanel("RAM",     AppColors.ACCENT_GREEN);
        gaugeDisk = new GaugePanel("Stockage", AppColors.ACCENT_PURPLE);

        gaugeRow.add(gaugeCpu);
        gaugeRow.add(gaugeRam);
        gaugeRow.add(gaugeDisk);

        // Détails textuels
        taDetails = new JTextArea();
        taDetails.setFont(AppFonts.MONO);
        taDetails.setBackground(AppColors.BG_DARK);
        taDetails.setForeground(new Color(180, 220, 180));
        taDetails.setEditable(false);
        taDetails.setLineWrap(false);
        taDetails.setText("Chargement des informations...");

        JPanel detailCard = UIHelper.createCardPanel(new BorderLayout(0, 8));
        detailCard.add(UIHelper.createTitleLabel("Détails du système"), BorderLayout.NORTH);
        detailCard.add(UIHelper.createScrollPane(taDetails), BorderLayout.CENTER);

        content.add(gaugeRow,  BorderLayout.NORTH);
        content.add(detailCard, BorderLayout.CENTER);
        return content;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        footer.setBackground(AppColors.BG_DARK);
        JButton btnClose = UIHelper.createSecondaryButton("Fermer");
        btnClose.addActionListener(e -> {
            if (refreshTimer != null) refreshTimer.stop();
            dispose();
        });
        footer.add(btnClose);
        return footer;
    }

    // ── Chargement des données ─────────────────────────────────────────────

    private void loadInfo() {
        SwingWorker<SystemInfo, Void> worker = new SwingWorker<>() {
            @Override protected SystemInfo doInBackground() {
                if (isLocalHost) {
                    return SystemInfoCollector.collectLocal();
                } else {
                    return SystemInfoCollector.simulateRemote(
                        device.getIpAddress(),
                        device.getName(),
                        device.getType(),
                        device.getVendor() == null ? "" : device.getVendor());
                }
            }

            @Override protected void done() {
                try {
                    SystemInfo info = get();
                    updateUI(info);
                } catch (Exception e) {
                    taDetails.setText("Erreur de collecte : " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void updateUI(SystemInfo info) {
        // Gauges
        gaugeCpu.setValue((int)  info.cpuUsagePercent,
            String.format("%.1f%%", info.cpuUsagePercent),
            info.cpuCores + " core(s)  " + info.cpuFrequency);
        gaugeRam.setValue((int)  info.ramUsagePercent,
            String.format("%.1f%%", info.ramUsagePercent),
            info.ramUsedMB + " / " + info.ramTotalMB + " MB");
        if (info.diskTotalGB > 0) {
            gaugeDisk.setValue((int) info.diskUsagePercent,
                String.format("%.1f%%", info.diskUsagePercent),
                info.diskUsedGB + " / " + info.diskTotalGB + " GB");
        } else {
            gaugeDisk.setValue(0, "N/A", "Flash intégrée");
        }

        // Panneau texte détaillé
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════╗\n");
        sb.append("║  INFORMATIONS SYSTÈME — ").append(padRight(info.hostname, 34)).append("║\n");
        sb.append("╠══════════════════════════════════════════════════════════╣\n");

        sb.append("║  IDENTIFICATION\n");
        sb.append("║    Hôte       : ").append(info.hostname).append("\n");
        sb.append("║    IP         : ").append(info.ipAddress).append("\n");
        sb.append("║    OS         : ").append(info.osName).append("\n");
        sb.append("║    Kernel     : ").append(info.kernelVersion).append("\n");
        sb.append("║    Arch.      : ").append(info.architecture).append("\n");
        sb.append("║    Uptime     : ").append(info.uptime).append("\n");
        sb.append("║\n");

        sb.append("║  PROCESSEUR\n");
        sb.append("║    Modèle     : ").append(info.cpuModel).append("\n");
        sb.append("║    Cœurs      : ").append(info.cpuCores).append("\n");
        sb.append("║    Fréquence  : ").append(info.cpuFrequency).append("\n");
        sb.append("║    Utilisation: ").append(String.format("%.1f%%", info.cpuUsagePercent)).append("\n");
        if (info.cpuTempCelsius > 0) {
            String tempColor = info.cpuTempCelsius > 80 ? "⚠ " : info.cpuTempCelsius > 60 ? "" : "";
            sb.append("║    Température: ").append(tempColor)
              .append(String.format("%.1f°C", info.cpuTempCelsius)).append("\n");
        }
        sb.append("║\n");

        sb.append("║  MÉMOIRE RAM\n");
        sb.append("║    Total      : ").append(info.ramTotalMB).append(" MB\n");
        sb.append("║    Utilisée   : ").append(info.ramUsedMB).append(" MB")
          .append(String.format(" (%.1f%%)", info.ramUsagePercent)).append("\n");
        sb.append("║    Libre      : ").append(info.ramFreeMB).append(" MB\n");
        sb.append("║\n");

        if (info.diskTotalGB > 0) {
            sb.append("║  STOCKAGE\n");
            sb.append("║    Capacité   : ").append(info.diskTotalGB).append(" GB\n");
            sb.append("║    Utilisé    : ").append(info.diskUsedGB).append(" GB")
              .append(String.format(" (%.1f%%)", info.diskUsagePercent)).append("\n");
            sb.append("║    Libre      : ").append(info.diskFreeGB).append(" GB\n");
            sb.append("║\n");
        }

        sb.append("║  RÉSEAU\n");
        sb.append("║    MAC        : ").append(info.macAddress).append("\n");
        sb.append("║    Rx total   : ").append(formatBytes(info.netRxKBps)).append("\n");
        sb.append("║    Tx total   : ").append(formatBytes(info.netTxKBps)).append("\n");
        if (!info.openPortsList.equals("—")) {
            sb.append("║    Ports      : ").append(info.openPortsList).append("\n");
        }
        sb.append("║\n");

        if (info.processCount > 0) {
            sb.append("║  PROCESSUS    : ").append(info.processCount).append(" actifs\n");
        }

        if (info.batteryPercent >= 0) {
            String batIcon = info.batteryPercent > 50 ? "🔋" : info.batteryPercent > 20 ? "🪫" : "⚠";
            sb.append("║\n");
            sb.append("║  BATTERIE     : ").append(batIcon).append(" ")
              .append(info.batteryPercent).append("%\n");
        }

        sb.append("╚══════════════════════════════════════════════════════════╝");

        taDetails.setText(sb.toString());
        taDetails.setCaretPosition(0);
    }

    private String padRight(String s, int n) {
        if (s == null) s = "";
        return s.length() >= n ? s.substring(0, n) : s + " ".repeat(n - s.length());
    }

    private String formatBytes(long kb) {
        if (kb > 1024 * 1024) return String.format("%.1f GB", kb / (1024.0 * 1024));
        if (kb > 1024)        return String.format("%.1f MB", kb / 1024.0);
        return kb + " KB";
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Composant Gauge (arc circulaire animé)
    // ══════════════════════════════════════════════════════════════════════

    static class GaugePanel extends JPanel {

        private final String title;
        private final Color  accentColor;
        private int    value   = 0;      // 0-100
        private int    displayValue = 0; // valeur animée
        private String mainLabel = "—";
        private String subLabel  = "";
        private Timer  anim;

        GaugePanel(String title, Color accentColor) {
            this.title       = title;
            this.accentColor = accentColor;
            setBackground(AppColors.BG_CARD);
            setBorder(new EmptyBorder(12, 12, 12, 12));
            setPreferredSize(new Dimension(0, 180));
        }

        void setValue(int val, String main, String sub) {
            this.value      = val;
            this.mainLabel  = main;
            this.subLabel   = sub;
            // Animation d'incrémentation
            if (anim != null) anim.stop();
            int start = displayValue;
            int[] step = {0};
            int steps = 20;
            anim = new Timer(16, e -> {
                step[0]++;
                displayValue = start + (val - start) * step[0] / steps;
                repaint();
                if (step[0] >= steps) { displayValue = val; ((Timer)e.getSource()).stop(); }
            });
            anim.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth(), h = getHeight();
            float cx = w / 2f;

            // Titre
            g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
            g2.setColor(AppColors.TEXT_SECONDARY);
            FontMetrics fmTitle = g2.getFontMetrics();
            g2.drawString(title, cx - fmTitle.stringWidth(title)/2f, 22);

            // Arc de fond
            float r = Math.min(w, h) * 0.34f;
            float arcX = cx - r, arcY = 30;
            float arcW = r * 2, arcH = r * 2;
            int startAngle = 210, sweepMax = -240;

            g2.setStroke(new BasicStroke(10f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.setColor(new Color(50, 60, 80));
            g2.draw(new Arc2D.Float(arcX, arcY, arcW, arcH,
                startAngle, sweepMax, Arc2D.OPEN));

            // Arc de valeur
            int sweep = (int)(sweepMax * displayValue / 100.0);
            Color gaugeColor = displayValue > 85
                ? AppColors.ACCENT_RED
                : displayValue > 65 ? AppColors.ACCENT_ORANGE : accentColor;

            if (sweep != 0) {
                g2.setColor(gaugeColor);
                g2.draw(new Arc2D.Float(arcX, arcY, arcW, arcH,
                    startAngle, sweep, Arc2D.OPEN));
            }

            // Valeur principale au centre
            g2.setFont(new Font("Segoe UI", Font.BOLD, 18));
            g2.setColor(gaugeColor);
            FontMetrics fm = g2.getFontMetrics();
            float ty = arcY + arcH * 0.55f;
            g2.drawString(mainLabel, cx - fm.stringWidth(mainLabel)/2f, ty);

            // Sous-label
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(AppColors.TEXT_MUTED);
            FontMetrics fmSub = g2.getFontMetrics();
            g2.drawString(subLabel,
                cx - fmSub.stringWidth(subLabel)/2f, ty + 16);

            g2.dispose();
        }
    }
}
