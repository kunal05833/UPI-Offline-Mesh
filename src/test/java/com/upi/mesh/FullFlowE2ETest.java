package com.upi.mesh;

import com.upi.mesh.model.Transaction;
import com.upi.mesh.repository.*;
import com.upi.mesh.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Full end-to-end test:
 * Packet created → injected → gossip spread → bridge flush → settled
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowE2ETest {

    @Autowired DemoService demo;
    @Autowired MeshSimulatorService mesh;
    @Autowired BridgeIngestionService bridge;
    @Autowired IdempotencyService idempotency;
    @Autowired TransactionRepository txRepo;
    @Autowired AccountRepository accounts;

    @BeforeEach
    void reset() {
        mesh.resetMesh();
        idempotency.clear();
    }

    @Test @Order(1)
    void fullFlowSettlesExactlyOnce() throws Exception {
        // Step 1: alice creates encrypted packet (pin=1234)
        var packet = demo.createPacket("alice@upi", "bob@upi",
                new BigDecimal("500.00"), "1234", 5);
        assertNotNull(packet.getCiphertext());

        // Step 2: inject at phone-alice
        mesh.inject("phone-alice", packet);
        assertEquals(1, mesh.getDevice("phone-alice").packetCount());

        // Step 3: gossip spreads packet
        var gossipResult = mesh.gossipOnce();
        assertTrue(gossipResult.transfers() > 0, "Gossip should transfer packets");

        // Step 4: bridge flush — multiple bridges will attempt delivery
        var uploads = mesh.collectBridgeUploads();
        assertTrue(uploads.size() >= 1, "At least bridge node should have the packet");

        long settled = 0, duplicates = 0;
        for (var up : uploads) {
            var result = bridge.ingest(up.packet(), up.bridgeNodeId(), 3);
            if ("SETTLED".equals(result.outcome()))   settled++;
            if ("DUPLICATE_DROPPED".equals(result.outcome())) duplicates++;
        }

        // THE KEY PROPERTY: exactly 1 settlement regardless of how many bridges delivered
        assertEquals(1, settled, "Exactly ONE settlement expected");
        assertEquals(uploads.size() - 1, duplicates, "Rest must be duplicates");
    }

    @Test @Order(2)
    void wrongPinRejectedInE2E() throws Exception {
        var packet = demo.createPacket("alice@upi", "bob@upi",
                new BigDecimal("100.00"), "9999", 3); // wrong PIN
        mesh.inject("phone-alice", packet);
        mesh.gossipOnce();

        var uploads = mesh.collectBridgeUploads();
        long rejected = 0;
        for (var up : uploads) {
            var r = bridge.ingest(up.packet(), up.bridgeNodeId(), 2);
            if ("REJECTED".equals(r.outcome())) rejected++;
        }
        assertTrue(rejected >= 1, "Wrong PIN should be rejected");
    }
}
