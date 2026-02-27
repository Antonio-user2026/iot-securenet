package com.securenet.profile;

import com.securenet.auth.AuthService;
import com.securenet.auth.User;
import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.db.DatabaseManager;
import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;

/**
 * Fenêtre d'édition du profil utilisateur.
 * Permet de changer : nom complet, email, nom d'utilisateur, mot de passe.
 * S'affiche automatiquement au 1er login admin/admin.
 */
public class ProfileDialog extends JDialog {

    private final User       user;
    private final boolean    isFirstLogin;

    private JTextField     tfFullName;
    private JTextField     tfEmail;
    private JTextField     tfUsername;
    private JPasswordField pfOldPassword;
    private JPasswordField pfNewPassword;
    private JPasswordField pfConfirmPassword;
    private JLabel         lblAvatar;
    private String         selectedColor;
    private JLabel         lblStatus;

    // Couleurs d'avatar disponibles
    private static final String[] AVATAR_COLORS = {
        "#0096FF", "#00DC82", "#FF4646", "#A064FF",
        "#FFA000", "#00BFAE", "#FF6B6B", "#4CAF50"
    };

    public ProfileDialog(Frame parent, User user, boolean isFirstLogin) {
        super(parent, isFirstLogin
              ? "⚠ Premier démarrage — Configurez votre compte"
              : "Mon profil", true);
        this.user         = user;
        this.isFirstLogin = isFirstLogin;
        this.selectedColor = "#0096FF";
        setSize(500, 600);
        setLocationRelativeTo(parent);
        setResizable(false);
        if (isFirstLogin) setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AppColors.BG_PANEL);
        setContentPane(root);

        // ── Banner premier login ────────────────────────────────────────
        if (isFirstLogin) {
            JPanel banner = new JPanel(new BorderLayout(12, 0));
            banner.setBackground(new Color(255, 160, 0, 30));
            banner.setBorder(new EmptyBorder(12, 16, 12, 16));
            JLabel warn = new JLabel(AppIcons.alert(20));
            JLabel msg  = new JLabel("<html><b>Première connexion détectée.</b> " +
                "Pour la sécurité, veuillez modifier vos identifiants par défaut.</html>");
            msg.setFont(AppFonts.SMALL);
            msg.setForeground(AppColors.ACCENT_ORANGE);
            banner.add(warn, BorderLayout.WEST);
            banner.add(msg,  BorderLayout.CENTER);
            root.add(banner, BorderLayout.NORTH);
        }

        // ── Formulaire central ─────────────────────────────────────────
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setBackground(AppColors.BG_PANEL);
        form.setBorder(new EmptyBorder(20, 28, 8, 28));

        // Avatar
        form.add(buildAvatarSection());
        form.add(Box.createVerticalStrut(20));

        // Section infos
        form.add(sectionTitle("INFORMATIONS GÉNÉRALES"));
        form.add(Box.createVerticalStrut(8));
        tfFullName = UIHelper.createTextField(user.getUsername());
        tfEmail    = UIHelper.createTextField("email@exemple.com");
        tfUsername = UIHelper.createTextField(user.getUsername());
        tfUsername.setText(user.getUsername());
        addFormRow(form, "Nom complet", tfFullName);
        addFormRow(form, "Email",       tfEmail);
        addFormRow(form, "Nom d'utilisateur", tfUsername);

        form.add(Box.createVerticalStrut(16));
        form.add(sectionTitle("CHANGER LE MOT DE PASSE"));
        form.add(Box.createVerticalStrut(8));

        pfOldPassword     = UIHelper.createPasswordField("Mot de passe actuel");
        pfNewPassword     = UIHelper.createPasswordField("Nouveau mot de passe");
        pfConfirmPassword = UIHelper.createPasswordField("Confirmer le nouveau mot de passe");

        addFormRow(form, "Ancien mot de passe", pfOldPassword);
        addFormRow(form, "Nouveau mot de passe", pfNewPassword);
        addFormRow(form, "Confirmer", pfConfirmPassword);

        form.add(Box.createVerticalStrut(4));
        JLabel hint = UIHelper.createSubtitleLabel("Laissez vide pour ne pas changer le mot de passe");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(hint);

        root.add(new JScrollPane(form) {{
            setBorder(BorderFactory.createEmptyBorder());
            getViewport().setBackground(AppColors.BG_PANEL);
        }}, BorderLayout.CENTER);

        // ── Footer ─────────────────────────────────────────────────────
        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setBackground(AppColors.BG_DARK);
        footer.setBorder(new EmptyBorder(12, 28, 16, 28));

