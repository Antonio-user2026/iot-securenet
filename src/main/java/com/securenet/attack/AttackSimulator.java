package com.securenet.attack;

import com.securenet.events.EventBus;
import com.securenet.events.SecurityEvent;
import com.securenet.twin.TopologyNode;

import java.util.*;
import java.util.function.Consumer;

/**
 * Moteur de simulation d'attaques réseau pour le Digital Twin.
 * Simule visuellement différents types d'attaques IoT avec effets animés
 * et injection d'événements dans l'EventBus (IDS/SIEM reçoivent les alertes).
 */
public class AttackSimulator {

    // ══════════════════════════════════════════════════════════════════════
    //  Catalogue des attaques disponibles
    // ══════════════════════════════════════════════════════════════════════

    public enum AttackType {
        PORT_SCAN         ("Port Scan",           "Scan des ports ouverts",
                           SecurityEvent.Severity.WARNING,  3000, 8),
        SYN_FLOOD         ("SYN Flood (DDoS)",    "Inondation TCP SYN",
                           SecurityEvent.Severity.CRITICAL, 5000, 20),
        BRUTE_FORCE_SSH   ("Brute Force SSH",     "Attaque par dictionnaire SSH",
                           SecurityEvent.Severity.CRITICAL, 4000, 15),
        ARP_SPOOFING      ("ARP Spoofing",        "Empoisonnement de table ARP",
                           SecurityEvent.Severity.CRITICAL, 3500, 10),
        MQTT_INJECTION    ("Injection MQTT",      "Injection de payload MQTT",
                           SecurityEvent.Severity.WARNING,  2500, 6),
        DNS_SPOOFING      ("DNS Spoofing",        "Falsification de réponses DNS",
                           SecurityEvent.Severity.CRITICAL, 3000, 8),
        TELNET_INTRUSION  ("Intrusion Telnet",    "Connexion Telnet non autorisée",
                           SecurityEvent.Severity.CRITICAL, 2000, 5),
        DDOS_UDP          ("UDP Flood (DDoS)",    "Attaque par amplification UDP",
                           SecurityEvent.Severity.CRITICAL, 5000, 25),
        MAN_IN_THE_MIDDLE ("Man-in-the-Middle",   "Interception du trafic",
                           SecurityEvent.Severity.CRITICAL, 4500, 12),
        FIRMWARE_EXPLOIT  ("Exploit Firmware",   "Exploitation de vulnérabilité firmware",
                           SecurityEvent.Severity.CRITICAL, 6000, 18),
        REPLAY_ATTACK     ("Replay Attack",       "Rejeu de paquets authentifiés",
                           SecurityEvent.Severity.WARNING,  2500, 7),
        CREDENTIAL_STUFFING("Credential Stuffing","Bourrage d'identifiants volés",
                           SecurityEvent.Severity.WARNING,  3500, 10);

        public final String  label;
        public final String  description;
        public final SecurityEvent.Severity severity;
        public final int     durationMs;    // durée totale de la simulation
        public final int     eventCount;    // nombre d'événements générés

