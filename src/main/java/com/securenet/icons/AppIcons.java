package com.securenet.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;

/**
 * Bibliothèque d'icônes vectorielles modernes dessinées en Java2D.
 * Chaque icône est une ImageIcon générée à la volée, nette à toute taille.
 * Style : line icons, strokeWidth=1.8, coins arrondis.
 */
public class AppIcons {

    public static final int SM  = 16;
    public static final int MD  = 20;
    public static final int LG  = 24;
    public static final int XL  = 32;

    // ─── Couleurs par défaut ────────────────────────────────────────────────
    private static final Color DEFAULT  = new Color(160, 180, 210);
    private static final Color ACTIVE   = new Color(0,   150, 255);
    private static final Color SUCCESS  = new Color(0,   220, 130);
    private static final Color WARNING  = new Color(255, 160,   0);
    private static final Color DANGER   = new Color(255,  70,  70);
    private static final Color PURPLE   = new Color(150, 100, 255);

    // ══════════════════════════════════════════════════════════════════════
    //  API publique – icônes nommées
    // ══════════════════════════════════════════════════════════════════════

    public static ImageIcon dashboard(int size)       { return draw(size, DEFAULT,  AppIcons::iconDashboard);  }
    public static ImageIcon devices(int size)         { return draw(size, ACTIVE,   AppIcons::iconDevices);    }
    public static ImageIcon ids(int size)             { return draw(size, WARNING,  AppIcons::iconIDS);        }
    public static ImageIcon firewall(int size)        { return draw(size, DANGER,   AppIcons::iconFirewall);   }
    public static ImageIcon siem(int size)            { return draw(size, WARNING,  AppIcons::iconSIEM);       }
    public static ImageIcon twin(int size)            { return draw(size, PURPLE,   AppIcons::iconTwin);       }
    public static ImageIcon settings(int size)        { return draw(size, DEFAULT,  AppIcons::iconSettings);   }
    public static ImageIcon user(int size)            { return draw(size, DEFAULT,  AppIcons::iconUser);       }
    public static ImageIcon logout(int size)          { return draw(size, DANGER,   AppIcons::iconLogout);     }
    public static ImageIcon shield(int size)          { return draw(size, ACTIVE,   AppIcons::iconShield);     }
    public static ImageIcon add(int size)             { return draw(size, SUCCESS,  AppIcons::iconAdd);        }
    public static ImageIcon delete(int size)          { return draw(size, DANGER,   AppIcons::iconDelete);     }
    public static ImageIcon refresh(int size)         { return draw(size, DEFAULT,  AppIcons::iconRefresh);    }
    public static ImageIcon search(int size)          { return draw(size, DEFAULT,  AppIcons::iconSearch);     }
    public static ImageIcon export(int size)          { return draw(size, DEFAULT,  AppIcons::iconExport);     }
    public static ImageIcon lock(int size)            { return draw(size, SUCCESS,  AppIcons::iconLock);       }
    public static ImageIcon unlock(int size)          { return draw(size, WARNING,  AppIcons::iconUnlock);     }
    public static ImageIcon network(int size)         { return draw(size, ACTIVE,   AppIcons::iconNetwork);    }
    public static ImageIcon alert(int size)           { return draw(size, WARNING,  AppIcons::iconAlert);      }
    public static ImageIcon check(int size)           { return draw(size, SUCCESS,  AppIcons::iconCheck);      }
    public static ImageIcon pause(int size)           { return draw(size, WARNING,  AppIcons::iconPause);      }
    public static ImageIcon play(int size)            { return draw(size, SUCCESS,  AppIcons::iconPlay);       }
    public static ImageIcon key(int size)             { return draw(size, PURPLE,   AppIcons::iconKey);        }
    public static ImageIcon camera(int size)          { return draw(size, DEFAULT,  AppIcons::iconCamera);     }
    public static ImageIcon router(int size)          { return draw(size, ACTIVE,   AppIcons::iconRouter);     }
    public static ImageIcon sensor(int size)          { return draw(size, SUCCESS,  AppIcons::iconSensor);     }

    // ══════════════════════════════════════════════════════════════════════
    //  Moteur de rendu
    // ══════════════════════════════════════════════════════════════════════

    @FunctionalInterface
    private interface Painter { void paint(Graphics2D g, int size, Color color); }

