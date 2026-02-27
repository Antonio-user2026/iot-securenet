package com.securenet.firewall;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.db.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Panel Firewall - Gestion des règles de filtrage réseau.
 */
public class FirewallPanel extends JPanel {

    private DefaultTableModel tableModel;
    private JLabel            lblStatus;
    private boolean           firewallEnabled = true;

    private static final String[] COLUMNS = {
        "#", "Source IP", "Dest IP", "Port", "Protocole", "Action", "Statut"
    };

    public FirewallPanel() {
        super(new BorderLayout(0, 0));
        setBackground(AppColors.BG_PANEL);
        buildUI();
        loadRules();
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
        JLabel title = UIHelper.createTitleLabel("Pare-feu (Firewall)");
        lblStatus = UIHelper.createStatusBadge("● FIREWALL ACTIF", AppColors.STATUS_OK);
        left.add(title);
        left.add(Box.createVerticalStrut(4));
        left.add(lblStatus);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JButton btnAdd    = UIHelper.createPrimaryButton("+ Nouvelle règle");
        JButton btnDelete = UIHelper.createDangerButton("🗑 Supprimer");
        JButton btnToggle = UIHelper.createSecondaryButton("⏸ Désactiver FW");

        btnAdd.addActionListener(e    -> showAddRuleDialog());
        btnDelete.addActionListener(e -> deleteSelectedRule());
        btnToggle.addActionListener(e -> toggleFirewall(btnToggle));

        right.add(btnAdd);
        right.add(btnDelete);
        right.add(btnToggle);

        header.add(left,  BorderLayout.WEST);
        header.add(right, BorderLayout.EAST);

        // ─── Split : Table + Console ────────────────────────────────────
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        split.setDividerLocation(400);
        split.setBackground(AppColors.BG_PANEL);
        split.setBorder(null);
        split.setResizeWeight(0.7);

        split.setTopComponent(buildRulesTable());
        split.setBottomComponent(buildConsole());

        add(header, BorderLayout.NORTH);
        add(split,  BorderLayout.CENTER);
    }

