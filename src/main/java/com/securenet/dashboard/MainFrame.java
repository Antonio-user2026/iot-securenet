package com.securenet.dashboard;

import com.securenet.auth.AuthService;
import com.securenet.auth.LoginFrame;
import com.securenet.auth.User;
import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.config.AppConfig;
import com.securenet.db.DBConfigDialog;
import com.securenet.devices.DevicePanel;
import com.securenet.firewall.FirewallPanel;
import com.securenet.icons.AppIcons;
import com.securenet.ids.IDSPanel;
import com.securenet.profile.ProfileDialog;
import com.securenet.siem.SIEMPanel;
import com.securenet.twin.DigitalTwinPanel;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fenêtre principale v2.
 * - Icônes vectorielles dans la sidebar
 * - Module Digital Twin
 * - Profil administrateur
 * - Config DB accessible depuis paramètres
 */
public class MainFrame extends JFrame {

    private final User      currentUser;
    private final CardLayout cardLayout  = new CardLayout();
    private final JPanel     contentPane = new JPanel(cardLayout);

    private final Map<String, NavButton> navButtons = new LinkedHashMap<>();
    private String activeModule = "dashboard";

    private JLabel lblClock;
    private JLabel lblUser;

    public MainFrame(User user) {
        super("IoT SecureNet Platform");
        this.currentUser = user;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1360, 840);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);
        setIconImage(createAppIcon());
        buildUI();
        checkFirstLogin();
    }

    private Image createAppIcon() {
        return AppIcons.shield(32).getImage();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Construction UI
    // ══════════════════════════════════════════════════════════════════════

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(AppColors.BG_DARK);
        setContentPane(root);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildContent(), BorderLayout.CENTER);

        switchTo("dashboard");

        Timer clock = new Timer(1000, e -> updateClock());
        clock.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Sidebar
    // ══════════════════════════════════════════════════════════════════════

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(AppColors.BG_SIDEBAR);
        sidebar.setPreferredSize(new Dimension(230, 0));

        sidebar.add(buildLogo());
        sidebar.add(UIHelper.createSeparator());
        sidebar.add(Box.createVerticalStrut(8));

        // ── Supervision ─────────────────────────────────────────────────
        addSection(sidebar, "SUPERVISION");
        addNav(sidebar, "dashboard", AppIcons.dashboard(18), "Dashboard");
        addNav(sidebar, "devices",   AppIcons.devices(18),   "Appareils IoT");
        addNav(sidebar, "twin",      AppIcons.twin(18),      "Digital Twin");

        // ── Sécurité ────────────────────────────────────────────────────
        addSection(sidebar, "SÉCURITÉ");
        addNav(sidebar, "ids",      AppIcons.ids(18),      "Détection IDS");
        addNav(sidebar, "firewall", AppIcons.firewall(18), "Pare-feu");
        addNav(sidebar, "siem",     AppIcons.siem(18),     "SIEM & Alertes");

        sidebar.add(Box.createVerticalGlue());
        sidebar.add(UIHelper.createSeparator());
        sidebar.add(buildSidebarFooter());

        return sidebar;
    }

    private JPanel buildLogo() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(22, 0, 18, 0));

        JLabel icon = new JLabel(AppIcons.shield(32));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel title = new JLabel("SecureNet");
        title.setFont(AppFonts.TITLE_MEDIUM);
        title.setForeground(AppColors.TEXT_PRIMARY);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel dbMode = new JLabel(
            AppConfig.getInstance().getDbMode().toUpperCase() + " · v1.0");
        dbMode.setFont(AppFonts.SMALL);
        dbMode.setForeground(AppColors.TEXT_MUTED);
        dbMode.setAlignmentX(Component.CENTER_ALIGNMENT);

        p.add(icon); p.add(Box.createVerticalStrut(7));
        p.add(title); p.add(Box.createVerticalStrut(2));
        p.add(dbMode);
        return p;
    }

    private void addSection(JPanel sidebar, String title) {
        JLabel lbl = new JLabel(title);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(AppColors.TEXT_MUTED);
        lbl.setBorder(new EmptyBorder(14, 20, 4, 0));
        sidebar.add(lbl);
    }

    private void addNav(JPanel sidebar, String id, ImageIcon icon, String label) {
        NavButton btn = new NavButton(icon, label);
        btn.addActionListener(e -> switchTo(id));
        navButtons.put(id, btn);
        sidebar.add(btn);
    }

    private JPanel buildSidebarFooter() {
        JPanel footer = new JPanel(new BorderLayout(6, 0));
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(10, 14, 16, 14));

        // Avatar coloré
        JPanel avatar = buildAvatarBubble();

        // Infos user
        lblUser = new JLabel("<html><b style='color:#DCE6F5'>" +
            currentUser.getUsername() +
            "</b><br><small style='color:#8096B4'>" +
            currentUser.getRole() + "</small></html>");
        lblUser.setFont(AppFonts.SMALL);

        // Boutons actions
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        actions.setOpaque(false);

        JButton btnProfile  = iconBtn(AppIcons.user(14),    "Profil");
        JButton btnDB       = iconBtn(AppIcons.network(14), "Base de données");
        JButton btnLogout   = iconBtn(AppIcons.logout(14),  "Déconnexion");

        btnProfile.addActionListener(e -> openProfile());
        btnDB.addActionListener(e      -> openDBConfig());
        btnLogout.addActionListener(e  -> doLogout());

        actions.add(btnProfile);
        actions.add(btnDB);
        actions.add(btnLogout);

        footer.add(avatar,  BorderLayout.WEST);
        footer.add(lblUser, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.EAST);
        return footer;
    }

    private JPanel buildAvatarBubble() {
        JPanel bubble = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D)g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0, 150, 255, 50));
                g2.fillOval(0, 0, 34, 34);
                g2.setColor(AppColors.ACCENT_BLUE);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawOval(1, 1, 32, 32);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 14));
                g2.setColor(AppColors.TEXT_PRIMARY);
                String init = currentUser.getUsername().substring(0, 1).toUpperCase();
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(init, (34 - fm.stringWidth(init))/2,
                              (34 + fm.getAscent() - fm.getDescent())/2);
                g2.dispose();
            }
        };
        bubble.setPreferredSize(new Dimension(34, 34));
        bubble.setOpaque(false);
        return bubble;
    }

    private JButton iconBtn(ImageIcon icon, String tooltip) {
        JButton b = new JButton(icon);
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(28, 28));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Contenu
    // ══════════════════════════════════════════════════════════════════════

    private JPanel buildContent() {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(AppColors.BG_PANEL);
        wrapper.add(buildTopBar(), BorderLayout.NORTH);

        contentPane.setBackground(AppColors.BG_PANEL);
        contentPane.add(new DashboardPanel(currentUser), "dashboard");
        contentPane.add(new DevicePanel(),               "devices");
        contentPane.add(new DigitalTwinPanel(),          "twin");
        contentPane.add(new IDSPanel(),                  "ids");
        contentPane.add(new FirewallPanel(),             "firewall");
        contentPane.add(new SIEMPanel(),                 "siem");

        wrapper.add(contentPane, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(AppColors.BG_DARK);
        bar.setBorder(new EmptyBorder(8, 20, 8, 20));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(UIHelper.createStatusBadge("● SYSTÈME ACTIF",   AppColors.STATUS_OK));
        left.add(UIHelper.createStatusBadge("ARCH LINUX",        AppColors.ACCENT_BLUE));
        left.add(UIHelper.createStatusBadge(
            AppConfig.getInstance().getDbMode().toUpperCase() + " DB",
            AppColors.ACCENT_PURPLE));

        lblClock = new JLabel();
        lblClock.setFont(AppFonts.MONO);
        lblClock.setForeground(AppColors.TEXT_SECONDARY);
        updateClock();

        bar.add(left,     BorderLayout.WEST);
        bar.add(lblClock, BorderLayout.EAST);
        return bar;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Navigation
    // ══════════════════════════════════════════════════════════════════════

    public void switchTo(String id) {
        if (navButtons.containsKey(activeModule))
            navButtons.get(activeModule).setActive(false);
        activeModule = id;
        if (navButtons.containsKey(id))
            navButtons.get(id).setActive(true);
        cardLayout.show(contentPane, id);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Actions
    // ══════════════════════════════════════════════════════════════════════

    private void checkFirstLogin() {
        if (AppConfig.getInstance().isFirstLogin()
                && "admin".equals(currentUser.getUsername())) {
            SwingUtilities.invokeLater(() -> {
                ProfileDialog dlg = new ProfileDialog(this, currentUser, true);
                dlg.setVisible(true);
            });
        }
    }

    private void openProfile() {
        ProfileDialog dlg = new ProfileDialog(this, currentUser, false);
        dlg.setVisible(true);
    }

    private void openDBConfig() {
        DBConfigDialog dlg = new DBConfigDialog(this);
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            JOptionPane.showMessageDialog(this,
                "Configuration enregistrée.\nRedémarrez l'application pour appliquer les changements.",
                "Base de données", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void doLogout() {
        int c = JOptionPane.showConfirmDialog(this,
            "Voulez-vous vous déconnecter ?", "Déconnexion",
            JOptionPane.YES_NO_OPTION);
        if (c == JOptionPane.YES_OPTION) {
            AuthService.getInstance().logout();
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        }
    }

    private void updateClock() {
        lblClock.setText(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}
