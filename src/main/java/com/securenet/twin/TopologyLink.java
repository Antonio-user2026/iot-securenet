package com.securenet.twin;

/**
 * Lien de connexion entre deux nœuds dans la topologie.
 */
public class TopologyLink {
    public int     id;
    public int     sourceId;
    public int     targetId;
    public String  linkType;   // ethernet, wifi, zigbee, mqtt
    public int     bandwidth;  // Mbps
    public boolean isAlert;    // rouge si attaque détectée sur ce lien

    public TopologyLink(int id, int sourceId, int targetId,
                        String linkType, int bandwidth) {
        this.id        = id;
        this.sourceId  = sourceId;
        this.targetId  = targetId;
        this.linkType  = linkType;
        this.bandwidth = bandwidth;
        this.isAlert   = false;
    }
}
