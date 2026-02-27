package com.securenet;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/**
 * Écran de démarrage animé de IoT SecureNet Platform.
 *
 * Effets visuels :
 *  - Fond sombre avec grille hexagonale animée (réseau IoT)
 *  - Logo central avec animation de pulse
 *  - Particules flottantes (nœuds réseau)
 *  - Connexions animées entre particules
 *  - Barre de progression avec étapes de chargement
 *  - Texte de statut en temps réel
 *  - Fade-in / Fade-out
 */
public class SplashScreen extends JWindow {

    // ── Dimensions ─────────────────────────────────────────────────────────
    private static final int W = 720;
    private static final int H = 440;

    // ── Composants UI ──────────────────────────────────────────────────────
    private JProgressBar progressBar;
    private JLabel       lblStatus;
    private JLabel       lblVersion;
    private Timer        animTimer;
    private Timer        progressTimer;

    // ── Animation ──────────────────────────────────────────────────────────
    private float        animPhase    = 0f;
    private float        pulsePhase   = 0f;
    private float        fadeAlpha    = 0f;      // 0=transparent → 1=opaque
    private boolean      fadingIn     = true;
    private boolean      fadingOut    = false;
    private final List<Particle>  particles = new ArrayList<>();
    private final Random          rnd       = new Random();

    // ── Chargement ─────────────────────────────────────────────────────────
    private int          progressValue = 0;
    private int          stepIndex     = 0;
    private Runnable     onComplete;

    private static final String[][] STEPS = {
        {"5",  "Initialisation du moteur graphique..."},
        {"12", "Chargement du thème FlatLaf Dark..."},
        {"20", "Connexion à la base de données..."},
        {"30", "Chargement des modules IoT..."},
        {"40", "Initialisation du scanner réseau..."},
        {"52", "Démarrage du moteur IDS/SIEM..."},
        {"63", "Chargement du Digital Twin..."},
        {"74", "Activation du pare-feu virtuel..."},
        {"84", "Synchronisation des appareils..."},
        {"92", "Vérification de la sécurité..."},
        {"98", "Prêt."},
        {"100","Bienvenue dans IoT SecureNet Platform"}
    };

    // ══════════════════════════════════════════════════════════════════════

    public SplashScreen() {
        super();
        setSize(W, H);
        setLocationRelativeTo(null);
        setBackground(new Color(0, 0, 0, 0));
        initParticles();
        buildUI();
    }

    // ── Contenu ────────────────────────────────────────────────────────────

