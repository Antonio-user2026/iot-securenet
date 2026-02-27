package com.securenet.twin;

import com.securenet.attack.AttackSimulationDialog;
import com.securenet.twin.DeviceActionDialog;
import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.db.DatabaseManager;
import com.securenet.devices.Device;
import com.securenet.devices.DeviceService;
import com.securenet.icons.AppIcons;
import com.securenet.scanner.AddDeviceDialog;
import com.securenet.sysinfo.SystemInfoPanel;

import javax.swing.*;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.InetAddress;
import java.sql.*;
import java.util.*;
import java.util.List;

/**
 * Panel principal du module Digital Twin v3.
 * ─ Topologie interactive avec nœuds déplaçables
 * ─ Sélection d'un nœud → sidebar détails
 * ─ Bouton "Simuler attaque" → AttackSimulationDialog (12 types d'attaques)
 * ─ Bouton "Infos système"  → SystemInfoPanel (CPU/RAM/ROM/Réseau)
 * ─ Bouton "Ajouter"        → AddDeviceDialog (scan réseau intégré)
 */
public class DigitalTwinPanel extends JPanel {

    // Topologie
    private NetworkTopologyPanel topoPanel;

    // Sidebar — labels d'infos nœud courant
    private JLabel lblNodeName, lblNodeIP, lblNodeMAC,
                   lblNodeType, lblNodeStatus, lblNodeVendor, lblNodeFirmware;

    // Boutons d'action nœud (désactivés si rien sélectionné)
    private JButton btnAttack, btnSysInfo, btnBlock, btnScan, btnPing, btnActions;

    // KPI
    private JLabel lblNodeCount, lblLinkCount, lblAlertCount;
    private int    alertCount = 0;

    // Log
    private JTextArea taEventLog;

    // Nœud courant
    private TopologyNode currentNode = null;

    // ──────────────────────────────────────────────────────────────────────

