package com.securenet.scanner;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.devices.Device;
import com.securenet.devices.DeviceService;
import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * Dialogue d'ajout d'un appareil IoT avec découverte réseau intégrée.
 *
 * Fonctionnalités :
 *  - Scan du réseau local au démarrage
 *  - Liste déroulante des IPs découvertes (auto-remplie en temps réel)
 *  - Sélection d'une IP → pré-remplissage automatique du nom, MAC, vendeur
 *  - Saisie manuelle possible si l'appareil n'est pas trouvé
 *  - Indicateur de progression du scan
 */
public class AddDeviceDialog extends JDialog {

    // Scanner réseau
    private final NetworkScanner scanner = new NetworkScanner();

    // Composants UI
    private JComboBox<String>    cbIPAddress;
    private JTextField           tfName;
    private JTextField           tfMAC;
    private JTextField           tfVendor;
    private JTextField           tfFirmware;
    private JComboBox<Device.Type>   cbType;
    private JComboBox<Device.Status> cbStatus;
    private JLabel               lblScanStatus;
    private JProgressBar         progressBar;
    private JButton              btnScan;
    private JButton              btnSave;
    private JLabel               lblPingResult;

    // Données découvertes (indexées par IP)
    private final java.util.Map<String, NetworkScanner.DiscoveredHost> hostMap = new java.util.LinkedHashMap<>();

    // Résultat
    private Device result = null;

    public AddDeviceDialog(Frame parent) {
        super(parent, "Ajouter un appareil IoT", true);
        setSize(520, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
        startScan(); // Lancer le scan immédiatement
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AppColors.BG_PANEL);
        setContentPane(root);

        // ── Header ─────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBackground(AppColors.BG_DARK);
        header.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel iconLabel = new JLabel(AppIcons.devices(28));
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel lTitle = UIHelper.createTitleLabel("Ajouter un appareil IoT");
        JLabel lSub   = UIHelper.createSubtitleLabel("Scan du réseau local en cours...");
        titleBlock.add(lTitle); titleBlock.add(lSub);
        header.add(iconLabel,  BorderLayout.WEST);
        header.add(titleBlock, BorderLayout.CENTER);

        // ── Scan status bar ────────────────────────────────────────────────
        JPanel scanBar = new JPanel(new BorderLayout(8, 0));
        scanBar.setBackground(new Color(0, 100, 200, 20));
        scanBar.setBorder(new EmptyBorder(8, 16, 8, 16));

        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new Dimension(0, 4));
        progressBar.setBackground(AppColors.BG_CARD);
        progressBar.setForeground(AppColors.ACCENT_BLUE);
        progressBar.setBorderPainted(false);

        lblScanStatus = new JLabel("Scan du réseau local...");
        lblScanStatus.setFont(AppFonts.SMALL);
        lblScanStatus.setForeground(AppColors.ACCENT_BLUE);

        btnScan = new JButton("↻ Re-scanner");
        btnScan.setFont(AppFonts.SMALL);
        btnScan.setBackground(AppColors.BG_CARD);
        btnScan.setForeground(AppColors.TEXT_SECONDARY);
        btnScan.setFocusPainted(false);
        btnScan.setBorderPainted(false);
        btnScan.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnScan.addActionListener(e -> startScan());

        scanBar.add(lblScanStatus, BorderLayout.WEST);
        scanBar.add(btnScan,       BorderLayout.EAST);

        JPanel scanWrapper = new JPanel(new BorderLayout());
        scanWrapper.setOpaque(false);
        scanWrapper.add(scanBar,     BorderLayout.NORTH);
        scanWrapper.add(progressBar, BorderLayout.SOUTH);

        // ── Formulaire ─────────────────────────────────────────────────────
        JPanel form = buildForm();

        // ── Footer ─────────────────────────────────────────────────────────
        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setBackground(AppColors.BG_DARK);
        footer.setBorder(new EmptyBorder(12, 20, 14, 20));