    private void buildUI() {
        JPanel canvas = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                paintSplash((Graphics2D) g);
            }
        };
        canvas.setOpaque(false);
        canvas.setLayout(new BorderLayout());

        // Zone bas : barre + statut
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.setOpaque(false);
        bottom.setBorder(BorderFactory.createEmptyBorder(0, 48, 28, 48));

        // Barre de progression custom
        progressBar = new JProgressBar(0, 100) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                // Track
                g2.setColor(new Color(255, 255, 255, 20));
                g2.fillRoundRect(0, 0, w, h, h, h);
                // Fill
                int filled = (int)(w * getValue() / 100.0);
                if (filled > 0) {
                    // Gradient bleu → vert
                    GradientPaint gp = new GradientPaint(
                        0, 0, new Color(0, 120, 255),
                        filled, 0, new Color(0, 220, 130));
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, filled, h, h, h);
                    // Brillance
                    g2.setColor(new Color(255, 255, 255, 40));
                    g2.fillRoundRect(0, 0, filled, h / 2, h / 2, h / 2);
                }
                g2.dispose();
            }
        };
        progressBar.setPreferredSize(new Dimension(0, 5));
        progressBar.setOpaque(false);
        progressBar.setBorderPainted(false);
        progressBar.setValue(0);

        lblStatus = new JLabel("Démarrage...");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblStatus.setForeground(new Color(120, 160, 210));
        lblStatus.setHorizontalAlignment(JLabel.LEFT);

        lblVersion = new JLabel("v3.0  ·  © 2025 IoT SecureNet");
        lblVersion.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblVersion.setForeground(new Color(60, 80, 110));
        lblVersion.setHorizontalAlignment(JLabel.RIGHT);

        JPanel statusRow = new JPanel(new BorderLayout());
        statusRow.setOpaque(false);
        statusRow.add(lblStatus,  BorderLayout.WEST);
        statusRow.add(lblVersion, BorderLayout.EAST);

        bottom.add(progressBar, BorderLayout.CENTER);
        bottom.add(statusRow,   BorderLayout.SOUTH);

        canvas.add(bottom, BorderLayout.SOUTH);
        setContentPane(canvas);
    }

    // ── Rendu principal ────────────────────────────────────────────────────

    private void paintSplash(Graphics2D g2) {
        int w = getWidth(), h = getHeight();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Fond avec dégradé radial sombre
        drawBackground(g2, w, h);

        // Grille hexagonale
        drawHexGrid(g2, w, h);

        // Connexions entre particules
        drawConnections(g2);

        // Particules (nœuds réseau)
        drawParticles(g2);

        // Logo central
        drawLogo(g2, w, h);

        // Fade overlay
        if (fadeAlpha < 1f) {
            float inv = 1f - fadeAlpha;
            g2.setColor(new Color(0, 0, 0, (int)(inv * 255)));
            g2.fillRect(0, 0, w, h);
        }
    }

    private void drawBackground(Graphics2D g2, int w, int h) {
        // Fond dégradé radial centré
        float cx = w / 2f, cy = h / 2f;
        float[] fractions = {0f, 0.5f, 1f};
        Color[] colors = {
            new Color(15, 25, 45),
            new Color(12, 18, 32),
            new Color(8, 12, 20)
        };
        RadialGradientPaint bg = new RadialGradientPaint(
            cx, cy, Math.max(w, h) * 0.7f, fractions, colors);
        g2.setPaint(bg);
        g2.fillRect(0, 0, w, h);

        // Effet "glow" derrière le logo
        float pulse = 0.7f + 0.3f * (float) Math.sin(pulsePhase);
        RadialGradientPaint glow = new RadialGradientPaint(
            cx, cy - 30, 180 * pulse,
            new float[]{0f, 1f},
            new Color[]{new Color(0, 80, 200, 40), new Color(0, 0, 0, 0)});
        g2.setPaint(glow);
        g2.fillRect(0, 0, w, h);
    }

    private void drawHexGrid(Graphics2D g2, int w, int h) {
        float size = 28f;
        float hexW  = size * 2;
        float hexH  = (float)(Math.sqrt(3) * size);
        float offset = (animPhase * 15) % (hexW * 1.5f);

        g2.setStroke(new BasicStroke(0.5f));
        g2.setColor(new Color(30, 60, 120, 18));

        for (float y = -hexH; y < h + hexH; y += hexH) {
            for (float x = -hexW - offset; x < w + hexW; x += hexW * 1.5f) {
                boolean shift = ((int)((y + hexH) / hexH) % 2) == 1;
                float px = x + (shift ? hexW * 0.75f : 0);
                drawHex(g2, px, y, size);
            }
        }
    }

    private void drawHex(Graphics2D g2, float cx, float cy, float size) {
        Path2D hex = new Path2D.Float();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180 * (60 * i - 30);
            float x = cx + size * (float) Math.cos(angle);
            float y = cy + size * (float) Math.sin(angle);
            if (i == 0) hex.moveTo(x, y); else hex.lineTo(x, y);
        }
        hex.closePath();
        g2.draw(hex);
    }

    private void drawConnections(Graphics2D g2) {
        float maxDist = 140f;
        for (int i = 0; i < particles.size(); i++) {
            Particle a = particles.get(i);
            for (int j = i + 1; j < particles.size(); j++) {
                Particle b = particles.get(j);
                float dx = a.x - b.x, dy = a.y - b.y;
                float dist = (float) Math.sqrt(dx * dx + dy * dy);
                if (dist < maxDist) {
                    float alpha = (1f - dist / maxDist) * 0.4f;
                    // Particules animées sur le lien
                    float t = (animPhase * 0.8f + i * 0.3f) % 1f;
                    float px = a.x + (b.x - a.x) * t;
                    float py = a.y + (b.y - a.y) * t;

                    g2.setColor(new Color(0, 150, 255, (int)(alpha * 120)));
                    g2.setStroke(new BasicStroke(0.8f));
                    g2.drawLine((int)a.x, (int)a.y, (int)b.x, (int)b.y);

                    // Point animé sur le lien
                    g2.setColor(new Color(0, 220, 130, (int)(alpha * 200)));
                    g2.fillOval((int)(px - 2), (int)(py - 2), 4, 4);
                }
            }
        }
    }

    private void drawParticles(Graphics2D g2) {
        for (Particle p : particles) {
            float pulse = 0.7f + 0.3f * (float) Math.sin(pulsePhase * p.phaseMult);
            int r = (int)(p.radius * pulse);
            // Halo
            g2.setColor(new Color(p.color.getRed(), p.color.getGreen(),
                p.color.getBlue(), 30));
            g2.fillOval((int)(p.x - r * 2), (int)(p.y - r * 2), r * 4, r * 4);
            // Corps
            g2.setColor(p.color);
            g2.fillOval((int)(p.x - r), (int)(p.y - r), r * 2, r * 2);
            // Centre blanc
            g2.setColor(new Color(255, 255, 255, 180));
            g2.fillOval((int)(p.x - r / 2), (int)(p.y - r / 2), r, r);
        }
    }

    private void drawLogo(Graphics2D g2, int w, int h) {
        float cx = w / 2f, cy = h / 2f - 40f;
        float pulse = 1f + 0.04f * (float) Math.sin(pulsePhase * 1.5f);

        // ── Anneau extérieur rotatif ──
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0, new float[]{8, 6}, (animPhase * 30) % 14f));  // 14 = période (8+6)
        g2.setColor(new Color(0, 150, 255, 60));
        float r1 = 72 * pulse;
        g2.draw(new Ellipse2D.Float(cx - r1, cy - r1, r1 * 2, r1 * 2));

        // ── Anneau moyen ──
        g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
            0, new float[]{4, 8}, 12f - ((animPhase * 20) % 12f)));  // rotation inverse, 12 = période (4+8)
        g2.setColor(new Color(0, 220, 130, 50));
        float r2 = 56 * pulse;
        g2.draw(new Ellipse2D.Float(cx - r2, cy - r2, r2 * 2, r2 * 2));

        // ── Cercle de fond du logo ──
        float r0 = 44 * pulse;
        RadialGradientPaint lgp = new RadialGradientPaint(
            cx, cy, r0,
            new float[]{0f, 0.6f, 1f},
            new Color[]{new Color(0, 100, 220), new Color(0, 60, 160), new Color(0, 40, 100)});
        g2.setPaint(lgp);
        g2.fill(new Ellipse2D.Float(cx - r0, cy - r0, r0 * 2, r0 * 2));

        // Bordure cercle
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0, 180, 255, 180));
        g2.draw(new Ellipse2D.Float(cx - r0, cy - r0, r0 * 2, r0 * 2));

        // ── Icône "bouclier IoT" (chemin vectoriel) ──
        drawShieldIcon(g2, cx, cy, 22 * pulse);

        // ── Titre ──
        g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
        g2.setColor(new Color(220, 235, 255));
        String title = "IoT SecureNet";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, cx - fm.stringWidth(title) / 2f, cy + 72);

        // ── Sous-titre ──
        g2.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        g2.setColor(new Color(100, 140, 200));
        String sub = "PLATFORM  ·  Security Monitoring & Digital Twin";
        FontMetrics fmSub = g2.getFontMetrics();
        g2.drawString(sub, cx - fmSub.stringWidth(sub) / 2f, cy + 94);

        // ── Points de connexion autour du logo ──
        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8 + animPhase * 0.5;
            float pr = r1 + 6;
            float px = cx + pr * (float) Math.cos(angle);
            float py = cy + pr * (float) Math.sin(angle);
            float dotR = i % 2 == 0 ? 3 : 2;
            Color dc = i % 2 == 0 ? new Color(0, 150, 255) : new Color(0, 220, 130);
            g2.setColor(dc);
            g2.fill(new Ellipse2D.Float(px - dotR, py - dotR, dotR * 2, dotR * 2));
        }
    }

    private void drawShieldIcon(Graphics2D g2, float cx, float cy, float size) {
        // Bouclier stylisé + WiFi
        Path2D shield = new Path2D.Float();
        shield.moveTo(cx, cy - size);
        shield.curveTo(cx + size * 0.8f, cy - size * 0.8f,
                       cx + size,        cy + size * 0.2f,
                       cx,               cy + size);
        shield.curveTo(cx - size,        cy + size * 0.2f,
                       cx - size * 0.8f, cy - size * 0.8f,
                       cx,               cy - size);

        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.setColor(new Color(100, 200, 255, 220));
        g2.draw(shield);

        // Arcs WiFi au centre du bouclier
        g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float p = (float) Math.sin(pulsePhase * 2) * 0.3f + 0.7f;
        for (int a = 0; a < 3; a++) {
            float ar = (a + 1) * size * 0.25f;
            int startA = 200, sweepA = 140;
            g2.setColor(new Color(100, 200, 255, (int)(p * (180 - a * 50))));
            g2.draw(new Arc2D.Float(cx - ar, cy - ar * 0.6f,
                ar * 2, ar * 2, startA, sweepA, Arc2D.OPEN));
        }
    }

    // ── Particules ─────────────────────────────────────────────────────────

    private static class Particle {
        float x, y, vx, vy, radius, phaseMult;
        Color color;
        Particle(float x, float y, float vx, float vy, float r, Color c, float pm) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy;
            this.radius = r; this.color = c; this.phaseMult = pm;
        }
    }

    private void initParticles() {
        Color[] colors = {
            new Color(0, 150, 255),
            new Color(0, 220, 130),
            new Color(150, 100, 255),
            new Color(255, 160, 0)
        };
        for (int i = 0; i < 22; i++) {
            particles.add(new Particle(
                rnd.nextFloat() * W,
                rnd.nextFloat() * H,
                (rnd.nextFloat() - 0.5f) * 0.4f,
                (rnd.nextFloat() - 0.5f) * 0.4f,
                2 + rnd.nextFloat() * 3,
                colors[rnd.nextInt(colors.length)],
                0.5f + rnd.nextFloat() * 1.5f
            ));
        }
    }

    private void updateParticles() {
        for (Particle p : particles) {
            p.x += p.vx;
            p.y += p.vy;
            if (p.x < 0) { p.x = 0; p.vx = -p.vx; }
            if (p.x > W) { p.x = W; p.vx = -p.vx; }
            if (p.y < 0) { p.y = 0; p.vy = -p.vy; }
            if (p.y > H) { p.y = H; p.vy = -p.vy; }
        }
    }

    // ── Lancement & contrôle ───────────────────────────────────────────────

    /**
     * Affiche le splash, exécute le chargement, appelle onComplete à la fin.
     */
    public void showAndLoad(Runnable onComplete) {
        this.onComplete = onComplete;
        setVisible(true);
        startAnimation();
        startProgress();
    }

    private void startAnimation() {
        animTimer = new Timer(30, e -> {
            animPhase   = (animPhase  + 0.03f) % 1000f;  // borne : évite overflow float
            pulsePhase  = (pulsePhase + 0.08f) % 1000f;
            updateParticles();

            // Fade-in
            if (fadingIn) {
                fadeAlpha = Math.min(1f, fadeAlpha + 0.06f);
                if (fadeAlpha >= 1f) fadingIn = false;
            }
            // Fade-out
            if (fadingOut) {
                fadeAlpha = Math.max(0f, fadeAlpha - 0.05f);
                if (fadeAlpha <= 0f) {
                    animTimer.stop();
                    setVisible(false);
                    dispose();
                    if (onComplete != null) onComplete.run();
                }
            }
            repaint();
        });
        animTimer.start();
    }

    private void startProgress() {
        progressTimer = new Timer(260, e -> {
            if (stepIndex >= STEPS.length) {
                progressTimer.stop();
                // Pause avant fade-out
                new Timer(600, ev -> {
                    ((Timer) ev.getSource()).stop();
                    fadingOut = true;
                }).start();
                return;
            }
            int target = Integer.parseInt(STEPS[stepIndex][0]);
            String msg = STEPS[stepIndex][1];
            stepIndex++;

            // Animer vers la valeur cible
            Timer anim = new Timer(20, null);
            anim.addActionListener(ev -> {
                progressValue = Math.min(progressValue + 2, target);
                progressBar.setValue(progressValue);
                if (progressValue >= target) ((Timer) ev.getSource()).stop();
            });
            anim.start();
            lblStatus.setText(msg);
        });
        // Délai avant premier step (laisse le fade-in se faire)
        new Timer(400, e -> {
            ((Timer) e.getSource()).stop();
            progressTimer.start();
        }).start();
    }

    public void setLoadingMessage(String msg) {
        SwingUtilities.invokeLater(() -> lblStatus.setText(msg));
    }
}