        AttackType(String label, String description, SecurityEvent.Severity severity,
                   int durationMs, int eventCount) {
            this.label       = label;
            this.description = description;
            this.severity    = severity;
            this.durationMs  = durationMs;
            this.eventCount  = eventCount;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  État de la simulation en cours
    // ══════════════════════════════════════════════════════════════════════

    public static class AttackState {
        public final AttackType type;
        public final TopologyNode target;
        public int    eventsGenerated = 0;
        public int    progress        = 0;   // 0-100
        public boolean finished       = false;
        public String  phase          = "Initialisation...";

        AttackState(AttackType type, TopologyNode target) {
            this.type   = type;
            this.target = target;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API
    // ══════════════════════════════════════════════════════════════════════

    private Consumer<AttackState> onProgress;
    private Consumer<AttackState> onFinished;
    private volatile boolean      cancelled = false;

    public void setOnProgress(Consumer<AttackState> cb) { this.onProgress = cb; }
    public void setOnFinished(Consumer<AttackState> cb) { this.onFinished = cb; }

    /**
     * Lance une simulation d'attaque sur le nœud cible.
     * Non-bloquant (thread daemon).
     */
    public void simulate(AttackType type, TopologyNode target) {
        cancelled = false;
        AttackState state = new AttackState(type, target);

        Thread t = new Thread(() -> runSimulation(state));
        t.setDaemon(true);
        t.start();
    }

    public void cancel() { cancelled = true; }

    // ══════════════════════════════════════════════════════════════════════
    //  Moteur de simulation
    // ══════════════════════════════════════════════════════════════════════

    private void runSimulation(AttackState state) {
        AttackType type   = state.type;
        TopologyNode node = state.target;
        String targetIP   = node.ip;

        String[] phases = buildPhases(type);
        int intervalMs   = type.durationMs / (phases.length + type.eventCount);

        // Phase 1 : préparation
        for (String phase : phases) {
            if (cancelled) { finish(state, true); return; }
            state.phase    = phase;
            state.progress = (int)(100.0 * state.eventsGenerated / type.eventCount);
            notifyProgress(state);
            sleep(intervalMs);
        }

        // Phase 2 : génération d'événements réseau
        Random rnd = new Random();
        String[] ports    = attackPorts(type);
        String[] payloads = attackPayloads(type);

        for (int i = 0; i < type.eventCount && !cancelled; i++) {
            String payload = payloads[i % payloads.length];

            // Publier sur l'EventBus → IDS + SIEM reçoivent l'alerte
            SecurityEvent event = new SecurityEvent(
                SecurityEvent.Type.IDS_ALERT,
                type.severity,
                targetIP,
                type.label + " — " + payload
            );
            EventBus.getInstance().publish(event);

            state.eventsGenerated++;
            state.progress = (int)(100.0 * state.eventsGenerated / type.eventCount);
            state.phase    = String.format("Attaque en cours... [%d/%d] %s",
                state.eventsGenerated, type.eventCount, payload);

            // Marquer le nœud comme suspect visuellement
            if (node.status == TopologyNode.Status.ONLINE) {
                node.status = TopologyNode.Status.SUSPICIOUS;
            }

            notifyProgress(state);
            sleep(intervalMs + rnd.nextInt(80));
        }

        // Phase 3 : conclusion
        state.phase    = cancelled ? "Simulation annulée." : "Simulation terminée.";
        state.progress = 100;
        state.finished = true;

        // Si critique : bloquer automatiquement le nœud
        if (!cancelled && type.severity == SecurityEvent.Severity.CRITICAL) {
            node.status = TopologyNode.Status.BLOCKED;
            // Événement de blocage
            EventBus.getInstance().publish(new SecurityEvent(
                SecurityEvent.Type.FIREWALL_BLOCK,
                SecurityEvent.Severity.CRITICAL,
                targetIP,
                "IP " + targetIP + " bloquée suite à " + type.label));
        }

        finish(state, cancelled);
    }

    private String[] buildPhases(AttackType type) {
        return switch (type) {
            case PORT_SCAN -> new String[]{
                "Envoi de paquets SYN sur chaque port...",
                "Analyse des réponses RST/SYN-ACK...",
                "Cartographie des services exposés..."};
            case SYN_FLOOD -> new String[]{
                "Génération des IPs source spoofées...",
                "Envoi massif de paquets SYN...",
                "Saturation de la table de connexions...",
                "Épuisement des ressources..."};
            case BRUTE_FORCE_SSH -> new String[]{
                "Chargement du dictionnaire de mots de passe...",
                "Tentatives d'authentification en série...",
                "Analyse des délais de réponse..."};
            case ARP_SPOOFING -> new String[]{
                "Envoi de fausses réponses ARP gratuits...",
                "Empoisonnement du cache ARP...",
                "Interception du trafic en cours..."};
            case MQTT_INJECTION -> new String[]{
                "Connexion au broker MQTT sans auth...",
                "Publication sur tous les topics...",
                "Injection de commandes malveillantes..."};
            case MAN_IN_THE_MIDDLE -> new String[]{
                "ARP spoofing en cours...",
                "Redirection du trafic...",
                "Déchiffrement SSL (MITM)...",
                "Capture des identifiants..."};
            case FIRMWARE_EXPLOIT -> new String[]{
                "Analyse de la version firmware...",
                "Recherche de CVE applicables...",
                "Exploitation du buffer overflow...",
                "Injection de shellcode..."};
            default -> new String[]{
                "Préparation de l'attaque...",
                "Envoi des paquets malveillants..."};
        };
    }

    private String[] attackPorts(AttackType type) {
        return switch (type) {
            case PORT_SCAN   -> new String[]{"22","23","80","443","1883","8080","8443","5683"};
            case SYN_FLOOD   -> new String[]{"80","443","8080"};
            case BRUTE_FORCE_SSH -> new String[]{"22"};
            case MQTT_INJECTION  -> new String[]{"1883","8883"};
            case TELNET_INTRUSION -> new String[]{"23"};
            default          -> new String[]{"ANY"};
        };
    }

    private String[] attackPayloads(AttackType type) {
        return switch (type) {
            case PORT_SCAN  -> new String[]{
                "SYN→22 (SSH)",  "SYN→23 (Telnet)", "SYN→80 (HTTP)",
                "SYN→443 (HTTPS)","SYN→1883 (MQTT)","SYN→8080 (Alt-HTTP)"};
            case SYN_FLOOD  -> new String[]{
                "SYN flood 10Kpps","SYN flood 25Kpps","SYN flood 50Kpps",
                "Saturation 100K connexions"};
            case BRUTE_FORCE_SSH -> new String[]{
                "root:admin","admin:password","pi:raspberry",
                "admin:123456","root:toor","user:1234"};
            case ARP_SPOOFING -> new String[]{
                "ARP Reply: GW=attacker","Gratuitous ARP broadcast",
                "Cache poisoned: 192.168.1.1"};
            case MQTT_INJECTION -> new String[]{
                "PUBLISH /cmd/actuator ON","PUBLISH /sensor/temp 999",
                "SUBSCRIBE #  (wildcard)","PUBLISH /lock/open 1"};
            case MAN_IN_THE_MIDDLE -> new String[]{
                "Intercepté: credentials","Intercepté: session cookie",
                "Downgrade HTTPS→HTTP","Capture: private key"};
            case FIRMWARE_EXPLOIT -> new String[]{
                "CVE-2023-1234: RCE","Buffer overflow @ 0x0804a000",
                "Shellcode injecté","Root shell obtenu !"};
            case DNS_SPOOFING -> new String[]{
                "DNS spoof: api.vendor.com","DNS spoof: ota.update.com",
                "Redirect to 10.0.0.666"};
            case CREDENTIAL_STUFFING -> new String[]{
                "admin:leaked_pass_1","root:breach_2023_pw",
                "user@device:rockyou_entry"};
            default -> new String[]{"Paquet malveillant envoyé","Exploit tenté"};
        };
    }

    private void notifyProgress(AttackState state) {
        if (onProgress != null) onProgress.accept(state);
    }

    private void finish(AttackState state, boolean cancelled) {
        state.finished = true;
        if (onFinished != null) onFinished.accept(state);
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
