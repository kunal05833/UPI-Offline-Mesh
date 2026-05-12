package com.upi.mesh.service;

import com.upi.mesh.crypto.HybridCryptoService;
import com.upi.mesh.model.MeshPacket;
import com.upi.mesh.model.PaymentInstruction;
import com.upi.mesh.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Production bridge ingestion pipeline:
 *   1. Hash ciphertext → idempotency key
 *   2. Idempotency gate  (Redis SETNX / in-memory fallback)
 *   3. Decrypt           (RSA private key)
 *   4. Freshness check   (signedAt replay protection)
 *   5. Settle            (PIN verify + debit/credit @Transactional)
 */
@Service
public class BridgeIngestionService {

    private static final Logger log = LoggerFactory.getLogger(BridgeIngestionService.class);

    @Autowired private HybridCryptoService crypto;
    @Autowired private IdempotencyService idempotency;
    @Autowired private SettlementService settlement;

    @Value("${upi.mesh.packet-max-age-seconds:86400}")
    private long maxAgeSeconds;

    public IngestResult ingest(MeshPacket packet, String bridgeNodeId, int hopCount) {
        try {
            String packetHash = crypto.hashCiphertext(packet.getCiphertext());

            // ── Step 1: Idempotency gate ──
            if (!idempotency.claim(packetHash)) {
                log.info("DUPLICATE dropped: {}... from bridge={}",
                        packetHash.substring(0, 12), bridgeNodeId);
                return IngestResult.duplicate(packetHash);
            }

            // ── Step 2: Decrypt ──
            PaymentInstruction instruction;
            try {
                instruction = crypto.decrypt(packet.getCiphertext());
            } catch (Exception e) {
                log.warn("Decryption failed for {}: {}", packetHash.substring(0, 12), e.getMessage());
                return IngestResult.invalid(packetHash, "decryption_failed");
            }

            // ── Step 3: Freshness check (replay protection) ──
            long ageSeconds = (Instant.now().toEpochMilli() - instruction.getSignedAt()) / 1000;
            if (ageSeconds > maxAgeSeconds) {
                log.warn("Stale packet {}... age={}s", packetHash.substring(0, 12), ageSeconds);
                return IngestResult.invalid(packetHash, "stale_packet: age=" + ageSeconds + "s");
            }
            if (ageSeconds < -300) {
                return IngestResult.invalid(packetHash, "future_dated");
            }

            // ── Step 4: Settle (includes PIN verification) ──
            Transaction tx = settlement.settle(instruction, packetHash, bridgeNodeId, hopCount);
            return IngestResult.from(packetHash, tx);

        } catch (Exception e) {
            log.error("Ingestion error: {}", e.getMessage(), e);
            return IngestResult.invalid("?", "internal_error");
        }
    }

    public record IngestResult(
            String outcome,
            String packetHash,
            String reason,
            Long transactionId,
            String status
    ) {
        public static IngestResult from(String hash, Transaction tx) {
            if (tx.getStatus() == Transaction.Status.SETTLED) {
                return new IngestResult("SETTLED", hash, null, tx.getId(), "SETTLED");
            } else {
                return new IngestResult("REJECTED", hash, tx.getRejectReason(), tx.getId(), "REJECTED");
            }
        }

        public static IngestResult duplicate(String hash) {
            return new IngestResult("DUPLICATE_DROPPED", hash, null, null, null);
        }

        public static IngestResult invalid(String hash, String reason) {
            return new IngestResult("INVALID", hash, reason, null, null);
        }
    }
}