    private static ImageIcon draw(int size, Color color, Painter painter) {
        // Rendu HiDPI (scale x2)
        int scale = 2;
        BufferedImage img = new BufferedImage(size * scale, size * scale,
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(scale, scale);
        painter.paint(g, size, color);
        g.dispose();

        // Revenir à taille normale avec scaling
        Image scaled = img.getScaledInstance(size, size, Image.SCALE_SMOOTH);
        return new ImageIcon(scaled);
    }

    private static Stroke stroke(float w) {
        return new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Icônes individuelles
    // ══════════════════════════════════════════════════════════════════════

    // 📊 Dashboard - 4 carrés en grille
    private static void iconDashboard(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float h = s * 0.38f, p = s * 0.1f, gap = s * 0.06f;
        float x1 = p, y1 = p, x2 = p + h + gap, y2 = p + h + gap;
        g.draw(round(x1, y1, h, h, 2));
        g.draw(round(x2, y1, s - p - x2, h, 2));
        g.draw(round(x1, y2, h, s - p - y2, 2));
        g.draw(round(x2, y2, s - p - x2, s - p - y2, 2));
    }

    // 📡 Devices - nœud central + 3 satellites
    private static void iconDevices(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f, cy = s/2f, r = s*0.12f;
        // Centre
        g.fill(circle(cx, cy, r));
        // Satellites
        float[][] pts = {{cx, s*0.12f},{s*0.85f, cy},{s*0.15f, cy}};
        for (float[] pt : pts) {
            g.draw(new Line2D.Float(cx, cy, pt[0], pt[1]));
            g.fill(circle(pt[0], pt[1], r * 0.85f));
        }
        // Arc WiFi en haut à droite
        drawWifiArcs(g, s * 0.78f, s * 0.22f, s * 0.07f, c);
    }

    // 🔍 IDS - œil avec pupille
    private static void iconIDS(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f, cy = s/2f;
        float rx = s * 0.42f, ry = s * 0.25f;
        // Contour œil (2 arcs)
        Path2D eye = new Path2D.Float();
        eye.moveTo(cx - rx, cy);
        eye.quadTo(cx, cy - ry * 1.6f, cx + rx, cy);
        eye.quadTo(cx, cy + ry * 1.6f, cx - rx, cy);
        g.draw(eye);
        // Pupille
        g.fill(circle(cx, cy, s * 0.1f));
        // Anneau
        g.draw(circle(cx, cy, s * 0.17f));
        // Trait de scan
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 120));
        g.setStroke(stroke(1.0f));
        g.draw(new Line2D.Float(cx + s*0.18f, cy - s*0.18f, cx + s*0.35f, cy - s*0.35f));
    }

    // 🔥 Firewall - bouclier avec verrou
    private static void iconFirewall(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        Path2D shield = shieldPath(s);
        g.draw(shield);
        // Verrou au centre
        float lx = s*0.38f, ly = s*0.44f, lw = s*0.24f, lh = s*0.2f;
        g.draw(round(lx, ly, lw, lh, 3));
        g.draw(new Arc2D.Float(lx + lw*0.18f, ly - lh*0.55f,
               lw*0.64f, lh*0.7f, 0, 180, Arc2D.OPEN));
        // Point
        g.fill(circle(s/2f, ly + lh * 0.5f, s*0.03f));
    }

    // 🚨 SIEM - cloche d'alerte
    private static void iconSIEM(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f;
        Path2D bell = new Path2D.Float();
        bell.moveTo(cx - s*0.08f, s*0.82f);
        bell.lineTo(cx + s*0.08f, s*0.82f);
        bell.lineTo(cx + s*0.38f, s*0.65f);
        bell.lineTo(cx + s*0.38f, s*0.45f);
        bell.quadTo(cx + s*0.38f, s*0.12f, cx, s*0.12f);
        bell.quadTo(cx - s*0.38f, s*0.12f, cx - s*0.38f, s*0.45f);
        bell.lineTo(cx - s*0.38f, s*0.65f);
        bell.closePath();
        g.draw(bell);
        // Clapet bas
        g.draw(new Arc2D.Float(cx - s*0.13f, s*0.78f, s*0.26f, s*0.13f,
               0, -180, Arc2D.OPEN));
        // Pastille d'alerte
        g.setColor(DANGER);
        g.fill(circle(cx + s*0.28f, s*0.22f, s*0.1f));
    }

