package com.securenet.twin;

import com.securenet.events.EventBus;
import com.securenet.events.SecurityEvent;

import java.util.function.BiConsumer;

/**
 * Moteur de simulation des actions réseau dans le Digital Twin.
 *
 * Gère toutes les transitions d'état des nœuds avec :
 *  - Délais de simulation réalistes
 *  - Effets visuels (statut animé)
 *  - Injection d'événements dans l'EventBus → IDS / SIEM
 *  - Journal d'activité
 *  - Rapport de résistance / robustesse
 */
public class DeviceActionEngine {

    /** Types d'actions disponibles dans le Digital Twin. */
    public enum ActionType {

        // ── Actions de contrôle ─────────────────────────────────────────────
        ACTIVATE    ("Activer",          "Remise en ligne de l'appareil",
                     TopologyNode.Status.ONLINE,    1500, SecurityEvent.Severity.INFO),
        BLOCK       ("Bloquer",          "Blocage réseau de l'appareil",
                     TopologyNode.Status.BLOCKED,   800,  SecurityEvent.Severity.CRITICAL),
        SHUTDOWN    ("Mettre hors ligne","Arrêt simulé de l'appareil",
                     TopologyNode.Status.OFFLINE,   1200, SecurityEvent.Severity.WARNING),
        RESTART     ("Redémarrer",       "Redémarrage simulé (cycle Off→On)",
                     TopologyNode.Status.ONLINE,    3000, SecurityEvent.Severity.INFO),

        // ── Actions de diagnostic ───────────────────────────────────────────
        SCAN_PORTS  ("Scan de ports",   "Analyse des ports ouverts",
                     TopologyNode.Status.SCANNING,  2500, SecurityEvent.Severity.INFO),
        PING        ("Ping",            "Test de connectivité ICMP",
                     null,              400,  SecurityEvent.Severity.INFO),
        TRACE_ROUTE ("Traceroute",      "Trace du chemin réseau",
                     null,              2000, SecurityEvent.Severity.INFO),

        // ── Actions de simulation de panne ──────────────────────────────────
        SIMULATE_CRASH   ("Simuler une panne",    "Panne matérielle simulée",
                     TopologyNode.Status.OFFLINE,  500, SecurityEvent.Severity.CRITICAL),
        SIMULATE_OVERLOAD("Simuler une surcharge","CPU/RAM saturés",
                     TopologyNode.Status.SUSPICIOUS, 600, SecurityEvent.Severity.WARNING),
        SIMULATE_DISCONNECT("Déconnecter réseau", "Coupure réseau simulée",
                     TopologyNode.Status.OFFLINE,   700, SecurityEvent.Severity.WARNING),

        // ── Actions de sécurité ──────────────────────────────────────────────
        ISOLATE     ("Isoler (quarantaine)", "Quarantaine réseau",
                     TopologyNode.Status.BLOCKED,   1000, SecurityEvent.Severity.CRITICAL),
        PATCH       ("Appliquer correctif",  "Mise à jour firmware simulée",
                     TopologyNode.Status.SCANNING,  4000, SecurityEvent.Severity.INFO),
        RESET_FIREWALL("Réinitialiser FW",  "Réinitialisation des règles firewall",
                     TopologyNode.Status.ONLINE,    1800, SecurityEvent.Severity.WARNING),
        CHANGE_VLAN  ("Changer VLAN",       "Migration vers VLAN sécurisé",
                     TopologyNode.Status.SCANNING,  2000, SecurityEvent.Severity.INFO);

        public final String               label;
        public final String               description;
        public final TopologyNode.Status  resultStatus; // null = pas de changement de statut
        public final int                  delayMs;
        public final SecurityEvent.Severity severity;

        ActionType(String label, String description,
                   TopologyNode.Status resultStatus,
                   int delayMs, SecurityEvent.Severity severity) {
            this.label        = label;
            this.description  = description;
            this.resultStatus = resultStatus;
            this.delayMs      = delayMs;
            this.severity     = severity;
        }
    }

    /** Résultat d'une action simulée. */
    public static class ActionResult {
        public final ActionType     type;
        public final TopologyNode   node;
        public final boolean        success;
        public final String         message;
        public final String         details;
        public final long           durationMs;

