package com.securenet.attack;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.common.UIHelper;
import com.securenet.icons.AppIcons;
import com.securenet.twin.TopologyNode;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Fenêtre de sélection et de suivi d'une simulation d'attaque.
 * Lancée depuis le Digital Twin lorsqu'un nœud est sélectionné.
 */
public class AttackSimulationDialog extends JDialog {

    private final TopologyNode      target;
    private final AttackSimulator   simulator = new AttackSimulator();

    // Composants
    private JList<AttackSimulator.AttackType> attackList;
    private JTextArea                         taDescription;
    private JProgressBar                      progressBar;
    private JTextArea                         taLog;
    private JLabel                            lblPhase;
    private JLabel                            lblEventCount;
    private JButton                           btnStart, btnStop, btnClose;
    private JPanel                            progressCard;

    private boolean running = false;

    public AttackSimulationDialog(Frame parent, TopologyNode target) {
        super(parent, "Simulation d'attaque — " + target.name, false);
        this.target = target;
        setSize(680, 640);
        setLocationRelativeTo(parent);
        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(AppColors.BG_PANEL);
        setContentPane(root);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);
    }

    // ── Header ─────────────────────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout(12, 0));
        p.setBackground(AppColors.BG_DARK);
        p.setBorder(new EmptyBorder(14, 20, 14, 20));

        JLabel icon = new JLabel(AppIcons.alert(28));

        JPanel info = new JPanel();
        info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
        info.setOpaque(false);
        JLabel lTitle = UIHelper.createTitleLabel("Simulateur d'attaques réseau");
        JLabel lSub   = UIHelper.createSubtitleLabel(
            "Cible : " + target.name + "  (" + target.ip + ")");

        // Badge avertissement
        JLabel badge = UIHelper.createStatusBadge(
            "⚠ MODE SIMULATION — Aucun trafic réel envoyé", AppColors.ACCENT_ORANGE);

        info.add(lTitle);
        info.add(Box.createVerticalStrut(3));
        info.add(lSub);
        info.add(Box.createVerticalStrut(5));
        info.add(badge);

        p.add(icon, BorderLayout.WEST);
        p.add(info, BorderLayout.CENTER);
        return p;
    }

    // ── Contenu central ────────────────────────────────────────────────────

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setBackground(AppColors.BG_PANEL);
        center.setBorder(new EmptyBorder(14, 16, 8, 16));

        // ── Partie gauche : liste des attaques ─────────────────────────────
        JPanel leftPanel = UIHelper.createCardPanel(new BorderLayout(0, 8));
        leftPanel.add(UIHelper.createTitleLabel("Choisir une attaque"), BorderLayout.NORTH);

        DefaultListModel<AttackSimulator.AttackType> model = new DefaultListModel<>();
        for (AttackSimulator.AttackType at : AttackSimulator.AttackType.values()) {
            model.addElement(at);
        }

        attackList = new JList<>(model);
        attackList.setBackground(AppColors.BG_DARK);
        attackList.setForeground(AppColors.TEXT_PRIMARY);
        attackList.setFont(AppFonts.BODY);
        attackList.setFixedCellHeight(38);
        attackList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        attackList.setSelectionBackground(new Color(255, 80, 80, 60));
        attackList.setSelectionForeground(AppColors.TEXT_PRIMARY);

        // Renderer personnalisé avec badge sévérité
        attackList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                AttackSimulator.AttackType at = (AttackSimulator.AttackType) value;
                setBackground(isSelected
                    ? new Color(255, 80, 80, 50)
                    : (index % 2 == 0 ? AppColors.BG_DARK : new Color(28, 34, 45)));

                Color sevColor = at.severity == com.securenet.events.SecurityEvent.Severity.CRITICAL
                    ? AppColors.ACCENT_RED : AppColors.ACCENT_ORANGE;
                String sevLabel = at.severity.name();
                setText("<html><b style='color:#DCE6F5'>" + at.label +
                    "</b>  <font color='" + toHex(sevColor) + "'>[" + sevLabel + "]</font>" +
                    "<br><font color='#8096B4' size='1'>" + at.description + "</font></html>");
                setBorder(new EmptyBorder(4, 10, 4, 10));
                setOpaque(true);
                return this;
            }
        });

        attackList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateDescription();
        });

        // NE PAS appeler setSelectedIndex ici : taDescription n'est pas encore créé
        leftPanel.add(UIHelper.createScrollPane(attackList), BorderLayout.CENTER);
        leftPanel.setPreferredSize(new Dimension(280, 0));

        // ── Partie droite : détails + logs ─────────────────────────────────
        JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
        rightPanel.setOpaque(false);

        // Description de l'attaque sélectionnée
        JPanel descCard = UIHelper.createCardPanel(new BorderLayout(0, 6));
        JLabel descTitle = UIHelper.createTitleLabel("Détails de l'attaque");
        descTitle.setIcon(AppIcons.ids(14));
        descCard.add(descTitle, BorderLayout.NORTH);

        taDescription = new JTextArea(4, 1);
        taDescription.setFont(AppFonts.SMALL);
        taDescription.setBackground(AppColors.BG_DARK);
        taDescription.setForeground(AppColors.TEXT_SECONDARY);
        taDescription.setEditable(false);
        taDescription.setLineWrap(true);
        taDescription.setWrapStyleWord(true);
        taDescription.setOpaque(true);
        descCard.add(UIHelper.createScrollPane(taDescription), BorderLayout.CENTER);
        descCard.setPreferredSize(new Dimension(0, 130));

        // Sélectionner le premier élément MAINTENANT que taDescription existe
        attackList.setSelectedIndex(0);

        // Progression
        progressCard = UIHelper.createCardPanel(new BorderLayout(0, 6));
        progressCard.add(buildProgressHeader(), BorderLayout.NORTH);

        progressBar = new JProgressBar(0, 100);
        progressBar.setBackground(AppColors.BG_DARK);
        progressBar.setForeground(AppColors.ACCENT_RED);
        progressBar.setStringPainted(false);
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(0, 6));
        progressCard.add(progressBar, BorderLayout.CENTER);

        // Log temps réel
        JPanel logCard = UIHelper.createCardPanel(new BorderLayout(0, 6));
        JLabel logTitle = UIHelper.createTitleLabel("Journal d'attaque");
        logTitle.setIcon(AppIcons.siem(14));
        logCard.add(logTitle, BorderLayout.NORTH);

        taLog = new JTextArea();
        taLog.setFont(new Font("Monospaced", Font.PLAIN, 11));
        taLog.setBackground(new Color(8, 12, 18));
        taLog.setForeground(AppColors.ACCENT_RED);
        taLog.setEditable(false);
        taLog.setLineWrap(true);
        taLog.setText("[ATTK] Simulateur prêt. Sélectionnez une attaque et cliquez Lancer.\n");
        logCard.add(UIHelper.createScrollPane(taLog), BorderLayout.CENTER);

        rightPanel.add(descCard,    BorderLayout.NORTH);
        rightPanel.add(progressCard, BorderLayout.CENTER);
        rightPanel.add(logCard,      BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(0, 0));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        split.setDividerLocation(280);
        split.setBorder(null);
        split.setBackground(AppColors.BG_PANEL);
        split.setResizeWeight(0.35);

        center.add(split, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildProgressHeader() {
        JPanel p = new JPanel(new BorderLayout(6, 0));
        p.setOpaque(false);

        lblPhase = new JLabel("En attente...");
        lblPhase.setFont(AppFonts.SMALL);
        lblPhase.setForeground(AppColors.TEXT_SECONDARY);

        lblEventCount = new JLabel("0 événements");
        lblEventCount.setFont(AppFonts.SMALL);
        lblEventCount.setForeground(AppColors.ACCENT_RED);

        p.add(lblPhase,      BorderLayout.CENTER);
        p.add(lblEventCount, BorderLayout.EAST);
        return p;
    }

    // ── Footer ─────────────────────────────────────────────────────────────

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout(0, 0));
        footer.setBackground(AppColors.BG_DARK);
        footer.setBorder(new EmptyBorder(10, 20, 12, 20));

        JLabel hint = UIHelper.createSubtitleLabel(
            "Les alertes générées sont visibles dans les modules IDS et SIEM");
        hint.setIcon(AppIcons.shield(12));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setOpaque(false);

        btnStart = UIHelper.createDangerButton("Lancer l'attaque", AppIcons.play(14));
        btnStop  = UIHelper.createSecondaryButton("Arrêter",        AppIcons.pause(14));
        btnClose = UIHelper.createSecondaryButton("Fermer");

        btnStop.setEnabled(false);

        btnStart.addActionListener(e -> startAttack());
        btnStop.addActionListener(e  -> stopAttack());
        btnClose.addActionListener(e -> {
            simulator.cancel();
            dispose();
        });

        btnRow.add(btnStart); btnRow.add(btnStop); btnRow.add(btnClose);

        footer.add(hint,   BorderLayout.WEST);
        footer.add(btnRow, BorderLayout.EAST);
        return footer;
    }

    // ── Logique ────────────────────────────────────────────────────────────

    private void updateDescription() {
        if (taDescription == null) return;  // guard : appelé avant init complète
        AttackSimulator.AttackType selected = attackList.getSelectedValue();
        if (selected == null) return;
        taDescription.setText(
            "Attaque : " + selected.label + "\n" +
            "Sévérité : " + selected.severity.name() + "\n" +
            "Durée estimée : " + (selected.durationMs / 1000) + " secondes\n" +
            "Événements générés : " + selected.eventCount + "\n\n" +
            "Description : " + selected.description + "\n\n" +
            (selected.severity == com.securenet.events.SecurityEvent.Severity.CRITICAL
                ? "⚠ Cette attaque marquera l'appareil comme BLOQUÉ si elle réussit.\n"
                : "ℹ Cette attaque génère des alertes WARNING dans l'IDS.\n")
        );
    }

    private void startAttack() {
        AttackSimulator.AttackType selected = attackList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Sélectionnez une attaque.");
            return;
        }

        running = true;
        btnStart.setEnabled(false);
        btnStop.setEnabled(true);
        attackList.setEnabled(false);
        progressBar.setValue(0);
        taLog.setText("");

        log("[ATTK] Cible       : " + target.name + " (" + target.ip + ")");
        log("[ATTK] Type        : " + selected.label);
        log("[ATTK] Sévérité    : " + selected.severity);
        log("[ATTK] Démarrage...\n");

        simulator.setOnProgress(state -> SwingUtilities.invokeLater(() -> {
            progressBar.setValue(state.progress);
            lblPhase.setText(state.phase);
            lblEventCount.setText(state.eventsGenerated + " / " + selected.eventCount + " événements");
            log("[>>] " + state.phase);
        }));

        simulator.setOnFinished(state -> SwingUtilities.invokeLater(() -> {
            running = false;
            btnStart.setEnabled(true);
            btnStop.setEnabled(false);
            attackList.setEnabled(true);
            progressBar.setValue(100);

            if (state.finished && !state.phase.contains("annulée")) {
                lblPhase.setText("Simulation terminée.");
                log("\n[OK] Simulation terminée. " + state.eventsGenerated + " événements générés.");
                if (selected.severity == com.securenet.events.SecurityEvent.Severity.CRITICAL) {
                    log("[!]  Appareil " + target.ip + " marqué comme BLOQUÉ.");
                }
            } else {
                lblPhase.setText("Simulation annulée.");
                log("[STOP] Simulation arrêtée par l'utilisateur.");
            }
        }));

        simulator.simulate(selected, target);
    }

    private void stopAttack() {
        simulator.cancel();
        btnStart.setEnabled(true);
        btnStop.setEnabled(false);
        attackList.setEnabled(true);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            taLog.append(msg + "\n");
            taLog.setCaretPosition(taLog.getDocument().getLength());
        });
    }

    private String toHex(Color c) {
        return String.format("#%02X%02X%02X", c.getRed(), c.getGreen(), c.getBlue());
    }

    /** Retourne le nombre d'événements générés par la dernière simulation. */
    public int getGeneratedEventCount() {
        return running ? 0 : (int) taLog.getText().lines()
            .filter(l -> l.startsWith("[>>]")).count();
    }
}
