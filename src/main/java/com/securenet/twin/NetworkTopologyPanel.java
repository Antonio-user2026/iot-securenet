package com.securenet.twin;

import com.securenet.common.AppColors;
import com.securenet.common.AppFonts;
import com.securenet.devices.Device;
import com.securenet.events.EventBus;
import com.securenet.events.SecurityEvent;
import com.securenet.icons.AppIcons;

import javax.swing.*;
import javax.swing.Timer;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

/**
 * Panneau de visualisation de la topologie réseau (Digital Twin).
 * Rendu graphique interactif :
 *  - Nœuds déplaçables (drag & drop)
 *  - Liens animés avec flux de données
 *  - Coloration par statut en temps réel
 *  - Zoom et pan
 *  - Clic sur nœud = infos détaillées
 */
public class NetworkTopologyPanel extends JPanel {

    // ── Modèle de données ──────────────────────────────────────────────────
    private final List<TopologyNode> nodes = new ArrayList<>();
    private final List<TopologyLink> links = new ArrayList<>();

    // ── Interaction ────────────────────────────────────────────────────────
    private TopologyNode draggingNode  = null;
    private TopologyNode selectedNode  = null;
    private Point        dragOffset    = new Point();
    private double       zoomFactor    = 1.0;
    private Point        panOffset     = new Point(0, 0);
    private Point        lastPanPoint  = null;

    // ── Animation ──────────────────────────────────────────────────────────
    private float  animPhase = 0f;   // 0..1, drive packet animation
    private Timer  animTimer;

    // ── Callbacks ──────────────────────────────────────────────────────────
    private java.util.function.Consumer<TopologyNode> onNodeSelected;

