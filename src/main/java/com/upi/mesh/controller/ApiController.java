package com.upi.mesh.controller;

import com.upi.mesh.crypto.ServerKeyHolder;
import com.upi.mesh.model.*;
import com.upi.mesh.repository.*;
import com.upi.mesh.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Production REST API.
 *
 * Endpoint groups:
 *   /api/auth/*          → login (public)
 *   /api/server-key      → RSA public key (public — phone fetches this offline)
 *   /api/bridge/ingest   → bridge node upload (ROLE_BRIDGE or ADMIN, rate-limited)
 *   /api/mesh/*          → simulation controls (ROLE_ADMIN only)
 *   /api/demo/send       → demo packet creation (ROLE_ADMIN only)
 *   /api/accounts        → ledger read (authenticated)
 *   /api/transactions    → tx history (authenticated)
 *   /api/stats           → dashboard metrics (authenticated)
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    @Autowired private ServerKeyHolder serverKey;
    @Autowired private DemoService demo;
    @Autowired private MeshSimulatorService mesh;
    @Autowired private BridgeIngestionService bridge;
    @Autowired private AccountRepository accountRepo;
    @Autowired private TransactionRepository txRepo;
    @Autowired private IdempotencyService idempotency;
    @Autowired private RateLimitService rateLimit;

    // ─────────────────────────── Public ────────────────────────────

    /** Phone devices fetch this key before going offline */
    @GetMapping("/server-key")
    public Map<String, String> getServerPublicKey() {
        return Map.of(
                "publicKey", serverKey.getPublicKeyBase64(),
                "algorithm", "RSA-2048/OAEP-SHA256",
                "hybridScheme", "RSA-OAEP wraps AES-256-GCM session key"
        );
    }

    // ─────────────────────────── Bridge (ROLE_BRIDGE) ──────────────

    /**
     * THE PRODUCTION ENDPOINT.
     * Android bridge node POSTs here whenever it has internet + held packets.
     * Requires: Authorization: Bearer <jwt>
     */
    @PostMapping("/bridge/ingest")
    @PreAuthorize("hasAnyRole('BRIDGE', 'ADMIN')")
    public ResponseEntity<?> ingest(
            @Valid @RequestBody MeshPacket packet,
            @RequestHeader(value = "X-Bridge-Node-Id", defaultValue = "unknown") String bridgeNodeId,
            @RequestHeader(value = "X-Hop-Count",      defaultValue = "0")       int hopCount,
            HttpServletRequest req) {

        // Rate limit per bridge node ID
        rateLimit.checkBridgeLimit(bridgeNodeId);

        BridgeIngestionService.IngestResult r = bridge.ingest(packet, bridgeNodeId, hopCount);
        return ResponseEntity.ok(r);
    }

    // ─────────────────────────── Admin: Demo ───────────────────────

    @PostMapping("/demo/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> demoSend(@Valid @RequestBody DemoSendRequest req) throws Exception {
        if (req.senderVpa().equals(req.receiverVpa())) {
            throw new IllegalArgumentException("Sender and receiver cannot be same");
        }
        MeshPacket packet = demo.createPacket(
                req.senderVpa(), req.receiverVpa(), req.amount(), req.pin(),
                req.ttl() != null ? req.ttl() : 5);

        String startDevice = req.startDevice() != null ? req.startDevice() : "phone-alice";
        mesh.inject(startDevice, packet);

        return ResponseEntity.ok(Map.of(
                "packetId", packet.getPacketId(),
                "ciphertextPreview", packet.getCiphertext().substring(0, 64) + "...",
                "ttl", packet.getTtl(),
                "injectedAt", startDevice
        ));
    }

    // ─────────────────────────── Admin: Mesh Sim ──────────────────

    @GetMapping("/mesh/state")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> meshState() {
        List<Map<String, Object>> deviceData = new ArrayList<>();
        for (var d : mesh.getDevices()) {
            deviceData.add(Map.of(
                    "deviceId", d.getDeviceId(),
                    "hasInternet", d.hasInternet(),
                    "packetCount", d.packetCount(),
                    "packetIds", d.getHeldPackets().stream()
                            .map(p -> p.getPacketId().substring(0, 8)).toList()
            ));
        }
        return Map.of("devices", deviceData, "idempotencyCacheSize", idempotency.size());
    }

    @PostMapping("/mesh/gossip")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> meshGossip() {
        var r = mesh.gossipOnce();
        return Map.of("transfers", r.transfers(), "deviceCounts", r.deviceCounts());
    }

    @PostMapping("/mesh/flush")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> meshFlush() {
        var uploads = mesh.collectBridgeUploads();
        List<Map<String, Object>> results = Collections.synchronizedList(new ArrayList<>());

        uploads.parallelStream().forEach(up -> {
            var r = bridge.ingest(up.packet(), up.bridgeNodeId(), 5 - up.packet().getTtl());
            results.add(Map.of(
                    "bridgeNode",    up.bridgeNodeId(),
                    "packetId",      up.packet().getPacketId().substring(0, 8),
                    "outcome",       r.outcome(),
                    "reason",        r.reason() == null ? "" : r.reason(),
                    "transactionId", r.transactionId() == null ? -1 : r.transactionId()
            ));
        });

        return Map.of("uploadsAttempted", uploads.size(), "results", results);
    }

    @PostMapping("/mesh/reset")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> meshReset() {
        mesh.resetMesh();
        idempotency.clear();
        return Map.of("status", "mesh and idempotency cache cleared");
    }

    // ─────────────────────────── Ledger (authenticated) ───────────

    @GetMapping("/accounts")
    public List<Account> listAccounts() {
        return accountRepo.findAll();
    }

    @GetMapping("/transactions")
    public List<Transaction> listTransactions() {
        return txRepo.findTop50ByOrderByIdDesc();
    }

    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        long total    = txRepo.count();
        long settled  = txRepo.countByStatus(Transaction.Status.SETTLED);
        long rejected = txRepo.countByStatus(Transaction.Status.REJECTED);
        Double avgHops = txRepo.findAvgHopCountSettled();
        BigDecimal totalAmt = txRepo.totalSettledAmount();

        return Map.of(
                "totalTransactions", total,
                "settled",           settled,
                "rejected",          rejected,
                "idempotencyCacheSize", idempotency.size(),
                "duplicatesCaught",  Math.max(0, idempotency.size() - total),
                "avgHopsSettled",    avgHops != null
                        ? new BigDecimal(avgHops).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                "totalSettledAmount", totalAmt != null ? totalAmt : BigDecimal.ZERO,
                "meshPackets",       mesh.getDevices().stream().mapToInt(VirtualDevice::packetCount).sum()
        );
    }

    // ─────────────────────────── DTOs ─────────────────────────────

    public record DemoSendRequest(
            @NotBlank String senderVpa,
            @NotBlank String receiverVpa,
            @NotNull @DecimalMin("1") @DecimalMax("100000") BigDecimal amount,
            @NotBlank String pin,
            Integer ttl,
            String startDevice
    ) {}
}