    public DigitalTwinPanel() {
        super(new BorderLayout(0, 0));
        setBackground(AppColors.BG_PANEL);
        buildUI();
        loadTopology();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Construction UI
    // ══════════════════════════════════════════════════════════════════════

    private void buildUI() {
        setBorder(new EmptyBorder(20, 20, 20, 20));

        topoPanel = new NetworkTopologyPanel();
        topoPanel.setOnNodeSelected(this::onNodeSelected);

        JPanel canvasCard = UIHelper.createCardPanel(new BorderLayout());
        canvasCard.setBorder(new EmptyBorder(2, 2, 2, 2));
        canvasCard.add(topoPanel, BorderLayout.CENTER);
        canvasCard.add(buildCanvasHint(), BorderLayout.SOUTH);

        JPanel sidebar = buildSidebar();
        sidebar.setPreferredSize(new Dimension(260, 0));

        JSplitPane split = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT, canvasCard, sidebar);
        split.setDividerLocation(900);
        split.setBackground(AppColors.BG_PANEL);
        split.setBorder(null);
        split.setResizeWeight(0.78);

        add(buildToolbar(), BorderLayout.NORTH);
        add(split,          BorderLayout.CENTER);
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 8));
        wrapper.setOpaque(false);
        wrapper.setBorder(new EmptyBorder(0, 0, 14, 0));

        // Titre + sous-titre
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        titleBlock.add(UIHelper.createTitleLabel("Digital Twin — Topologie réseau IoT"));
        titleBlock.add(Box.createVerticalStrut(2));
        titleBlock.add(UIHelper.createSubtitleLabel(
            "Sélectionnez un appareil sur le graphe pour simuler des attaques ou consulter ses informations"));

        // Boutons toolbar
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0));
        btnRow.setOpaque(false);

        JButton btnFit     = mkIconBtn(AppIcons.search(15),  "Ajuster la vue");
        JButton btnReset   = mkIconBtn(AppIcons.refresh(15), "Réinitialiser la vue");
        JButton btnAddNode = UIHelper.createPrimaryButton(" Ajouter", AppIcons.add(14));
        JButton btnReload  = UIHelper.createSecondaryButton(" Actualiser", AppIcons.refresh(14));

        btnFit.addActionListener(e     -> topoPanel.zoomFit());
        btnReset.addActionListener(e   -> topoPanel.resetView());
        btnAddNode.addActionListener(e -> openAddDeviceDialog());
        btnReload.addActionListener(e  -> loadTopology());

        btnRow.add(btnFit); btnRow.add(btnReset);
        btnRow.add(new JSeparator(JSeparator.VERTICAL) {{
            setPreferredSize(new Dimension(1, 28));
            setForeground(AppColors.BORDER);
        }});
        btnRow.add(btnAddNode);
        btnRow.add(btnReload);

        JPanel topLine = new JPanel(new BorderLayout(0, 0));
        topLine.setOpaque(false);
        topLine.add(titleBlock, BorderLayout.WEST);
        topLine.add(btnRow,     BorderLayout.EAST);

        wrapper.add(topLine,     BorderLayout.NORTH);
        wrapper.add(buildKpiRow(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildKpiRow() {
        JPanel row = new JPanel(new GridLayout(1, 3, 10, 0));
        row.setOpaque(false);
        lblNodeCount  = kpiLabel("0");
        lblLinkCount  = kpiLabel("0");
        lblAlertCount = kpiLabel("0");
        row.add(buildKpiCard(AppIcons.devices(16), "Appareils",  lblNodeCount,  AppColors.ACCENT_BLUE));
        row.add(buildKpiCard(AppIcons.network(16), "Connexions", lblLinkCount,  AppColors.ACCENT_GREEN));
        row.add(buildKpiCard(AppIcons.alert(16),   "Alertes",    lblAlertCount, AppColors.ACCENT_RED));
        return row;
    }

    private JLabel kpiLabel(String t) {
        JLabel l = new JLabel(t);
        l.setFont(AppFonts.TITLE_SMALL);
        return l;
    }

    private JPanel buildKpiCard(ImageIcon icon, String label,
                                  JLabel valueLabel, Color color) {
        JPanel p = UIHelper.createCardPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        valueLabel.setForeground(color);
        p.add(new JLabel(icon));
        p.add(valueLabel);
        p.add(UIHelper.createSubtitleLabel(label));
        return p;
    }

    // ─── Hint bas du canvas ───────────────────────────────────────────────

    private JPanel buildCanvasHint() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        p.setOpaque(false);
        p.add(UIHelper.createSubtitleLabel(
            "Clic = sélectionner  ·  Glisser = déplacer  ·  Molette = zoom  ·  Alt+Drag = pan"));
        return p;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sidebar droite
    // ══════════════════════════════════════════════════════════════════════

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppColors.BG_PANEL);
        sidebar.setBorder(new EmptyBorder(0, 10, 0, 0));

        sidebar.add(buildNodeInfoCard());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(buildActionCard());
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(buildLogCard());
        return sidebar;
    }

    // ── Carte infos nœud ──────────────────────────────────────────────────

    private JPanel buildNodeInfoCard() {
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 8));

        // Titre avec icône
        JLabel title = UIHelper.createTitleLabel("Appareil sélectionné");
        title.setIcon(AppIcons.devices(16));
        card.add(title, BorderLayout.NORTH);

        // Champs
        JPanel fields = new JPanel();
        fields.setLayout(new BoxLayout(fields, BoxLayout.Y_AXIS));
        fields.setOpaque(false);

        lblNodeName     = mkDetailValue("— Aucune sélection —");
        lblNodeIP       = mkDetailValue("—");
        lblNodeMAC      = mkDetailValue("—");
        lblNodeType     = mkDetailValue("—");
        lblNodeStatus   = mkDetailValue("—");
        lblNodeVendor   = mkDetailValue("—");
        lblNodeFirmware = mkDetailValue("—");

        addDetailRow(fields, "Nom",      lblNodeName);
        addDetailRow(fields, "IP",       lblNodeIP);
        addDetailRow(fields, "MAC",      lblNodeMAC);
        addDetailRow(fields, "Type",     lblNodeType);
        addDetailRow(fields, "Statut",   lblNodeStatus);
        addDetailRow(fields, "Vendeur",  lblNodeVendor);
        addDetailRow(fields, "Firmware", lblNodeFirmware);

        card.add(fields, BorderLayout.CENTER);
        return card;
    }

    // ── Carte actions ─────────────────────────────────────────────────────

    private JPanel buildActionCard() {
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 8));

        JLabel title = UIHelper.createTitleLabel("Actions");
        title.setIcon(AppIcons.settings(15));
        card.add(title, BorderLayout.NORTH);

        // Boutons principaux (2 grandes lignes)
        // Ligne 1 : Simuler attaque + Infos système
        JPanel row1 = new JPanel(new GridLayout(1, 2, 6, 0));
        row1.setOpaque(false);
        btnAttack  = mkActionBtn("Simuler attaque", AppColors.ACCENT_RED,   AppIcons.alert(14));
        btnSysInfo = mkActionBtn("Infos système",   AppColors.ACCENT_BLUE,  AppIcons.network(14));
        row1.add(btnAttack);
        row1.add(btnSysInfo);

        // Ligne 2 : Actions réseau (pleine largeur)
        JButton btnActions = mkActionBtn("Actions réseau / Robustesse",
            AppColors.ACCENT_PURPLE, AppIcons.settings(14));
        btnActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

        // Ligne 3 : Bloquer, Scanner, Ping (rapides)
        JPanel row3 = new JPanel(new GridLayout(1, 3, 6, 0));
        row3.setOpaque(false);
        btnBlock = mkSmallBtn("Bloquer", AppColors.ACCENT_RED);
        btnScan  = mkSmallBtn("Scanner", AppColors.ACCENT_BLUE);
        btnPing  = mkSmallBtn("Ping",    AppColors.ACCENT_GREEN);
        row3.add(btnBlock); row3.add(btnScan); row3.add(btnPing);

        // Désactiver tant que rien n'est sélectionné
        setNodeActionsEnabled(false);
        btnActions.setEnabled(false);

        // Listeners
        btnAttack.addActionListener(e  -> openAttackDialog());
        btnSysInfo.addActionListener(e -> openSysInfoDialog());
        btnActions.addActionListener(e -> openDeviceActionsDialog());
        btnBlock.addActionListener(e   -> blockCurrentNode());
        btnScan.addActionListener(e    -> scanCurrentNode());
        btnPing.addActionListener(e    -> pingCurrentNode());

        JPanel all = new JPanel();
        all.setLayout(new BoxLayout(all, BoxLayout.Y_AXIS));
        all.setOpaque(false);
        all.add(row1);
        all.add(Box.createVerticalStrut(6));
        all.add(btnActions);
        all.add(Box.createVerticalStrut(6));
        all.add(row3);

        // Stocker btnActions pour activation
        this.btnActions = btnActions;

        card.add(all, BorderLayout.CENTER);

        // Hint
        JLabel hint = UIHelper.createSubtitleLabel("Sélectionnez un appareil sur le graphe");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(hint, BorderLayout.SOUTH);

        return card;
    }

    // ── Carte journal ─────────────────────────────────────────────────────

    private JPanel buildLogCard() {
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 6));
        card.setPreferredSize(new Dimension(0, 200));

        JLabel title = UIHelper.createTitleLabel("Journal");
        title.setIcon(AppIcons.siem(14));
        card.add(title, BorderLayout.NORTH);

        taEventLog = new JTextArea();
        taEventLog.setFont(new Font("Monospaced", Font.PLAIN, 10));
        taEventLog.setBackground(AppColors.BG_DARK);
        taEventLog.setForeground(AppColors.ACCENT_GREEN);
        taEventLog.setEditable(false);
        taEventLog.setLineWrap(true);
        taEventLog.setText("[OK] Digital Twin initialisé\n");

        card.add(UIHelper.createScrollPane(taEventLog), BorderLayout.CENTER);
        return card;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Chargement de la topologie
    // ══════════════════════════════════════════════════════════════════════

    private void loadTopology() {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            private final List<TopologyNode> newNodes = new ArrayList<>();
            private final List<TopologyLink> newLinks = new ArrayList<>();

            @Override protected Void doInBackground() {
                // Charger / initialiser les appareils
                DeviceService ds = DeviceService.getInstance();
                ds.insertDemoIfEmpty();
                for (Device d : ds.findAll())
                    newNodes.add(TopologyNode.fromDevice(d));

                // Charger les liens depuis la DB
                try {
                    Connection conn = DatabaseManager.getInstance().getConnection();
                    ResultSet rs = conn.createStatement()
                                       .executeQuery("SELECT * FROM device_connections");
                    while (rs.next())
                        newLinks.add(new TopologyLink(
                            rs.getInt("id"),
                            rs.getInt("source_id"),
                            rs.getInt("target_id"),
                            rs.getString("link_type"),
                            rs.getInt("bandwidth")));
                    rs.close();
                } catch (Exception e) {
                    System.err.println("[Twin] loadLinks: " + e.getMessage());
                }

                // Topologie étoile par défaut si aucun lien
                if (newLinks.isEmpty() && newNodes.size() > 1)
                    buildDefaultTopology(newNodes, newLinks);

                return null;
            }

            @Override protected void done() {
                topoPanel.setNodes(newNodes);
                topoPanel.setLinks(newLinks);
                topoPanel.zoomFit();
                lblNodeCount.setText(String.valueOf(newNodes.size()));
                lblLinkCount.setText(String.valueOf(newLinks.size()));
                logEvent("[OK] " + newNodes.size() + " appareils chargés");
                logEvent("[OK] " + newLinks.size() + " connexions");
            }
        };
        worker.execute();
    }

    private void buildDefaultTopology(List<TopologyNode> nodes,
                                       List<TopologyLink> links) {
        TopologyNode hub = nodes.stream()
            .filter(n -> n.type == Device.Type.ROUTER || n.type == Device.Type.GATEWAY)
            .findFirst().orElse(nodes.get(0));

        String[][] types = {{"ethernet","1000"},{"wifi","300"},{"zigbee","250"},{"mqtt","10"}};
        int lid = 1;
        for (TopologyNode n : nodes) {
            if (n == hub) continue;
            String[] lt = types[lid % types.length];
            links.add(new TopologyLink(lid++, hub.id, n.id, lt[0], Integer.parseInt(lt[1])));
            try {
                Connection conn = DatabaseManager.getInstance().getConnection();
                PreparedStatement ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO device_connections " +
                    "(source_id,target_id,link_type,bandwidth) VALUES(?,?,?,?)");
                ps.setInt(1, hub.id); ps.setInt(2, n.id);
                ps.setString(3, lt[0]); ps.setInt(4, Integer.parseInt(lt[1]));
                ps.executeUpdate(); ps.close();
            } catch (Exception ignored) {}
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Callback sélection nœud
    // ══════════════════════════════════════════════════════════════════════

    private void onNodeSelected(TopologyNode node) {
        currentNode = node;

        // Mettre à jour les labels de la sidebar
        lblNodeName.setText(node.name);
        lblNodeIP.setText(node.ip);
        lblNodeMAC.setText(node.mac == null || node.mac.isBlank() ? "—" : node.mac);
        lblNodeType.setText(node.type.name());
        lblNodeVendor.setText(node.vendor == null || node.vendor.isBlank() ? "—" : node.vendor);
        lblNodeFirmware.setText(node.firmware == null || node.firmware.isBlank() ? "—" : node.firmware);

        // Statut coloré
        Color sc = switch (node.status) {
            case ONLINE     -> AppColors.STATUS_OK;
            case OFFLINE    -> AppColors.TEXT_MUTED;
            case SUSPICIOUS -> AppColors.STATUS_WARNING;
            case BLOCKED    -> AppColors.STATUS_CRITICAL;
            case SCANNING   -> AppColors.ACCENT_BLUE;
        };
        lblNodeStatus.setForeground(sc);
        lblNodeStatus.setText(node.status.label);

        // Activer les boutons
        setNodeActionsEnabled(true);

        logEvent("[»] " + node.name + "  " + node.ip);
    }

    private void setNodeActionsEnabled(boolean enabled) {
        btnAttack.setEnabled(enabled);
        btnSysInfo.setEnabled(enabled);
        btnBlock.setEnabled(enabled);
        btnScan.setEnabled(enabled);
        btnPing.setEnabled(enabled);
        if (btnActions != null) btnActions.setEnabled(enabled);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Actions sur le nœud sélectionné
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Ouvre AttackSimulationDialog pour le nœud courant.
     * 12 types d'attaques simulables, journal temps réel,
     * événements injectés dans EventBus → IDS + SIEM les reçoivent.
     */
    private void openAttackDialog() {
        if (currentNode == null) return;
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        AttackSimulationDialog dlg = new AttackSimulationDialog(parent, currentNode);
        dlg.setVisible(true);
        // Après l'attaque, rafraîchir les KPIs
        alertCount += dlg.getGeneratedEventCount();
        lblAlertCount.setText(String.valueOf(alertCount));
        topoPanel.repaint();
        logEvent("[ATTK] Simulation terminée sur " + currentNode.name);
    }

    /**
     * Ouvre SystemInfoPanel pour le nœud courant.
     * Collecte réelle si c'est la machine locale, simulation sinon.
     */
    private void openSysInfoDialog() {
        if (currentNode == null) return;

        // Retrouver l'objet Device depuis l'IP
        Device device = DeviceService.getInstance().findAll().stream()
            .filter(d -> currentNode.ip.equals(d.getIpAddress()))
            .findFirst()
            .orElse(null);

        if (device == null) {
            // Créer un Device temporaire à partir du nœud
            device = new Device();
            device.setName(currentNode.name);
            device.setIpAddress(currentNode.ip);
            device.setMacAddress(currentNode.mac);
            device.setType(currentNode.type);
            device.setVendor(currentNode.vendor);
            device.setFirmware(currentNode.firmware);
            device.setStatus(Device.Status.ONLINE);
        }

        boolean isLocal = isLocalIP(currentNode.ip);
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        SystemInfoPanel infoPanel = new SystemInfoPanel(parent, device, isLocal);
        infoPanel.setVisible(true);

        logEvent("[INFO] Infos système : " + currentNode.name + "  (" + currentNode.ip + ")");
    }

    private void blockCurrentNode() {
        if (currentNode == null) return;
        currentNode.status = TopologyNode.Status.BLOCKED;
        lblNodeStatus.setForeground(AppColors.STATUS_CRITICAL);
        lblNodeStatus.setText("Bloqué");
        alertCount++;
        lblAlertCount.setText(String.valueOf(alertCount));
        topoPanel.repaint();
        logEvent("[BLOCK] " + currentNode.ip + " bloqué");
    }

    private void scanCurrentNode() {
        if (currentNode == null) return;
        currentNode.status = TopologyNode.Status.SCANNING;
        lblNodeStatus.setForeground(AppColors.ACCENT_BLUE);
        lblNodeStatus.setText("Scan...");
        topoPanel.repaint();
        logEvent("[SCAN] " + currentNode.ip + " en cours...");
        new Timer(2200, e -> {
            currentNode.status = TopologyNode.Status.ONLINE;
            lblNodeStatus.setForeground(AppColors.STATUS_OK);
            lblNodeStatus.setText("En ligne");
            topoPanel.repaint();
            logEvent("[SCAN] " + currentNode.ip + " → ports: 22, 80, 443, 1883");
        }) {{ setRepeats(false); start(); }};
    }

    private void pingCurrentNode() {
        if (currentNode == null) return;
        // Ping simulé (pas d'accès root requis)
        long rtt = 5 + (long)(Math.random() * 30);
        logEvent("[PING] " + currentNode.ip + " → 64 bytes, RTT=" + rtt + "ms, TTL=64");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Ajouter un appareil (depuis toolbar)
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Ouvre DeviceActionDialog : 14 actions réseau (bloquer, panner, patch, VLAN...),
     * simulation de pannes, rapport de robustesse.
     */
    private void openDeviceActionsDialog() {
        if (currentNode == null) return;
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        DeviceActionDialog dlg = new DeviceActionDialog(parent, currentNode, () -> {
            // Repaint du canvas après chaque action
            topoPanel.repaint();
            // Mise à jour du statut dans la sidebar
            onNodeSelected(currentNode);
        });
        dlg.setVisible(true);
    }

        private void openAddDeviceDialog() {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        AddDeviceDialog dlg = new AddDeviceDialog(parent);
        dlg.setVisible(true);
        if (dlg.getResult() != null) {
            logEvent("[ADD] Nouvel appareil : " + dlg.getResult().getName()
                     + "  " + dlg.getResult().getIpAddress());
            loadTopology();
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers UI
    // ══════════════════════════════════════════════════════════════════════

    private JButton mkIconBtn(ImageIcon icon, String tooltip) {
        JButton btn = UIHelper.createSecondaryButton("");
        btn.setIcon(icon);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(36, 36));
        return btn;
    }

    /**
     * Bouton action principal (pleine largeur, fond coloré transparent).
     */
    private JButton mkActionBtn(String text, Color color, ImageIcon icon) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                Color bg = getBackground();
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 25));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(new Color(bg.getRed(), bg.getGreen(), bg.getBlue(), 80));
                g2.setStroke(new BasicStroke(1f));
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setIcon(icon);
        b.setIconTextGap(7);
        b.setFont(AppFonts.SMALL);
        b.setBackground(color);
        b.setForeground(color);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(0, 36));

        // Hover : fond plus visible
        b.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                b.setBackground(color.brighter());
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                b.setBackground(color);
            }
        });
        return b;
    }

    private JButton mkSmallBtn(String text, Color color) {
        JButton b = new JButton(text);
        b.setFont(AppFonts.SMALL);
        b.setBackground(new Color(color.getRed(), color.getGreen(), color.getBlue(), 25));
        b.setForeground(color);
        b.setFocusPainted(false);
        b.setBorder(javax.swing.BorderFactory.createLineBorder(
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 70)));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setPreferredSize(new Dimension(0, 30));
        return b;
    }

    private JLabel mkDetailValue(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.SMALL);
        l.setForeground(AppColors.TEXT_PRIMARY);
        return l;
    }

    private void addDetailRow(JPanel panel, String key, JLabel value) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        JLabel k = new JLabel(key);
        k.setFont(AppFonts.SMALL);
        k.setForeground(AppColors.TEXT_MUTED);
        k.setPreferredSize(new Dimension(58, 18));
        row.add(k,     BorderLayout.WEST);
        row.add(value, BorderLayout.CENTER);
        panel.add(row);
        panel.add(Box.createVerticalStrut(3));
    }

    private void logEvent(String msg) {
        SwingUtilities.invokeLater(() -> {
            taEventLog.append(msg + "\n");
            taEventLog.setCaretPosition(taEventLog.getDocument().getLength());
        });
    }

    private boolean isLocalIP(String ip) {
        try {
            String local = InetAddress.getLocalHost().getHostAddress();
            return ip.equals(local) || ip.startsWith("127.");
        } catch (Exception e) { return false; }
    }
}
