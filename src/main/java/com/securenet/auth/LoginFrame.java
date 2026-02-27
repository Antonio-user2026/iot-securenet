package com.securenet.auth;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.dashboard.MainFrame;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;

/**
 * Fenêtre de connexion.
 */
public class LoginFrame extends JFrame {

    private JTextField     tfUsername;
    private JPasswordField tfPassword;
    private JLabel         lblError;
    private JButton        btnLogin;

    public LoginFrame() {
        super("IoT SecureNet — Connexion");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(440, 520);
        setLocationRelativeTo(null);
        setResizable(false);
        buildUI();
    }

    private void buildUI() {
        // Panneau principal
        JPanel root = new JPanel(new GridBagLayout());
        root.setBackground(AppColors.BG_DARK);
        setContentPane(root);

        // Carte centrale
        JPanel card = UIHelper.createCardPanel(new BorderLayout(0, 16));
        card.setPreferredSize(new Dimension(360, 420));
        card.setBorder(new EmptyBorder(36, 36, 36, 36));

        // ── Header ────────────────────────────────────────────────────────
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);

        JLabel lblIcon = new JLabel("🛡", JLabel.CENTER);
        lblIcon.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
        lblIcon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblTitle = new JLabel("IoT SecureNet", JLabel.CENTER);
        lblTitle.setFont(AppFonts.TITLE_LARGE);
        lblTitle.setForeground(AppColors.TEXT_PRIMARY);
        lblTitle.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel lblSub = new JLabel("Plateforme de supervision sécurisée", JLabel.CENTER);
        lblSub.setFont(AppFonts.SMALL);
        lblSub.setForeground(AppColors.TEXT_SECONDARY);
        lblSub.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(lblIcon);
        header.add(Box.createVerticalStrut(8));
        header.add(lblTitle);
        header.add(Box.createVerticalStrut(4));
        header.add(lblSub);

        // ── Formulaire ────────────────────────────────────────────────────
        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);

        tfUsername = UIHelper.createTextField("Nom d'utilisateur");
        tfUsername.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        tfPassword = UIHelper.createPasswordField("Mot de passe");
        tfPassword.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        lblError = new JLabel(" ");
        lblError.setFont(AppFonts.SMALL);
        lblError.setForeground(AppColors.ACCENT_RED);
        lblError.setAlignmentX(Component.CENTER_ALIGNMENT);

        btnLogin = UIHelper.createPrimaryButton("Se connecter");
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setAlignmentX(Component.CENTER_ALIGNMENT);

        form.add(createFieldLabel("Utilisateur"));
        form.add(Box.createVerticalStrut(4));
        form.add(tfUsername);
        form.add(Box.createVerticalStrut(14));
        form.add(createFieldLabel("Mot de passe"));
        form.add(Box.createVerticalStrut(4));
        form.add(tfPassword);
        form.add(Box.createVerticalStrut(8));
        form.add(lblError);
        form.add(Box.createVerticalStrut(8));
        form.add(btnLogin);

        // ── Hint ─────────────────────────────────────────────────────────
        JLabel lblHint = new JLabel("Compte par défaut : admin / admin", JLabel.CENTER);
        lblHint.setFont(AppFonts.SMALL);
        lblHint.setForeground(AppColors.TEXT_MUTED);

        card.add(header, BorderLayout.NORTH);
        card.add(form,   BorderLayout.CENTER);
        card.add(lblHint, BorderLayout.SOUTH);
        root.add(card);

        // ── Actions ───────────────────────────────────────────────────────
        btnLogin.addActionListener(e -> doLogin());
        tfPassword.addActionListener(e -> doLogin());
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(AppFonts.SMALL);
        lbl.setForeground(AppColors.TEXT_SECONDARY);
        return lbl;
    }

    private void doLogin() {
        String username = tfUsername.getText().trim();
        String password = new String(tfPassword.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            lblError.setText("Veuillez remplir tous les champs.");
            return;
        }

        btnLogin.setEnabled(false);
        btnLogin.setText("Connexion...");

        SwingWorker<User, Void> worker = new SwingWorker<>() {
            @Override
            protected User doInBackground() {
                return AuthService.getInstance().login(username, password);
            }

            @Override
            protected void done() {
                try {
                    User user = get();
                    if (user != null) {
                        dispose();
                        SwingUtilities.invokeLater(() -> {
                            MainFrame mainFrame = new MainFrame(user);
                            mainFrame.setVisible(true);
                        });
                    } else {
                        lblError.setText("Identifiants incorrects.");
                        btnLogin.setEnabled(true);
                        btnLogin.setText("Se connecter");
                    }
                } catch (Exception ex) {
                    lblError.setText("Erreur de connexion.");
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Se connecter");
                }
            }
        };
        worker.execute();
    }
}