    public NetworkTopologyPanel() {
        setBackground(AppColors.BG_DARK);
        setPreferredSize(new Dimension(800, 600));
        initInteraction();
        startAnimation();
        subscribeEvents();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Données
    // ══════════════════════════════════════════════════════════════════════

    public void setNodes(List<TopologyNode> n) {
        nodes.clear(); nodes.addAll(n);
        autoLayout();
        repaint();
    }

    public void setLinks(List<TopologyLink> l) {
        links.clear(); links.addAll(l);
        repaint();
    }

    public void addNode(TopologyNode n) {
        nodes.add(n);
        autoLayout();
        repaint();
    }

    public void addLink(TopologyLink l) {
        links.add(l);
        repaint();
    }

    public void setOnNodeSelected(java.util.function.Consumer<TopologyNode> cb) {
        this.onNodeSelected = cb;
    }

    /** Mise à jour du statut d'un nœud par IP (depuis IDS/SIEM). */
    public void updateNodeStatus(String ip, TopologyNode.Status status) {
        SwingUtilities.invokeLater(() -> {
            nodes.stream().filter(n -> n.ip.equals(ip)).forEach(n -> n.status = status);
            repaint();
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Layout automatique — algorithme force-directed simplifié
    // ══════════════════════════════════════════════════════════════════════

    private void autoLayout() {
        int w = Math.max(getWidth(), 800);
        int h = Math.max(getHeight(), 600);

        if (nodes.isEmpty()) return;

        // Si les nœuds ont des positions sauvegardées, les utiliser
        boolean allZero = nodes.stream().allMatch(n -> n.x == 0 && n.y == 0);
        if (!allZero) return;

        // Placer en cercle + 1 gateway au centre
        TopologyNode gateway = nodes.stream()
            .filter(n -> n.type == Device.Type.ROUTER || n.type == Device.Type.GATEWAY)
            .findFirst().orElse(null);

        if (gateway != null) {
            gateway.x = w / 2f;
            gateway.y = h / 2f;
            List<TopologyNode> others = new ArrayList<>(nodes);
            others.remove(gateway);
            float cx = w / 2f, cy = h / 2f;
            float radius = Math.min(w, h) * 0.32f;
            for (int i = 0; i < others.size(); i++) {
                double angle = 2 * Math.PI * i / others.size();
                others.get(i).x = cx + radius * (float) Math.cos(angle);
                others.get(i).y = cy + radius * (float) Math.sin(angle);
            }
        } else {
            // Grille sinon
            int cols = (int) Math.ceil(Math.sqrt(nodes.size()));
            float cellW = w / (float)(cols + 1), cellH = h / (float)(nodes.size()/cols + 2);
            for (int i = 0; i < nodes.size(); i++) {
                nodes.get(i).x = cellW * (i % cols + 1);
                nodes.get(i).y = cellH * (i / cols + 1);
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Rendu
    // ══════════════════════════════════════════════════════════════════════

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Grille de fond
        drawGrid(g2);

        // Appliquer pan + zoom
        g2.translate(panOffset.x, panOffset.y);
        g2.scale(zoomFactor, zoomFactor);

        // Liens d'abord (derrière les nœuds)
        for (TopologyLink link : links) drawLink(g2, link);

        // Nœuds
        for (TopologyNode node : nodes) drawNode(g2, node);

        // Légende
        drawLegend(g2);

        g2.dispose();
    }

    private void drawGrid(Graphics2D g2) {
        g2.setColor(new Color(255, 255, 255, 6));
        g2.setStroke(new BasicStroke(0.5f));
        int step = 40;
        for (int x = 0; x < getWidth();  x += step) g2.drawLine(x, 0, x, getHeight());
        for (int y = 0; y < getHeight(); y += step) g2.drawLine(0, y, getWidth(), y);
    }

    private void drawLink(Graphics2D g2, TopologyLink link) {
        TopologyNode src = findNode(link.sourceId);
        TopologyNode tgt = findNode(link.targetId);
        if (src == null || tgt == null) return;

        float sx = src.x, sy = src.y, tx = tgt.x, ty = tgt.y;

        // Couleur selon état
        Color linkColor = link.isAlert
            ? new Color(255, 70, 70, 120)
            : new Color(80, 120, 180, 80);

        // Trait principal
        g2.setColor(linkColor);
        g2.setStroke(new BasicStroke(link.isAlert ? 2.0f : 1.2f,
            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new Line2D.Float(sx, sy, tx, ty));

        // Animation de paquets (points qui se déplacent le long du lien)
        float[] phases = {animPhase, (animPhase + 0.33f) % 1f, (animPhase + 0.66f) % 1f};
        for (float phase : phases) {
            float px = sx + (tx - sx) * phase;
            float py = sy + (ty - sy) * phase;
            g2.setColor(new Color(0, 200, 255, (int)(180 * (1 - Math.abs(phase - 0.5f) * 2))));
            g2.fill(new Ellipse2D.Float(px - 2.5f, py - 2.5f, 5, 5));
        }

        // Label bandwidth au milieu
        if (link.bandwidth > 0) {
            float mx = (sx + tx) / 2f, my = (sy + ty) / 2f;
            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g2.setColor(new Color(120, 150, 200, 160));
            g2.drawString(link.bandwidth + "Mbps", mx + 4, my - 4);
        }
    }

    private static final int NODE_R = 28; // rayon nœud

    private void drawNode(Graphics2D g2, TopologyNode node) {
        float x = node.x, y = node.y;
        boolean selected = node == selectedNode;

        // Halo de sélection
        if (selected) {
            g2.setColor(new Color(0, 150, 255, 35));
            g2.fill(new Ellipse2D.Float(x - NODE_R - 8, y - NODE_R - 8,
                                         (NODE_R + 8)*2, (NODE_R + 8)*2));
            g2.setColor(new Color(0, 150, 255, 160));
            g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                1, new float[]{4, 3}, 0));
            g2.draw(new Ellipse2D.Float(x - NODE_R - 8, y - NODE_R - 8,
                                         (NODE_R + 8)*2, (NODE_R + 8)*2));
        }

        // Halo de statut (glow)
        Color statusColor = nodeStatusColor(node.status);
        g2.setColor(new Color(statusColor.getRed(), statusColor.getGreen(),
                               statusColor.getBlue(), 40));
        g2.fill(new Ellipse2D.Float(x - NODE_R - 4, y - NODE_R - 4,
                                     (NODE_R+4)*2, (NODE_R+4)*2));

        // Corps du nœud
        GradientPaint grad = new GradientPaint(
            x - NODE_R, y - NODE_R, AppColors.BG_CARD,
            x + NODE_R, y + NODE_R, new Color(50, 60, 80));
        g2.setPaint(grad);
        g2.fill(new Ellipse2D.Float(x - NODE_R, y - NODE_R, NODE_R*2, NODE_R*2));

        // Bordure
        g2.setColor(statusColor);
        g2.setStroke(new BasicStroke(1.8f));
        g2.draw(new Ellipse2D.Float(x - NODE_R, y - NODE_R, NODE_R*2, NODE_R*2));

        // Icône du type
        ImageIcon icon = getDeviceIcon(node.type, NODE_R);
        if (icon != null) {
            icon.paintIcon(this, g2,
                (int)(x - icon.getIconWidth()  / 2f),
                (int)(y - icon.getIconHeight() / 2f));
        }

        // Badge statut (coin bas-droit)
        g2.setColor(statusColor);
        g2.fill(new Ellipse2D.Float(x + NODE_R*0.55f, y + NODE_R*0.55f, 10, 10));
        g2.setColor(AppColors.BG_DARK);
        g2.setStroke(new BasicStroke(1.5f));
        g2.draw(new Ellipse2D.Float(x + NODE_R*0.55f, y + NODE_R*0.55f, 10, 10));

        // Label
        g2.setFont(AppFonts.SMALL);
        g2.setColor(AppColors.TEXT_PRIMARY);
        FontMetrics fm = g2.getFontMetrics();
        String label = node.name.length() > 14 ? node.name.substring(0, 13) + "…" : node.name;
        float lx = x - fm.stringWidth(label) / 2f;
        g2.drawString(label, lx, y + NODE_R + 14);

        // IP sous le nom
        g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
        g2.setColor(AppColors.TEXT_MUTED);
        fm = g2.getFontMetrics();
        float ix = x - fm.stringWidth(node.ip) / 2f;
        g2.drawString(node.ip, ix, y + NODE_R + 25);
    }

    private void drawLegend(Graphics2D g2) {
        int lx = 14, ly = getHeight() - 14;
        TopologyNode.Status[] statuses = TopologyNode.Status.values();
        for (int i = statuses.length - 1; i >= 0; i--) {
            TopologyNode.Status s = statuses[i];
            Color c = nodeStatusColor(s);
            g2.setColor(c);
            g2.fill(new Ellipse2D.Float(lx, ly - 10, 10, 10));
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            g2.setColor(AppColors.TEXT_MUTED);
            g2.drawString(s.label, lx + 13, ly);
            lx += 80;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Couleurs et icônes
    // ══════════════════════════════════════════════════════════════════════

    private Color nodeStatusColor(TopologyNode.Status s) {
        return switch (s) {
            case ONLINE    -> AppColors.STATUS_OK;
            case OFFLINE   -> AppColors.TEXT_MUTED;
            case SUSPICIOUS-> AppColors.STATUS_WARNING;
            case BLOCKED   -> AppColors.STATUS_CRITICAL;
            case SCANNING  -> AppColors.ACCENT_BLUE;
        };
    }

    private ImageIcon getDeviceIcon(Device.Type type, int nodeR) {
        int size = (int)(nodeR * 0.85f);
        return switch (type) {
            case CAMERA     -> AppIcons.camera(size);
            case ROUTER     -> AppIcons.router(size);
            case GATEWAY    -> AppIcons.network(size);
            case SENSOR, THERMOSTAT -> AppIcons.sensor(size);
            case SMART_LOCK -> AppIcons.lock(size);
            default         -> AppIcons.devices(size);
        };
    }

    private TopologyNode findNode(int id) {
        return nodes.stream().filter(n -> n.id == id).findFirst().orElse(null);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Interaction souris
    // ══════════════════════════════════════════════════════════════════════

    private void initInteraction() {
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                Point wp = screenToWorld(e.getPoint());
                if (SwingUtilities.isMiddleMouseButton(e) ||
                    (SwingUtilities.isLeftMouseButton(e) && e.isAltDown())) {
                    lastPanPoint = e.getPoint();
                    return;
                }
                TopologyNode hit = nodeAt(wp);
                if (hit != null) {
                    draggingNode = hit;
                    dragOffset.x = (int)(hit.x - wp.x);
                    dragOffset.y = (int)(hit.y - wp.y);
                    selectedNode = hit;
                    if (onNodeSelected != null) onNodeSelected.accept(hit);
                } else {
                    selectedNode = null;
                    lastPanPoint = e.getPoint();
                }
                repaint();
            }

            @Override public void mouseDragged(MouseEvent e) {
                if (draggingNode != null) {
                    Point wp = screenToWorld(e.getPoint());
                    draggingNode.x = wp.x + dragOffset.x;
                    draggingNode.y = wp.y + dragOffset.y;
                    repaint();
                } else if (lastPanPoint != null) {
                    panOffset.x += e.getX() - lastPanPoint.x;
                    panOffset.y += e.getY() - lastPanPoint.y;
                    lastPanPoint = e.getPoint();
                    repaint();
                }
            }

            @Override public void mouseReleased(MouseEvent e) {
                draggingNode = null;
                lastPanPoint = null;
            }

            @Override public void mouseWheelMoved(MouseWheelEvent e) {
                double factor = e.getWheelRotation() < 0 ? 1.1 : 0.9;
                zoomFactor = Math.max(0.3, Math.min(3.0, zoomFactor * factor));
                repaint();
            }

            @Override public void mouseMoved(MouseEvent e) {
                Point wp = screenToWorld(e.getPoint());
                TopologyNode hit = nodeAt(wp);
                setCursor(hit != null
                    ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                    : Cursor.getDefaultCursor());
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        addMouseWheelListener(ma);
    }

    private Point screenToWorld(Point screen) {
        int wx = (int)((screen.x - panOffset.x) / zoomFactor);
        int wy = (int)((screen.y - panOffset.y) / zoomFactor);
        return new Point(wx, wy);
    }

    private TopologyNode nodeAt(Point p) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            TopologyNode n = nodes.get(i);
            double dist = Math.sqrt(Math.pow(n.x - p.x, 2) + Math.pow(n.y - p.y, 2));
            if (dist <= NODE_R + 4) return n;
        }
        return null;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Animation
    // ══════════════════════════════════════════════════════════════════════

    private void startAnimation() {
        animTimer = new Timer(40, e -> {
            animPhase = (animPhase + 0.015f) % 1f;
            repaint();
        });
        animTimer.start();
    }

    public void stopAnimation() { if (animTimer != null) animTimer.stop(); }

    // ══════════════════════════════════════════════════════════════════════
    //  Écoute des alertes IDS/SIEM
    // ══════════════════════════════════════════════════════════════════════

    private void subscribeEvents() {
        EventBus.getInstance().subscribe(SecurityEvent.class, event -> {
            if (event.getSeverity() == SecurityEvent.Severity.CRITICAL) {
                updateNodeStatus(event.getSourceIP(), TopologyNode.Status.SUSPICIOUS);
                // Marquer les liens vers cette IP comme alertes
                nodes.stream()
                    .filter(n -> n.ip.equals(event.getSourceIP()))
                    .findFirst()
                    .ifPresent(n -> links.stream()
                        .filter(l -> l.sourceId == n.id || l.targetId == n.id)
                        .forEach(l -> l.isAlert = true));
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Contrôles (zoom fit, reset)
    // ══════════════════════════════════════════════════════════════════════

    public void resetView() {
        zoomFactor = 1.0;
        panOffset  = new Point(0, 0);
        repaint();
    }

    public void zoomFit() {
        if (nodes.isEmpty()) return;
        float minX = nodes.stream().map(n -> n.x).reduce(Float.MAX_VALUE, Math::min);
        float maxX = nodes.stream().map(n -> n.x).reduce(Float.MIN_VALUE, Math::max);
        float minY = nodes.stream().map(n -> n.y).reduce(Float.MAX_VALUE, Math::min);
        float maxY = nodes.stream().map(n -> n.y).reduce(Float.MIN_VALUE, Math::max);
        float contentW = maxX - minX + NODE_R * 4;
        float contentH = maxY - minY + NODE_R * 4;
        zoomFactor = Math.min(getWidth() / contentW, getHeight() / contentH) * 0.85;
        zoomFactor = Math.max(0.3, Math.min(2.0, zoomFactor));
        panOffset.x = (int)((getWidth()  - contentW * zoomFactor) / 2 - minX * zoomFactor);
        panOffset.y = (int)((getHeight() - contentH * zoomFactor) / 2 - minY * zoomFactor);
        repaint();
    }
}