    private JPanel buildRulesTable() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 10));
        panel.add(UIHelper.createTitleLabel("Règles actives"), BorderLayout.NORTH);

        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        JTable table = new JTable(tableModel);
        table.setBackground(AppColors.BG_DARK);
        table.setForeground(AppColors.TEXT_PRIMARY);
        table.setFont(AppFonts.BODY);
        table.setRowHeight(30);
        table.setGridColor(AppColors.BORDER);
        table.getTableHeader().setBackground(AppColors.BG_PANEL);
        table.getTableHeader().setForeground(AppColors.TEXT_SECONDARY);
        table.getTableHeader().setFont(AppFonts.SMALL);
        table.setSelectionBackground(new Color(0, 150, 255, 40));

        // Colorier l'action
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v,
                    boolean sel, boolean foc, int row, int col) {
                super.getTableCellRendererComponent(t, v, sel, foc, row, col);
                setBackground(sel ? new Color(0, 150, 255, 40) : AppColors.BG_DARK);
                String action = (String) t.getModel().getValueAt(row, 5);
                if      ("DENY".equals(action))  setForeground(AppColors.ACCENT_RED);
                else if ("ALLOW".equals(action)) setForeground(AppColors.ACCENT_GREEN);
                else                              setForeground(AppColors.TEXT_PRIMARY);
                setOpaque(true);
                return this;
            }
        });

        int[] widths = {40, 140, 140, 70, 100, 90, 90};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        panel.add(UIHelper.createScrollPane(table), BorderLayout.CENTER);

        // Info bas
        JLabel info = UIHelper.createSubtitleLabel(
            "⚠ En production, les règles sont appliquées via iptables/nftables (ProcessBuilder)");
        panel.add(info, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildConsole() {
        JPanel panel = UIHelper.createCardPanel(new BorderLayout(0, 8));
        panel.add(UIHelper.createTitleLabel("Console Firewall"), BorderLayout.NORTH);

        JTextArea console = new JTextArea();
        console.setFont(AppFonts.MONO);
        console.setBackground(new Color(8, 12, 18));
        console.setForeground(new Color(0, 230, 100));
        console.setEditable(false);
        console.setLineWrap(true);
        console.setText(
            "# IoT SecureNet Firewall v1.0\n" +
            "# Interface: eth0\n" +
            "# Policy: DROP\n\n" +
            "iptables -P INPUT DROP\n" +
            "iptables -P FORWARD DROP\n" +
            "iptables -P OUTPUT ACCEPT\n" +
            "iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT\n" +
            "iptables -A INPUT -i lo -j ACCEPT\n\n" +
            "# [Règles personnalisées ici...]\n"
        );

        panel.add(UIHelper.createScrollPane(console), BorderLayout.CENTER);
        return panel;
    }

    private void loadRules() {
        // Insérer règles par défaut si vide
        String checkSql = "SELECT COUNT(*) FROM firewall_rules";
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(checkSql)) {
            if (rs.getInt(1) == 0) insertDefaultRules();
        } catch (SQLException e) {
            System.err.println("[Firewall] checkSql: " + e.getMessage());
        }

        String sql = "SELECT * FROM firewall_rules ORDER BY id";
        try (Statement st = DatabaseManager.getInstance().getConnection().createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            tableModel.setRowCount(0);
            while (rs.next()) {
                tableModel.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("source_ip"),
                    rs.getString("dest_ip"),
                    rs.getInt("port") == 0 ? "ANY" : rs.getInt("port"),
                    rs.getString("protocol"),
                    rs.getString("action"),
                    rs.getInt("enabled") == 1 ? "ACTIVE" : "INACTIF"
                });
            }
        } catch (SQLException e) {
            System.err.println("[Firewall] loadRules: " + e.getMessage());
        }
    }

    private void insertDefaultRules() {
        Object[][] defaults = {
            {"0.0.0.0/0",    "ANY",           22, "TCP", "DENY"},
            {"0.0.0.0/0",    "ANY",           23, "TCP", "DENY"},
            {"192.168.1.0/24","192.168.1.0/24",1883,"TCP","ALLOW"},
            {"0.0.0.0/0",    "ANY",           1883,"TCP","DENY"},
            {"ANY",          "ANY",            80, "TCP", "ALLOW"},
            {"ANY",          "ANY",           443, "TCP", "ALLOW"},
        };
        String sql = "INSERT INTO firewall_rules (source_ip,dest_ip,port,protocol,action) VALUES(?,?,?,?,?)";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            for (Object[] row : defaults) {
                ps.setString(1, (String) row[0]);
                ps.setString(2, (String) row[1]);
                ps.setInt(3,    (Integer) row[2]);
                ps.setString(4, (String) row[3]);
                ps.setString(5, (String) row[4]);
                ps.addBatch();
            }
            ps.executeBatch();
        } catch (SQLException e) {
            System.err.println("[Firewall] insertDefault: " + e.getMessage());
        }
    }

    private void showAddRuleDialog() {
        JDialog dialog = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this),
                                     "Nouvelle règle firewall", true);
        dialog.setSize(420, 360);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.BG_PANEL);
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));

        JTextField tfSrc  = UIHelper.createTextField("Source IP (ex: 192.168.1.0/24 ou ANY)");
        JTextField tfDst  = UIHelper.createTextField("Dest IP (ex: 192.168.1.1 ou ANY)");
        JTextField tfPort = UIHelper.createTextField("Port (0 = tous)");
        JComboBox<String> cbProto  = new JComboBox<>(new String[]{"TCP","UDP","ICMP","ANY"});
        JComboBox<String> cbAction = new JComboBox<>(new String[]{"ALLOW","DENY","LOG"});

        for (JComponent c : new JComponent[]{tfSrc,tfDst,tfPort,cbProto,cbAction}) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            if (c instanceof JComboBox) {
                c.setBackground(AppColors.BG_DARK);
                ((JComboBox<?>)c).setForeground(AppColors.TEXT_PRIMARY);
            }
        }

        JButton btnSave = UIHelper.createSuccessButton("Appliquer la règle");
        btnSave.addActionListener(e -> {
            String sql = "INSERT INTO firewall_rules (source_ip,dest_ip,port,protocol,action) VALUES(?,?,?,?,?)";
            try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
                ps.setString(1, tfSrc.getText().trim().isEmpty() ? "ANY" : tfSrc.getText());
                ps.setString(2, tfDst.getText().trim().isEmpty() ? "ANY" : tfDst.getText());
                ps.setInt(3,    tfPort.getText().trim().isEmpty() ? 0 : Integer.parseInt(tfPort.getText()));
                ps.setString(4, (String) cbProto.getSelectedItem());
                ps.setString(5, (String) cbAction.getSelectedItem());
                ps.executeUpdate();
                dialog.dispose();
                loadRules();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dialog, "Erreur : " + ex.getMessage());
            }
        });

        panel.add(createLbl("Source IP")); panel.add(Box.createVerticalStrut(4));
        panel.add(tfSrc); panel.add(Box.createVerticalStrut(10));
        panel.add(createLbl("Destination IP")); panel.add(Box.createVerticalStrut(4));
        panel.add(tfDst); panel.add(Box.createVerticalStrut(10));
        panel.add(createLbl("Port")); panel.add(Box.createVerticalStrut(4));
        panel.add(tfPort); panel.add(Box.createVerticalStrut(10));
        panel.add(createLbl("Protocole")); panel.add(Box.createVerticalStrut(4));
        panel.add(cbProto); panel.add(Box.createVerticalStrut(10));
        panel.add(createLbl("Action")); panel.add(Box.createVerticalStrut(4));
        panel.add(cbAction); panel.add(Box.createVerticalStrut(16));
        panel.add(btnSave);

        dialog.setContentPane(panel);
        dialog.setVisible(true);
    }

    private JLabel createLbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(AppFonts.SMALL);
        l.setForeground(AppColors.TEXT_SECONDARY);
        return l;
    }

    private void deleteSelectedRule() {
        // Simplification : supprimer la dernière règle ajoutée
        if (tableModel.getRowCount() == 0) return;
        int id = (int) tableModel.getValueAt(tableModel.getRowCount() - 1, 0);
        String sql = "DELETE FROM firewall_rules WHERE id=?";
        try (PreparedStatement ps = DatabaseManager.getInstance().getConnection().prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
            loadRules();
        } catch (SQLException e) {
            System.err.println("[Firewall] delete: " + e.getMessage());
        }
    }

    private void toggleFirewall(JButton btn) {
        firewallEnabled = !firewallEnabled;
        if (firewallEnabled) {
            lblStatus.setText("● FIREWALL ACTIF");
            lblStatus.setForeground(AppColors.STATUS_OK);
            btn.setText("⏸ Désactiver FW");
        } else {
            lblStatus.setText("⚠ FIREWALL INACTIF");
            lblStatus.setForeground(AppColors.STATUS_CRITICAL);
            btn.setText("▶ Activer FW");
        }
    }
}
