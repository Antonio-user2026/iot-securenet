package com.securenet.devices;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.icons.AppIcons;
import com.securenet.scanner.AddDeviceDialog;
import com.securenet.sysinfo.SystemInfoPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import java.awt.*;
import java.net.InetAddress;
import java.util.List;

/**
 * Panel de gestion des appareils IoT v3.
 * ─ Bouton Ajouter → AddDeviceDialog (scan réseau, IP auto-découverte, nom auto)
 * ─ Bouton Infos   → SystemInfoPanel (CPU, RAM, ROM, réseau en temps réel)
 * ─ Double-clic sur une ligne → ouvre aussi SystemInfoPanel
 */
public class DevicePanel extends JPanel {

    private final DeviceService   service = DeviceService.getInstance();
    private DefaultTableModel     tableModel;
    private JTable                table;
    private JTextField            tfSearch;

    public DevicePanel() {
        super(new BorderLayout(0, 0));
        setBackground(AppColors.BG_PANEL);
        buildUI();
        refreshData();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Construction UI
    // ══════════════════════════════════════════════════════════════════════

    private void buildUI() {
        setBorder(new EmptyBorder(20, 20, 20, 20));
        add(buildToolbar(),   BorderLayout.NORTH);
        add(buildTableCard(), BorderLayout.CENTER);
        add(buildFooter(),    BorderLayout.SOUTH);
    }

    // ─── Toolbar ──────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout(0, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(0, 0, 14, 0));

        // Titres
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.setOpaque(false);
        left.add(UIHelper.createTitleLabel("Appareils IoT connectés"));
        left.add(Box.createVerticalStrut(2));
        left.add(UIHelper.createSubtitleLabel("Inventaire complet · Cliquez sur un appareil pour voir ses informations"));

        // Boutons
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        tfSearch = UIHelper.createTextField("🔍  Rechercher...");
        tfSearch.setPreferredSize(new Dimension(200, 36));

        JButton btnAdd      = UIHelper.createPrimaryButton("  Ajouter",     AppIcons.add(14));
        JButton btnInfo     = UIHelper.createSecondaryButton("  Infos système", AppIcons.network(14));
        JButton btnRefresh  = UIHelper.createSecondaryButton("  Actualiser",  AppIcons.refresh(14));
        JButton btnDelete   = UIHelper.createDangerButton("  Supprimer",    AppIcons.delete(14));

        // Listeners
        tfSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e)  { filterTable(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e)  { filterTable(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { filterTable(); }
        });
        btnAdd.addActionListener(e     -> openAddDeviceDialog());
        btnInfo.addActionListener(e    -> openSysInfoForSelected());
        btnRefresh.addActionListener(e -> refreshData());
        btnDelete.addActionListener(e  -> deleteSelected());

        right.add(tfSearch);
        right.add(btnAdd);
        right.add(btnInfo);
        right.add(btnRefresh);
        right.add(btnDelete);

        bar.add(left,  BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ─── Table ────────────────────────────────────────────────────────────

    private JPanel buildTableCard() {
        String[] cols = {"", "Nom", "Adresse IP", "MAC", "Type", "Vendeur", "Firmware", "Statut"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
            @Override public Class<?> getColumnClass(int col) {
                return col == 0 ? ImageIcon.class : String.class;
            }
        };

        table = new JTable(tableModel);
        styleTable();

        // Double-clic → ouvrir SystemInfoPanel
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) openSysInfoForSelected();
            }
        });

        JPanel card = UIHelper.createCardPanel(new BorderLayout());
        card.add(UIHelper.createScrollPane(table), BorderLayout.CENTER);
        return card;
    }

    private void styleTable() {
        table.setBackground(AppColors.BG_DARK);
        table.setForeground(AppColors.TEXT_PRIMARY);
        table.setFont(AppFonts.BODY);
        table.setRowHeight(36);
        table.setGridColor(AppColors.BORDER);
        table.setShowGrid(true);
        table.setSelectionBackground(new Color(0, 150, 255, 45));
        table.setSelectionForeground(AppColors.TEXT_PRIMARY);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.getTableHeader().setBackground(AppColors.BG_PANEL);
        table.getTableHeader().setForeground(AppColors.TEXT_SECONDARY);
        table.getTableHeader().setFont(AppFonts.SMALL);
        table.getTableHeader().setReorderingAllowed(false);

        // Largeurs des colonnes
        int[] widths = {36, 170, 130, 150, 110, 120, 90, 110};
        for (int i = 0; i < widths.length; i++) {
            TableColumn col = table.getColumnModel().getColumn(i);
            col.setPreferredWidth(widths[i]);
            if (i == 0) { col.setMaxWidth(36); col.setMinWidth(36); col.setResizable(false); }
        }

        // Renderer colonne icône
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                JLabel l = new JLabel();
                l.setHorizontalAlignment(JLabel.CENTER);
                l.setOpaque(true);
                l.setBackground(sel ? new Color(0,150,255,45) : AppColors.BG_DARK);
                if (v instanceof ImageIcon) l.setIcon((ImageIcon) v);
                return l;
            }
        });

        // Renderer statut coloré (col 7)
        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(0,150,255,45) : AppColors.BG_DARK);
                setOpaque(true);
                String s = v == null ? "" : v.toString();
                switch (s) {
                    case "ONLINE"     -> { setForeground(AppColors.STATUS_OK);       setText("● En ligne"); }
                    case "OFFLINE"    -> { setForeground(AppColors.TEXT_MUTED);      setText("○ Hors ligne"); }
                    case "SUSPICIOUS" -> { setForeground(AppColors.STATUS_WARNING);  setText("⚠ Suspect"); }
                    case "BLOCKED"    -> { setForeground(AppColors.STATUS_CRITICAL); setText("✖ Bloqué"); }
                    default           -> setForeground(AppColors.TEXT_PRIMARY);
                }
                return this;
            }
        });

        // Renderer générique (cols 1-6)
        DefaultTableCellRenderer gen = new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(
                    JTable t, Object v, boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(0,150,255,45) : AppColors.BG_DARK);
                setForeground(AppColors.TEXT_PRIMARY);
                setOpaque(true);
                return this;
            }
        };
        for (int c = 1; c <= 6; c++) table.getColumnModel().getColumn(c).setCellRenderer(gen);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 6));
        footer.setOpaque(false);
        footer.add(UIHelper.createSubtitleLabel(
            "Double-clic sur un appareil pour voir CPU / RAM / ROM · " +
            "Bouton Ajouter = scan réseau automatique"));
        return footer;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Ouvre AddDeviceDialog (scan réseau + IP auto).
     * Si l'utilisateur valide, rafraîchit la table.
     */
    private void openAddDeviceDialog() {
        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        AddDeviceDialog dlg = new AddDeviceDialog(parent);
        dlg.setVisible(true);
        // La dialog enregistre l'appareil elle-même avant de se fermer
        if (dlg.getResult() != null) refreshData();
    }

    /**
     * Ouvre SystemInfoPanel pour l'appareil sélectionné dans la table.
     * Détermine si c'est la machine locale (même IP) pour choisir
     * entre collecte réelle et simulation.
     */
    private void openSysInfoForSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this,
                "Sélectionnez un appareil dans la liste.",
                "Aucune sélection", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        String ip    = (String) tableModel.getValueAt(modelRow, 2);
        String name  = (String) tableModel.getValueAt(modelRow, 1);

        // Trouver l'objet Device complet
        Device device = service.findAll().stream()
            .filter(d -> ip.equals(d.getIpAddress()))
            .findFirst()
            .orElse(null);
        if (device == null) return;

        // Est-ce la machine locale ?
        boolean isLocal = isLocalIP(ip);

        Frame parent = (Frame) SwingUtilities.getWindowAncestor(this);
        SystemInfoPanel panel = new SystemInfoPanel(parent, device, isLocal);
        panel.setVisible(true);
    }

    private boolean isLocalIP(String ip) {
        try {
            String localIP = InetAddress.getLocalHost().getHostAddress();
            return ip.equals(localIP) || ip.equals("127.0.0.1") || ip.equals("localhost");
        } catch (Exception e) { return false; }
    }

    private void deleteSelected() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Sélectionnez un appareil à supprimer.");
            return;
        }
        int modelRow = table.convertRowIndexToModel(row);
        String ip    = (String) tableModel.getValueAt(modelRow, 2);
        String name  = (String) tableModel.getValueAt(modelRow, 1);

        int confirm = JOptionPane.showConfirmDialog(this,
            "Supprimer « " + name + " » (" + ip + ") ?",
            "Confirmer la suppression", JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        service.findAll().stream()
            .filter(d -> ip.equals(d.getIpAddress()))
            .findFirst()
            .ifPresent(d -> { service.delete(d.getId()); refreshData(); });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Données
    // ══════════════════════════════════════════════════════════════════════

    public void refreshData() {
        SwingWorker<List<Device>, Void> worker = new SwingWorker<>() {
            @Override protected List<Device> doInBackground() {
                service.insertDemoIfEmpty();
                return service.findAll();
            }
            @Override protected void done() {
                try {
                    tableModel.setRowCount(0);
                    for (Device d : get()) {
                        tableModel.addRow(new Object[]{
                            d.getIcon(16),
                            d.getName()       == null ? "—" : d.getName(),
                            d.getIpAddress(),
                            d.getMacAddress() == null ? "—" : d.getMacAddress(),
                            d.getType().name(),
                            d.getVendor()     == null ? "—" : d.getVendor(),
                            d.getFirmware()   == null ? "—" : d.getFirmware(),
                            d.getStatus().name()
                        });
                    }
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DevicePanel.this,
                        "Erreur chargement : " + e.getMessage(), "Erreur",
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void filterTable() {
        String q = tfSearch.getText().trim();
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        sorter.setRowFilter(q.isEmpty() ? null : RowFilter.regexFilter("(?i)" + q));
    }
}