    // 🌐 Digital Twin - nœuds interconnectés
    private static void iconTwin(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.5f)); g.setColor(c);
        float r = s * 0.09f;
        float[][] nodes = {
            {s*0.5f, s*0.15f},
            {s*0.15f, s*0.72f},
            {s*0.85f, s*0.72f},
            {s*0.5f, s*0.5f}
        };
        // Liens
        int[][] links = {{0,3},{1,3},{2,3},{0,2},{0,1}};
        g.setStroke(stroke(1.2f));
        for (int[] l : links) {
            g.draw(new Line2D.Float(nodes[l[0]][0], nodes[l[0]][1],
                                    nodes[l[1]][0], nodes[l[1]][1]));
        }
        // Nœuds
        g.setStroke(stroke(1.6f));
        for (float[] n : nodes) { g.draw(circle(n[0], n[1], r)); }
        g.fill(circle(nodes[3][0], nodes[3][1], r * 0.5f)); // nœud central plein
    }

    // ⚙️ Settings - engrenage
    private static void iconSettings(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f, cy = s/2f;
        float outer = s*0.38f, inner = s*0.22f, tooth = s*0.08f;
        Path2D gear = new Path2D.Float();
        int teeth = 8;
        for (int i = 0; i < teeth * 2; i++) {
            double angle = Math.PI * i / teeth;
            float r = (i % 2 == 0) ? outer : outer - tooth;
            if (i == 0) gear.moveTo(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
            else        gear.lineTo(cx + r * Math.cos(angle), cy + r * Math.sin(angle));
        }
        gear.closePath();
        g.draw(gear);
        g.draw(circle(cx, cy, inner));
    }

    // 👤 User - silhouette
    private static void iconUser(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f;
        g.draw(circle(cx, s*0.32f, s*0.18f));
        Path2D body = new Path2D.Float();
        body.moveTo(s*0.1f, s*0.9f);
        body.quadTo(s*0.1f,  s*0.58f, cx, s*0.58f);
        body.quadTo(s*0.9f,  s*0.58f, s*0.9f, s*0.9f);
        g.draw(body);
    }

    // ⏻ Logout
    private static void iconLogout(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f, cy = s/2f;
        g.draw(new Arc2D.Float(cx - s*0.35f, cy - s*0.35f,
               s*0.7f, s*0.7f, 120, 300, Arc2D.OPEN));
        g.draw(new Line2D.Float(cx, s*0.1f, cx, cy * 0.92f));
    }

    // 🛡 Shield
    private static void iconShield(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(shieldPath(s));
        // Check inside
        g.setStroke(stroke(1.8f));
        Path2D check = new Path2D.Float();
        check.moveTo(s*0.36f, s*0.52f);
        check.lineTo(s*0.47f, s*0.63f);
        check.lineTo(s*0.64f, s*0.40f);
        g.draw(check);
    }

    // + Add
    private static void iconAdd(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.8f)); g.setColor(c);
        float cx = s/2f, cy = s/2f, r = s*0.35f;
        g.draw(circle(cx, cy, r));
        g.draw(new Line2D.Float(cx, cy - r*0.55f, cx, cy + r*0.55f));
        g.draw(new Line2D.Float(cx - r*0.55f, cy, cx + r*0.55f, cy));
    }

    // 🗑 Delete - poubelle
    private static void iconDelete(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float p = s*0.18f;
        g.draw(new Line2D.Float(s*0.12f, s*0.28f, s*0.88f, s*0.28f));
        g.draw(round(s*0.25f, s*0.28f, s*0.5f, s*0.62f, 2));
        g.draw(new Line2D.Float(s*0.4f, s*0.12f, s*0.6f, s*0.12f));
        g.draw(new Line2D.Float(s*0.4f, s*0.12f, s*0.35f, s*0.28f));
        g.draw(new Line2D.Float(s*0.6f, s*0.12f, s*0.65f, s*0.28f));
        // Lignes internes
        g.draw(new Line2D.Float(s*0.42f, s*0.42f, s*0.42f, s*0.78f));
        g.draw(new Line2D.Float(s*0.58f, s*0.42f, s*0.58f, s*0.78f));
    }

    // ↻ Refresh
    private static void iconRefresh(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f, cy = s/2f, r = s*0.33f;
        g.draw(new Arc2D.Float(cx-r, cy-r, r*2, r*2, 60, 270, Arc2D.OPEN));
        // Flèche
        float ax = cx + r * (float)Math.cos(Math.toRadians(60));
        float ay = cy - r * (float)Math.sin(Math.toRadians(60));
        g.draw(new Line2D.Float(ax - s*0.1f, ay + s*0.02f, ax, ay));
        g.draw(new Line2D.Float(ax + s*0.06f, ay + s*0.12f, ax, ay));
    }

    // 🔍 Search
    private static void iconSearch(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float r = s * 0.28f, cx = s*0.4f, cy = s*0.4f;
        g.draw(circle(cx, cy, r));
        float lx = cx + r * 0.7f, ly = cy + r * 0.7f;
        g.draw(new Line2D.Float(lx, ly, s*0.88f, s*0.88f));
    }

    // 📤 Export - boîte avec flèche montante
    private static void iconExport(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(round(s*0.15f, s*0.5f, s*0.7f, s*0.38f, 3));
        float cx = s/2f;
        g.draw(new Line2D.Float(cx, s*0.12f, cx, s*0.55f));
        Path2D arrow = new Path2D.Float();
        arrow.moveTo(cx - s*0.16f, s*0.3f);
        arrow.lineTo(cx, s*0.12f);
        arrow.lineTo(cx + s*0.16f, s*0.3f);
        g.draw(arrow);
    }

    // 🔒 Lock
    private static void iconLock(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(round(s*0.22f, s*0.46f, s*0.56f, s*0.44f, 4));
        g.draw(new Arc2D.Float(s*0.3f, s*0.12f, s*0.4f, s*0.42f, 0, 180, Arc2D.OPEN));
        g.fill(circle(s/2f, s*0.64f, s*0.06f));
        g.draw(new Line2D.Float(s/2f, s*0.64f, s/2f, s*0.75f));
    }

    // 🔓 Unlock
    private static void iconUnlock(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(round(s*0.22f, s*0.46f, s*0.56f, s*0.44f, 4));
        g.draw(new Arc2D.Float(s*0.1f, s*0.06f, s*0.4f, s*0.42f, 0, 180, Arc2D.OPEN));
        g.fill(circle(s/2f, s*0.64f, s*0.06f));
    }

    // 🌐 Network - globe
    private static void iconNetwork(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.5f)); g.setColor(c);
        float cx = s/2f, cy = s/2f, r = s*0.38f;
        g.draw(circle(cx, cy, r));
        g.draw(new Ellipse2D.Float(cx - r*0.55f, cy - r, r*1.1f, r*2));
        g.draw(new Line2D.Float(cx - r, cy, cx + r, cy));
        g.draw(new Line2D.Float(cx - r*0.9f, cy - r*0.55f, cx + r*0.9f, cy - r*0.55f));
        g.draw(new Line2D.Float(cx - r*0.9f, cy + r*0.55f, cx + r*0.9f, cy + r*0.55f));
    }

    // ⚠ Alert - triangle
    private static void iconAlert(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        Path2D t = new Path2D.Float();
        t.moveTo(s/2f,      s*0.1f);
        t.lineTo(s*0.9f,    s*0.88f);
        t.lineTo(s*0.1f,    s*0.88f);
        t.closePath();
        g.draw(t);
        g.draw(new Line2D.Float(s/2f, s*0.35f, s/2f, s*0.64f));
        g.fill(circle(s/2f, s*0.75f, s*0.04f));
    }

    // ✔ Check
    private static void iconCheck(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(2.0f)); g.setColor(c);
        Path2D check = new Path2D.Float();
        check.moveTo(s*0.18f, s*0.5f);
        check.lineTo(s*0.42f, s*0.74f);
        check.lineTo(s*0.82f, s*0.26f);
        g.draw(check);
    }

    // ⏸ Pause
    private static void iconPause(Graphics2D g, int s, Color c) {
        g.setColor(c);
        g.fill(round(s*0.22f, s*0.2f, s*0.2f, s*0.6f, 2));
        g.fill(round(s*0.58f, s*0.2f, s*0.2f, s*0.6f, 2));
    }

    // ▶ Play
    private static void iconPlay(Graphics2D g, int s, Color c) {
        g.setColor(c);
        Path2D p = new Path2D.Float();
        p.moveTo(s*0.25f, s*0.15f);
        p.lineTo(s*0.85f, s*0.5f);
        p.lineTo(s*0.25f, s*0.85f);
        p.closePath();
        g.fill(p);
    }

    // 🗝 Key
    private static void iconKey(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(circle(s*0.35f, s*0.38f, s*0.22f));
        g.draw(new Line2D.Float(s*0.52f, s*0.52f, s*0.88f, s*0.88f));
        g.draw(new Line2D.Float(s*0.72f, s*0.72f, s*0.72f, s*0.84f));
        g.draw(new Line2D.Float(s*0.64f, s*0.64f, s*0.78f, s*0.64f));
    }

    // 📷 Camera
    private static void iconCamera(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(round(s*0.1f, s*0.3f, s*0.8f, s*0.55f, 4));
        g.draw(circle(s/2f, s*0.57f, s*0.18f));
        g.fill(circle(s/2f, s*0.57f, s*0.07f));
        // Bosse haut
        Path2D hump = new Path2D.Float();
        hump.moveTo(s*0.36f, s*0.3f);
        hump.lineTo(s*0.32f, s*0.18f);
        hump.lineTo(s*0.52f, s*0.18f);
        hump.lineTo(s*0.64f, s*0.3f);
        g.draw(hump);
    }

    // 📡 Router - antenne
    private static void iconRouter(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        g.draw(round(s*0.12f, s*0.54f, s*0.76f, s*0.32f, 4));
        g.draw(new Line2D.Float(s*0.5f, s*0.54f, s*0.5f, s*0.18f));
        drawWifiArcs(g, s*0.5f, s*0.2f, s*0.14f, c);
        // LEDs
        for (float fx : new float[]{0.3f, 0.5f, 0.7f}) {
            g.fill(circle(s*fx, s*0.7f, s*0.04f));
        }
    }

    // 🌡 Sensor
    private static void iconSensor(Graphics2D g, int s, Color c) {
        g.setStroke(stroke(1.6f)); g.setColor(c);
        float cx = s/2f;
        g.draw(round(cx - s*0.1f, s*0.14f, s*0.2f, s*0.52f, 10));
        g.fill(circle(cx, s*0.76f, s*0.15f));
        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 100));
        g.draw(new Line2D.Float(s*0.68f, s*0.28f, s*0.78f, s*0.28f));
        g.draw(new Line2D.Float(s*0.68f, s*0.42f, s*0.82f, s*0.42f));
        g.draw(new Line2D.Float(s*0.68f, s*0.56f, s*0.78f, s*0.56f));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Helpers géométriques
    // ══════════════════════════════════════════════════════════════════════

    private static Ellipse2D.Float circle(float cx, float cy, float r) {
        return new Ellipse2D.Float(cx - r, cy - r, r * 2, r * 2);
    }

    private static RoundRectangle2D.Float round(float x, float y,
                                                  float w, float h, float arc) {
        return new RoundRectangle2D.Float(x, y, w, h, arc, arc);
    }

    private static Path2D shieldPath(int s) {
        Path2D path = new Path2D.Float();
        float cx = s / 2f;
        path.moveTo(cx, s * 0.1f);
        path.lineTo(s * 0.88f, s * 0.3f);
        path.lineTo(s * 0.88f, s * 0.55f);
        path.quadTo(s * 0.88f, s * 0.88f, cx, s * 0.9f);
        path.quadTo(s * 0.12f, s * 0.88f, s * 0.12f, s * 0.55f);
        path.lineTo(s * 0.12f, s * 0.3f);
        path.closePath();
        return path;
    }

    private static void drawWifiArcs(Graphics2D g, float cx, float cy,
                                      float baseR, Color c) {
        g.setColor(c);
        for (int i = 3; i >= 1; i--) {
            float r = baseR * i;
            g.draw(new Arc2D.Float(cx - r, cy - r, r*2, r*2, 40, 100, Arc2D.OPEN));
        }
        g.fill(circle(cx, cy + baseR * 3, baseR * 0.22f));
    }
}
