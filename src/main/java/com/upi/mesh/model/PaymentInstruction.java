package com.upi.mesh.model;

import java.math.BigDecimal;

public class PaymentInstruction {

    private String senderVpa;
    private String receiverVpa;
    private BigDecimal amount;
    private String pinHash;   // SHA-256 of UPI PIN
    private String nonce;     // UUID — unique per payment intent
    private Long   signedAt;  // epoch millis — replay protection

    public PaymentInstruction() {}

    public PaymentInstruction(String senderVpa, String receiverVpa, BigDecimal amount,
                              String pinHash, String nonce, Long signedAt) {
        this.senderVpa = senderVpa;
        this.receiverVpa = receiverVpa;
        this.amount = amount;
        this.pinHash = pinHash;
        this.nonce = nonce;
        this.signedAt = signedAt;
    }

    public String getSenderVpa()    { return senderVpa; }
    public void setSenderVpa(String s) { this.senderVpa = s; }

    public String getReceiverVpa()  { return receiverVpa; }
    public void setReceiverVpa(String r) { this.receiverVpa = r; }

    public BigDecimal getAmount()   { return amount; }
    public void setAmount(BigDecimal a) { this.amount = a; }

    public String getPinHash()      { return pinHash; }
    public void setPinHash(String p) { this.pinHash = p; }

    public String getNonce()        { return nonce; }
    public void setNonce(String n)  { this.nonce = n; }

    public Long getSignedAt()       { return signedAt; }
    public void setSignedAt(Long s) { this.signedAt = s; }
}
