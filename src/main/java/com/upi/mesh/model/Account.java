package com.upi.mesh.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @NotBlank
    private String vpa;

    @Column(nullable = false)
    @NotBlank
    private String holderName;

    @Column(nullable = false, precision = 19, scale = 2)
    @DecimalMin("0.00")
    private BigDecimal balance;

    /** SHA-256 of the user's UPI PIN — never stored in plaintext */
    @Column(nullable = false, length = 64)
    private String pinHash;

    @Column(nullable = false)
    private boolean isActive = true;

    @Version
    private Long version;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public Account() {}

    public Account(String vpa, String holderName, BigDecimal balance, String pinHash) {
        this.vpa = vpa;
        this.holderName = holderName;
        this.balance = balance;
        this.pinHash = pinHash;
    }

    // Getters / Setters
    public String getVpa() { return vpa; }
    public void setVpa(String vpa) { this.vpa = vpa; }

    public String getHolderName() { return holderName; }
    public void setHolderName(String h) { this.holderName = h; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal b) { this.balance = b; }

    public String getPinHash() { return pinHash; }
    public void setPinHash(String p) { this.pinHash = p; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean a) { this.isActive = a; }

    public Long getVersion() { return version; }
    public void setVersion(Long v) { this.version = v; }

    public Instant getCreatedAt() { return createdAt; }
}