        ActionResult(ActionType type, TopologyNode node, boolean success,
                     String message, String details, long durationMs) {
            this.type       = type;
            this.node       = node;
            this.success    = success;
            this.message    = message;
            this.details    = details;
            this.durationMs = durationMs;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Exécute une action sur un nœud de manière asynchrone.
     * @param action   Type d'action
     * @param node     Nœud cible
     * @param onStart  Appelé immédiatement (pour UI : spinners, etc.)
     * @param onDone   Appelé quand l'action est terminée avec le résultat
     */
    public static void execute(ActionType action, TopologyNode node,
                                Runnable onStart,
                                java.util.function.Consumer<ActionResult> onDone) {
        if (onStart != null)
            javax.swing.SwingUtilities.invokeLater(onStart);

        // Mettre en "scanning" pendant le délai
        if (action.resultStatus != null &&
            action.resultStatus != TopologyNode.Status.OFFLINE) {
            node.status = TopologyNode.Status.SCANNING;
        }

        Thread t = new Thread(() -> {
            long t0 = System.currentTimeMillis();
            try {
                // Pour RESTART : passer par OFFLINE d'abord
                if (action == ActionType.RESTART) {
                    node.status = TopologyNode.Status.OFFLINE;
                    Thread.sleep(action.delayMs / 2);
                    node.status = TopologyNode.Status.SCANNING;
                    Thread.sleep(action.delayMs / 2);
                } else {
                    Thread.sleep(action.delayMs);
                }

                // Pour PATCH : revenir à ONLINE après SCANNING
                if (action == ActionType.PATCH || action == ActionType.SCAN_PORTS
                        || action == ActionType.CHANGE_VLAN
                        || action == ActionType.RESET_FIREWALL) {
                    Thread.sleep(500);
                }
            } catch (InterruptedException ignored) {}

            long elapsed = System.currentTimeMillis() - t0;

            // Appliquer le nouveau statut
            if (action.resultStatus != null) {
                node.status = action.resultStatus;
            }

            // Générer les messages et événements
            String msg     = buildMessage(action, node);
            String details = buildDetails(action, node, elapsed);

            // Publier l'événement dans l'EventBus
            publishEvent(action, node, msg);

            ActionResult result = new ActionResult(action, node, true,
                msg, details, elapsed);

            if (onDone != null)
                javax.swing.SwingUtilities.invokeLater(() -> onDone.accept(result));
        });
        t.setDaemon(true);
        t.start();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Génération de messages
    // ══════════════════════════════════════════════════════════════════════

    private static String buildMessage(ActionType action, TopologyNode node) {
        return switch (action) {
            case ACTIVATE      -> "[OK] " + node.name + " remis en ligne";
            case BLOCK         -> "[BLOCK] " + node.name + " (" + node.ip + ") bloqué";
            case SHUTDOWN      -> "[DOWN] " + node.name + " mis hors ligne";
            case RESTART       -> "[RESTART] " + node.name + " redémarré avec succès";
            case SCAN_PORTS    -> "[SCAN] " + node.ip + " → ports: 22, 80, 443, 1883";
            case PING          -> "[PING] " + node.ip + " → RTT=" + (5 + (int)(Math.random() * 30)) + "ms";
            case TRACE_ROUTE   -> "[TRACE] " + node.ip + " → 3 sauts, gateway: 192.168.1.1";
            case SIMULATE_CRASH-> "[CRASH] Panne simulée sur " + node.name;
            case SIMULATE_OVERLOAD -> "[OVERLOAD] Surcharge CPU/RAM sur " + node.name;
            case SIMULATE_DISCONNECT -> "[DISC] Déconnexion réseau simulée : " + node.name;
            case ISOLATE       -> "[QUAR] " + node.name + " mis en quarantaine";
            case PATCH         -> "[PATCH] Correctif appliqué sur " + node.name;
            case RESET_FIREWALL-> "[FW] Règles firewall réinitialisées pour " + node.ip;
            case CHANGE_VLAN   -> "[VLAN] " + node.name + " migré vers VLAN sécurisé";
        };
    }

    private static String buildDetails(ActionType action, TopologyNode node, long elapsed) {
        return switch (action) {
            case SCAN_PORTS -> String.format(
                "Cible : %s\n" +
                "Ports ouverts  : 22 (SSH), 80 (HTTP), 443 (HTTPS), 1883 (MQTT)\n" +
                "Ports filtrés  : 23 (Telnet), 8080, 8443\n" +
                "Ports fermés   : tous les autres\n" +
                "OS détecté     : Linux Embedded (TTL=64)\n" +
                "Durée du scan  : %dms",
                node.ip, elapsed);
            case PING -> String.format(
                "Cible      : %s\n" +
                "Résultat   : 4 paquets envoyés, 4 reçus (0%% pertes)\n" +
                "RTT min    : %dms  /  moy : %dms  /  max : %dms\n" +
                "TTL        : 64\n" +
                "Taille     : 64 bytes",
                node.ip,
                3 + (int)(Math.random() * 5),
                8 + (int)(Math.random() * 15),
                20 + (int)(Math.random() * 30));
            case TRACE_ROUTE -> String.format(
                "Trace vers %s :\n" +
                "  1  192.168.1.1      2ms  (Gateway)\n" +
                "  2  10.0.0.1         8ms  (FAI)\n" +
                "  3  %s    %dms  (Destination)\n" +
                "Durée totale : %dms",
                node.ip, node.ip, 10 + (int)(Math.random() * 20), elapsed);
            case SIMULATE_CRASH -> String.format(
                "Type de panne  : Matérielle (simulation)\n" +
                "Appareil       : %s (%s)\n" +
                "Impact réseau  : Perte des services exposés\n" +
                "Nœuds affectés : Dépend de la topologie\n" +
                "Recommandation : Redémarrer / remplacer l'appareil",
                node.name, node.ip);
            case SIMULATE_OVERLOAD -> String.format(
                "Type          : Surcharge CPU/RAM (simulation)\n" +
                "Appareil      : %s\n" +
                "CPU simulé    : %d%%  (seuil critique : 90%%)\n" +
                "RAM simulée   : %d%%\n" +
                "Impact        : Dégradation des performances\n" +
                "Action suggérée : Réduire la charge ou redémarrer",
                node.name,
                85 + (int)(Math.random() * 15),
                78 + (int)(Math.random() * 20));
            case PATCH -> String.format(
                "Correctif      : SEC-2025-%04d\n" +
                "CVE corrigé    : CVE-2024-%05d\n" +
                "Firmware avant : 1.0.0\n" +
                "Firmware après : 1.0.%d\n" +
                "Durée          : %dms\n" +
                "Résultat       : Succès — redémarrage requis",
                (int)(Math.random() * 9999),
                (int)(Math.random() * 99999),
                1 + (int)(Math.random() * 5),
                elapsed);
            case ISOLATE -> String.format(
                "Appareil isolé : %s (%s)\n" +
                "VLAN quarantaine : VLAN 999\n" +
                "Règles ajoutées  : DROP all inbound/outbound\n" +
                "Raison           : Comportement suspect détecté\n" +
                "Révision         : Requise avant réactivation",
                node.name, node.ip);
            case CHANGE_VLAN -> String.format(
                "Appareil   : %s\n" +
                "VLAN avant : VLAN 10 (default)\n" +
                "VLAN après : VLAN 20 (IoT-Secure)\n" +
                "ACL mises à jour  : 4 règles\n" +
                "Durée migration   : %dms",
                node.name, elapsed);
            default -> String.format(
                "Action     : %s\n" +
                "Appareil   : %s (%s)\n" +
                "Résultat   : %s\n" +
                "Durée      : %dms",
                action.label, node.name, node.ip,
                action.resultStatus != null ? action.resultStatus.label : "—",
                elapsed);
        };
    }

    private static void publishEvent(ActionType action, TopologyNode node, String msg) {
        SecurityEvent.Type evType = switch (action) {
            case BLOCK, ISOLATE   -> SecurityEvent.Type.FIREWALL_BLOCK;
            case SCAN_PORTS       -> SecurityEvent.Type.SCAN_RESULT;
            case ACTIVATE, RESTART-> SecurityEvent.Type.DEVICE_CONNECT;
            case SHUTDOWN, SIMULATE_CRASH, SIMULATE_DISCONNECT
                                  -> SecurityEvent.Type.DEVICE_DISCONNECT;
            default               -> SecurityEvent.Type.IDS_ALERT;
        };
        EventBus.getInstance().publish(new SecurityEvent(
            evType, action.severity, node.ip, msg));
    }

    /**
     * Calcule un score de robustesse pour un nœud selon son état actuel.
     * Score de 0 à 100.
     */
    public static int computeRobustnessScore(TopologyNode node) {
        return switch (node.status) {
            case ONLINE     -> 100;
            case SCANNING   -> 70;
            case SUSPICIOUS -> 40;
            case OFFLINE    -> 10;
            case BLOCKED    -> 0;
        };
    }
}
