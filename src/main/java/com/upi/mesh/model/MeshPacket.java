package com.upi.mesh.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Over-the-wire gossip packet. Hops from phone to phone via BLE.
 * Intermediate phones can read outer fields (for routing) but
 * CANNOT read ciphertext — it is encrypted with the server's RSA public key.
 */
public class MeshPacket {

    @NotBlank
    private String packetId;

    @Min(0)
    private int ttl;

    @NotNull
    private Long createdAt;

    @NotBlank
    private String ciphertext;

    public MeshPacket() {}

    public String getPacketId() { return packetId; }
    public void setPacketId(String p) { this.packetId = p; }

    public int getTtl() { return ttl; }
    public void setTtl(int t) { this.ttl = t; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long c) { this.createdAt = c; }

    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String c) { this.ciphertext = c; }
}
