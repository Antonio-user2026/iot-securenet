package com.securenet.db;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.config.AppConfig;
import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Fenêtre de configuration de la base de données.
 * Apparaît si aucun fichier de config n'existe, ou accessible depuis les paramètres.
 */
public class DBConfigDialog extends JDialog {

    private JRadioButton rbSQLite, rbMySQL;
    private JPanel       mysqlPanel;
    private JTextField   tfHost, tfPort, tfDB, tfUser;
    private JPasswordField tfPass;
    private JLabel       lblStatus;
    private boolean      confirmed = false;

    public DBConfigDialog(Frame parent) {
        super(parent, "Configuration de la base de données", true);
        setSize(480, 500);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(AppColors.BG_PANEL);
        root.setBorder(new EmptyBorder(24, 28, 20, 28));
        setContentPane(root);

        // ── Header ─────────────────────────────────────────────────────────
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JLabel icon = new JLabel(AppIcons.network(32));
        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);
        JLabel lTitle = UIHelper.createTitleLabel("Base de données");
        JLabel lSub   = UIHelper.createSubtitleLabel("Choisissez votre moteur de stockage");
        titleBlock.add(lTitle); titleBlock.add(lSub);
        header.add(icon,       BorderLayout.WEST);
        header.add(titleBlock, BorderLayout.CENTER);

        // ── Choix du mode ──────────────────────────────────────────────────
        JPanel modePanel = UIHelper.createCardPanel(new GridLayout(1, 2, 12, 0));

        rbSQLite = new JRadioButton("SQLite  (local, dev)");
        rbMySQL  = new JRadioButton("MySQL  (serveur, prod)");
        for (JRadioButton rb : new JRadioButton[]{rbSQLite, rbMySQL}) {
            rb.setBackground(AppColors.BG_CARD);
            rb.setForeground(AppColors.TEXT_PRIMARY);
            rb.setFont(AppFonts.BODY_BOLD);
        }
        ButtonGroup bg = new ButtonGroup();
        bg.add(rbSQLite); bg.add(rbMySQL);

        boolean isMysql = "mysql".equals(AppConfig.getInstance().getDbMode());
        rbSQLite.setSelected(!isMysql);
        rbMySQL.setSelected(isMysql);

        modePanel.add(buildModeCard(rbSQLite, "Fichier local\nZéro configuration\nIdéal pour les tests",
                      AppColors.ACCENT_GREEN));
        modePanel.add(buildModeCard(rbMySQL, "Serveur distant\nHaute performance\nMode production",
                      AppColors.ACCENT_BLUE));

        // ── Paramètres MySQL ───────────────────────────────────────────────
        mysqlPanel = UIHelper.createCardPanel(new GridLayout(5, 2, 8, 10));
        mysqlPanel.setBorder(new EmptyBorder(14, 16, 14, 16));

        tfHost = UIHelper.createTextField("localhost");
        tfPort = UIHelper.createTextField("3306");
        tfDB   = UIHelper.createTextField("securenet");
        tfUser = UIHelper.createTextField("root");
        tfPass = UIHelper.createPasswordField("mot de passe");

        AppConfig cfg = AppConfig.getInstance();
        tfHost.setText(cfg.getDbHost());
        tfPort.setText(String.valueOf(cfg.getDbPort()));
        tfDB.setText(cfg.getDbName());
        tfUser.setText(cfg.getDbUser());

        addField(mysqlPanel, "Hôte MySQL",      tfHost);
        addField(mysqlPanel, "Port",            tfPort);
        addField(mysqlPanel, "Base de données", tfDB);
        addField(mysqlPanel, "Utilisateur",     tfUser);
        addField(mysqlPanel, "Mot de passe",    tfPass);

        // ── Status + boutons ───────────────────────────────────────────────
        lblStatus = new JLabel(" ");
        lblStatus.setFont(AppFonts.SMALL);
        lblStatus.setForeground(AppColors.TEXT_MUTED);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        JButton btnTest  = UIHelper.createSecondaryButton("Tester la connexion");
        JButton btnSave  = UIHelper.createPrimaryButton("Enregistrer");
        JButton btnSkip  = UIHelper.createSecondaryButton("Utiliser SQLite");
        btnRow.add(btnTest); btnRow.add(btnSkip); btnRow.add(btnSave);

        btnTest.addActionListener(e -> testConnection());
        btnSave.addActionListener(e -> saveAndClose());
        btnSkip.addActionListener(e -> { rbSQLite.setSelected(true); saveAndClose(); });

        // Toggle MySQL panel
        rbSQLite.addActionListener(e -> mysqlPanel.setVisible(false));
        rbMySQL.addActionListener(e  -> mysqlPanel.setVisible(true));
        mysqlPanel.setVisible(isMysql);

        root.add(header,     BorderLayout.NORTH);
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.add(modePanel);
        center.add(Box.createVerticalStrut(12));
        center.add(mysqlPanel);
        root.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(lblStatus, BorderLayout.WEST);
        south.add(btnRow,    BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
    }

    private JPanel buildModeCard(JRadioButton rb, String desc, Color accent) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBackground(AppColors.BG_DARK);
        p.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(accent, 1, true),
            new EmptyBorder(10, 12, 10, 12)));
        rb.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(rb);
        p.add(Box.createVerticalStrut(6));
        for (String line : desc.split("\n")) {
            JLabel l = UIHelper.createSubtitleLabel("  " + line);
            l.setAlignmentX(Component.LEFT_ALIGNMENT);
            p.add(l);
        }
        return p;
    }

    private void addField(JPanel panel, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(AppFonts.SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        panel.add(lbl);
        panel.add(field);
    }

    private void testConnection() {
        if (!rbMySQL.isSelected()) {
            lblStatus.setText("SQLite : aucun test nécessaire.");
            lblStatus.setForeground(AppColors.ACCENT_GREEN);
            return;
        }
        lblStatus.setText("Test en cours...");
        lblStatus.setForeground(AppColors.TEXT_SECONDARY);

        SwingWorker<Boolean, Void> w = new SwingWorker<>() {
            @Override protected Boolean doInBackground() {
                try {
                    return DatabaseManager.testMySQLConnection(
                        tfHost.getText().trim(),
                        Integer.parseInt(tfPort.getText().trim()),
                        tfDB.getText().trim(),
                        tfUser.getText().trim(),
                        new String(tfPass.getPassword()));
                } catch (Exception e) { return false; }
            }
            @Override protected void done() {
                try {
                    if (get()) {
                        lblStatus.setText("✔ Connexion réussie !");
                        lblStatus.setForeground(AppColors.ACCENT_GREEN);
                    } else {
                        lblStatus.setText("✘ Connexion échouée");
                        lblStatus.setForeground(AppColors.ACCENT_RED);
                    }
                } catch (Exception ex) {
                    lblStatus.setText("Erreur : " + ex.getMessage());
                    lblStatus.setForeground(AppColors.ACCENT_RED);
                }
            }
        };
        w.execute();
    }

    private void saveAndClose() {
        AppConfig cfg = AppConfig.getInstance();
        cfg.set("db.mode", rbMySQL.isSelected() ? "mysql" : "sqlite");
        if (rbMySQL.isSelected()) {
            cfg.set("db.host",     tfHost.getText().trim());
            cfg.set("db.port",     tfPort.getText().trim());
            cfg.set("db.name",     tfDB.getText().trim());
            cfg.set("db.user",     tfUser.getText().trim());
            cfg.set("db.password", new String(tfPass.getPassword()));
        }
        confirmed = true;
        dispose();
    }

    public boolean isConfirmed() { return confirmed; }
}