        lblPingResult = new JLabel(" ");
        lblPingResult.setFont(AppFonts.SMALL);
        lblPingResult.setForeground(AppColors.TEXT_MUTED);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        JButton btnCancel = UIHelper.createSecondaryButton("Annuler");
        btnSave = UIHelper.createPrimaryButton("Enregistrer", AppIcons.check(14));

        btnCancel.addActionListener(e -> dispose());
        btnSave.addActionListener(e   -> saveDevice());

        btnRow.add(btnCancel);
        btnRow.add(btnSave);

        footer.add(lblPingResult, BorderLayout.WEST);
        footer.add(btnRow,        BorderLayout.EAST);

        // ── Assembly ───────────────────────────────────────────────────────
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(header,      BorderLayout.NORTH);
        north.add(scanWrapper, BorderLayout.SOUTH);

        root.add(north,  BorderLayout.NORTH);
        root.add(new JScrollPane(form) {{
            setBorder(BorderFactory.createEmptyBorder());
            getViewport().setBackground(AppColors.BG_PANEL);
        }}, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(AppColors.BG_PANEL);
        form.setBorder(new EmptyBorder(16, 20, 8, 20));

        // ── Section Réseau ────────────────────────────────────────────────
        form.add(sectionLabel("DÉCOUVERTE RÉSEAU"));
        form.add(Box.createVerticalStrut(8));

        // ComboBox IP avec auto-complétion
        cbIPAddress = new JComboBox<>();
        cbIPAddress.setEditable(true);
        cbIPAddress.setBackground(AppColors.BG_DARK);
        cbIPAddress.setForeground(AppColors.TEXT_PRIMARY);
        cbIPAddress.setFont(AppFonts.BODY);
        cbIPAddress.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        cbIPAddress.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Renderer pour afficher IP + hostname dans la liste
        cbIPAddress.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setBackground(isSelected ? AppColors.ACCENT_BLUE : AppColors.BG_CARD);
                setForeground(isSelected ? Color.WHITE : AppColors.TEXT_PRIMARY);
                setFont(AppFonts.MONO);
                setBorder(new EmptyBorder(4, 8, 4, 8));
                if (value != null) {
                    String ip = extractIP(value.toString());
                    NetworkScanner.DiscoveredHost host = hostMap.get(ip);
                    if (host != null) {
                        setText("<html><b>" + host.ip + "</b>  " +
                            "<font color='#8096B4'>" + host.hostname +
                            "</font>  <font color='#00DC82'>" + host.pingMs + "ms</font>" +
                            (host.vendor.equals("Unknown") ? "" :
                            "  <font color='#A064FF'>" + host.vendor + "</font>") +
                            "</html>");
                    }
                }
                return this;
            }
        });

        // Quand l'utilisateur sélectionne une IP → auto-remplir les autres champs
        cbIPAddress.addActionListener(e -> {
            if ("comboBoxChanged".equals(e.getActionCommand())) {
                Object selected = cbIPAddress.getSelectedItem();
                if (selected != null) {
                    String ip = extractIP(selected.toString());
                    autofillFromIP(ip);
                }
            }
        });

        // Quand l'utilisateur tape dans le champ éditable → filtrer
        JTextField cbEditor = (JTextField) cbIPAddress.getEditor().getEditorComponent();
        cbEditor.setBackground(AppColors.BG_DARK);
        cbEditor.setForeground(AppColors.TEXT_PRIMARY);
        cbEditor.setCaretColor(AppColors.TEXT_PRIMARY);

        addRow(form, "Adresse IP *", cbIPAddress,
               "Sélectionnez parmi les appareils découverts ou saisissez manuellement");

        form.add(Box.createVerticalStrut(14));

        // ── Section Informations ──────────────────────────────────────────
        form.add(sectionLabel("INFORMATIONS DE L'APPAREIL"));
        form.add(Box.createVerticalStrut(8));

        tfName     = styleTextField("ex: Caméra Entrée");
        tfMAC      = styleTextField("ex: AA:BB:CC:DD:EE:FF");
        tfVendor   = styleTextField("ex: Cisco, Hikvision...");
        tfFirmware = styleTextField("ex: 1.0.0");

        cbType   = new JComboBox<>(Device.Type.values());
        cbStatus = new JComboBox<>(Device.Status.values());
        styleCombo(cbType); styleCombo(cbStatus);

        addRow(form, "Nom de l'appareil", tfName, null);
        addRow(form, "Adresse MAC",       tfMAC, "Auto-détectée si disponible");
        addRow(form, "Vendeur",           tfVendor, "Auto-détecté depuis l'OUI MAC");
        addRow(form, "Firmware",          tfFirmware, null);
        addRow(form, "Type d'appareil",   cbType, null);
        addRow(form, "Statut initial",    cbStatus, null);

        return form;
    }

    // ── Scan réseau ────────────────────────────────────────────────────────

    private void startScan() {
        if (scanner.isScanning()) return;

        cbIPAddress.removeAllItems();
        hostMap.clear();
        progressBar.setIndeterminate(true);
        progressBar.setVisible(true);
        lblScanStatus.setText("Scan en cours... 0 appareil(s) trouvé(s)");
        lblScanStatus.setForeground(AppColors.ACCENT_BLUE);
        btnScan.setEnabled(false);

        scanner.setOnFound(host -> SwingUtilities.invokeLater(() -> {
            hostMap.put(host.ip, host);
            cbIPAddress.addItem(host.ip);
            lblScanStatus.setText("Scan en cours... " + hostMap.size() + " appareil(s) trouvé(s)");
        }));

        scanner.setOnDone(() -> SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setValue(100);
            int count = hostMap.size();
            lblScanStatus.setText(count + " appareil(s) découvert(s) sur le réseau local");
            lblScanStatus.setForeground(count > 0 ? AppColors.ACCENT_GREEN : AppColors.TEXT_MUTED);
            btnScan.setEnabled(true);
            if (count > 0) cbIPAddress.setSelectedIndex(0);
        }));

        scanner.scanAsync();
    }

    // ── Auto-remplissage ───────────────────────────────────────────────────

    private void autofillFromIP(String ip) {
        NetworkScanner.DiscoveredHost host = hostMap.get(ip);
        if (host == null) {
            lblPingResult.setText("IP saisie manuellement");
            lblPingResult.setForeground(AppColors.TEXT_MUTED);
            return;
        }

        // Pré-remplir le nom depuis le hostname
        String name = host.hostname;
        if (name.equals(ip)) name = guessNameFromIP(ip, host.vendor);
        tfName.setText(name);

        // MAC
        if (!host.mac.equals("—")) tfMAC.setText(host.mac);

        // Vendeur
        if (!host.vendor.equals("Unknown")) tfVendor.setText(host.vendor);

        // Ping status
        lblPingResult.setText("Ping: " + host.pingMs + "ms  —  " + host.hostname);
        lblPingResult.setForeground(host.pingMs < 10
            ? AppColors.ACCENT_GREEN
            : host.pingMs < 100 ? AppColors.ACCENT_ORANGE : AppColors.ACCENT_RED);

        // Deviner le type
        Device.Type guessedType = guessDeviceType(host);
        cbType.setSelectedItem(guessedType);
        cbStatus.setSelectedItem(Device.Status.ONLINE);
    }

    private String guessNameFromIP(String ip, String vendor) {
        if (ip.endsWith(".1") || ip.endsWith(".254")) return "Routeur " + vendor;
        if (vendor.contains("Hikvision") || vendor.contains("Dahua")) return "Caméra IP " + ip;
        if (vendor.contains("Raspberry"))  return "Raspberry Pi " + ip;
        if (vendor.contains("Xiaomi") || vendor.contains("Aqara")) return "Capteur " + vendor;
        if (vendor.contains("Nest") || vendor.contains("Yale"))    return "Smart Device " + vendor;
        return vendor.equals("Unknown") ? "Appareil " + ip : vendor + " " + ip;
    }

    private Device.Type guessDeviceType(NetworkScanner.DiscoveredHost host) {
        String v = host.vendor.toLowerCase();
        String h = host.hostname.toLowerCase();
        if (v.contains("hikvision") || v.contains("dahua") || h.contains("cam")) return Device.Type.CAMERA;
        if (v.contains("cisco") || v.contains("tp-link") || host.ip.endsWith(".1")) return Device.Type.ROUTER;
        if (v.contains("philips") || v.contains("aqara"))  return Device.Type.GATEWAY;
        if (v.contains("nest") || h.contains("thermo"))    return Device.Type.THERMOSTAT;
        if (v.contains("yale") || h.contains("lock"))      return Device.Type.SMART_LOCK;
        if (v.contains("xiaomi") || h.contains("sensor"))  return Device.Type.SENSOR;
        return Device.Type.UNKNOWN;
    }

    // ── Sauvegarde ─────────────────────────────────────────────────────────

    private void saveDevice() {
        String ip = extractIP(
            cbIPAddress.getEditor().getEditorComponent() instanceof JTextField tf
                ? tf.getText().trim()
                : cbIPAddress.getSelectedItem() != null
                    ? cbIPAddress.getSelectedItem().toString().trim()
                    : "");

        if (ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "L'adresse IP est requise.",
                "Validation", JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new Device();
        result.setIpAddress(ip);
        result.setName(tfName.getText().trim().isEmpty() ? "Appareil " + ip : tfName.getText().trim());
        result.setMacAddress(tfMAC.getText().trim());
        result.setVendor(tfVendor.getText().trim());
        result.setFirmware(tfFirmware.getText().trim());
        result.setType((Device.Type)   cbType.getSelectedItem());
        result.setStatus((Device.Status) cbStatus.getSelectedItem());
        result.setSegment("default");

        DeviceService.getInstance().save(result);
        dispose();
    }

    // ── Helpers UI ─────────────────────────────────────────────────────────

    private JTextField styleTextField(String placeholder) {
        JTextField tf = UIHelper.createTextField(placeholder);
        tf.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        tf.setAlignmentX(Component.LEFT_ALIGNMENT);
        return tf;
    }

    private void styleCombo(JComboBox<?> cb) {
        cb.setBackground(AppColors.BG_DARK);
        cb.setForeground(AppColors.TEXT_PRIMARY);
        cb.setFont(AppFonts.BODY);
        cb.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        cb.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private void addRow(JPanel panel, String label, JComponent field, String hint) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(AppFonts.SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(lbl);
        panel.add(Box.createVerticalStrut(4));
        panel.add(field);
        if (hint != null) {
            JLabel hintLbl = UIHelper.createSubtitleLabel(hint);
            hintLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            hintLbl.setBorder(new EmptyBorder(2, 0, 0, 0));
            panel.add(hintLbl);
        }
        panel.add(Box.createVerticalStrut(10));
    }

    private JLabel sectionLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        lbl.setBorder(new EmptyBorder(4, 0, 2, 0));
        return lbl;
    }

    private String extractIP(String text) {
        if (text == null) return "";
        // Extraire juste l'IP si le texte contient des infos supplémentaires
        String trimmed = text.trim();
        // Si c'est du HTML ou contient des séparateurs, extraire la première partie
        if (trimmed.contains("  —  ")) trimmed = trimmed.split("  —  ")[0].trim();
        if (trimmed.contains(" "))     trimmed = trimmed.split(" ")[0].trim();
        return trimmed;
    }

    public Device getResult() { return result; }
}
