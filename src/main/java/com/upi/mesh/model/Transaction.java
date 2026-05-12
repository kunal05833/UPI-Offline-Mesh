package com.upi.mesh.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "transactions",
        indexes = {
            @Index(name = "idx_packet_hash",  columnList = "packetHash", unique = true),
            @Index(name = "idx_tx_sender",    columnList = "senderVpa"),
            @Index(name = "idx_tx_receiver",  columnList = "receiverVpa"),
            @Index(name = "idx_tx_status",    columnList = "status"),
            @Index(name = "idx_tx_settled",   columnList = "settledAt")
        })
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String packetHash;

    @Column(nullable = false)
    private String senderVpa;

    @Column(nullable = false)
    private String receiverVpa;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant signedAt;

    @Column(nullable = false)
    private Instant settledAt;

    @Column(nullable = false)
    private String bridgeNodeId;

    @Column(nullable = false)
    private int hopCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column
    private String rejectReason;

    public enum Status { SETTLED, REJECTED }

    public Transaction() {}

    // ── Getters / Setters ──
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPacketHash() { return packetHash; }
    public void setPacketHash(String h) { this.packetHash = h; }

    public String getSenderVpa() { return senderVpa; }
    public void setSenderVpa(String s) { this.senderVpa = s; }

    public String getReceiverVpa() { return receiverVpa; }
    public void setReceiverVpa(String r) { this.receiverVpa = r; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }

    public Instant getSignedAt() { return signedAt; }
    public void setSignedAt(Instant i) { this.signedAt = i; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant i) { this.settledAt = i; }

    public String getBridgeNodeId() { return bridgeNodeId; }
    public void setBridgeNodeId(String b) { this.bridgeNodeId = b; }

    public int getHopCount() { return hopCount; }
    public void setHopCount(int h) { this.hopCount = h; }

    public Status getStatus() { return status; }
    public void setStatus(Status s) { this.status = s; }

    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String r) { this.rejectReason = r; }
}