        lblStatus = new JLabel(" ");
        lblStatus.setFont(AppFonts.SMALL);

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);
        if (!isFirstLogin) {
            JButton btnCancel = UIHelper.createSecondaryButton("Annuler");
            btnCancel.addActionListener(e -> dispose());
            btnRow.add(btnCancel);
        }
        JButton btnSave = UIHelper.createPrimaryButton(
            isFirstLogin ? "Enregistrer et continuer" : "Enregistrer");
        btnSave.addActionListener(e -> saveProfile());
        btnRow.add(btnSave);

        footer.add(lblStatus, BorderLayout.WEST);
        footer.add(btnRow,    BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildAvatarSection() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);

        // Cercle avatar
        lblAvatar = new JLabel(user.getUsername().substring(0, 1).toUpperCase(), JLabel.CENTER) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color col;
                try { col = Color.decode(selectedColor); }
                catch (Exception e) { col = AppColors.ACCENT_BLUE; }
                g2.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 40));
                g2.fillOval(0, 0, getWidth(), getHeight());
                g2.setColor(col);
                g2.setStroke(new BasicStroke(2));
                g2.drawOval(1, 1, getWidth()-2, getHeight()-2);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 28));
        lblAvatar.setForeground(AppColors.TEXT_PRIMARY);
        lblAvatar.setPreferredSize(new Dimension(72, 72));
        lblAvatar.setMaximumSize(new Dimension(72, 72));
        lblAvatar.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblColorTitle = UIHelper.createSubtitleLabel("Couleur du profil");
        lblColorTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Palette couleurs
        JPanel palette = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        palette.setOpaque(false);
        palette.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (String hex : AVATAR_COLORS) {
            JButton dot = new JButton() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    Color c;
                    try { c = Color.decode(hex); } catch (Exception e) { c = Color.GRAY; }
                    g2.setColor(c);
                    g2.fillOval(2, 2, getWidth()-4, getHeight()-4);
                    if (hex.equals(selectedColor)) {
                        g2.setColor(Color.WHITE);
                        g2.setStroke(new BasicStroke(2));
                        g2.drawOval(2, 2, getWidth()-4, getHeight()-4);
                    }
                    g2.dispose();
                }
            };
            dot.setPreferredSize(new Dimension(24, 24));
            dot.setBorderPainted(false); dot.setContentAreaFilled(false);
            dot.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            dot.addActionListener(e -> { selectedColor = hex; lblAvatar.repaint(); palette.repaint(); });
            palette.add(dot);
        }

        panel.add(lblAvatar);
        panel.add(Box.createVerticalStrut(8));
        panel.add(lblColorTitle);
        panel.add(palette);
        return panel;
    }

    private JLabel sectionTitle(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        return lbl;
    }

    private void addFormRow(JPanel form, String label, JComponent field) {
        JLabel lbl = new JLabel(label);
        lbl.setFont(AppFonts.SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        lbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(lbl);
        form.add(Box.createVerticalStrut(4));
        form.add(field);
        form.add(Box.createVerticalStrut(10));
    }

    private void saveProfile() {
        String newUsername = tfUsername.getText().trim();
        String oldPass     = new String(pfOldPassword.getPassword());
        String newPass     = new String(pfNewPassword.getPassword());
        String confPass    = new String(pfConfirmPassword.getPassword());

        if (newUsername.isEmpty()) {
            showError("Le nom d'utilisateur ne peut pas être vide.");
            return;
        }

        // Vérification mot de passe si renseigné
        boolean changePass = !newPass.isEmpty();
        if (changePass) {
            if (isFirstLogin && oldPass.isEmpty()) oldPass = "admin"; // premier login
            String oldHash = AuthService.sha256(oldPass);
            if (!verifyOldPassword(oldHash)) {
                showError("Ancien mot de passe incorrect.");
                return;
            }
            if (!newPass.equals(confPass)) {
                showError("Les nouveaux mots de passe ne correspondent pas.");
                return;
            }
            if (newPass.length() < 6) {
                showError("Le mot de passe doit faire au moins 6 caractères.");
                return;
            }
        }

        // Sauvegarder en DB
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            String sql;
            PreparedStatement ps;

            if (changePass) {
                sql = "UPDATE users SET username=?, full_name=?, email=?, " +
                      "avatar_color=?, password=? WHERE id=?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, newUsername);
                ps.setString(2, tfFullName.getText().trim());
                ps.setString(3, tfEmail.getText().trim());
                ps.setString(4, selectedColor);
                ps.setString(5, AuthService.sha256(newPass));
                ps.setInt(6, user.getId());
            } else {
                sql = "UPDATE users SET username=?, full_name=?, email=?, avatar_color=? WHERE id=?";
                ps = conn.prepareStatement(sql);
                ps.setString(1, newUsername);
                ps.setString(2, tfFullName.getText().trim());
                ps.setString(3, tfEmail.getText().trim());
                ps.setString(4, selectedColor);
                ps.setInt(5, user.getId());
            }
            ps.executeUpdate();
            ps.close();
        } catch (Exception e) {
            showError("Erreur DB : " + e.getMessage());
            return;
        }

        // Marquer premier login comme terminé
        if (isFirstLogin) {
            com.securenet.config.AppConfig.getInstance().markFirstLoginDone();
        }

        showSuccess("Profil enregistré avec succès !");
        Timer t = new Timer(800, e -> dispose());
        t.setRepeats(false);
        t.start();
    }

    private boolean verifyOldPassword(String hash) {
        try {
            Connection conn = DatabaseManager.getInstance().getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM users WHERE id=? AND password=?");
            ps.setInt(1, user.getId());
            ps.setString(2, hash);
            boolean ok = ps.executeQuery().next();
            ps.close();
            return ok;
        } catch (Exception e) { return false; }
    }

    private void showError(String msg) {
        lblStatus.setText(msg);
        lblStatus.setForeground(AppColors.ACCENT_RED);
    }

    private void showSuccess(String msg) {
        lblStatus.setText(msg);
        lblStatus.setForeground(AppColors.ACCENT_GREEN);
    }
}
