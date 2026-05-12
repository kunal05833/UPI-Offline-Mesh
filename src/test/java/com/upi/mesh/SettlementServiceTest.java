package com.upi.mesh;

import com.upi.mesh.model.*;
import com.upi.mesh.repository.*;
import com.upi.mesh.service.SettlementService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class SettlementServiceTest {

    @Autowired SettlementService settlement;
    @Autowired AccountRepository accounts;
    @Autowired TransactionRepository transactions;

    // SHA-256 of "1234"
    private static final String PIN_1234 = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4";

    @BeforeEach
    void seedTestAccounts() {
        if (!accounts.existsByVpa("test-alice@upi")) {
            accounts.save(new Account("test-alice@upi", "Test Alice", new BigDecimal("5000.00"), PIN_1234));
            accounts.save(new Account("test-bob@upi",   "Test Bob",   new BigDecimal("1000.00"), PIN_1234));
        }
    }

    @Test
    void successfulTransfer() {
        PaymentInstruction inst = instruction("test-alice@upi", "test-bob@upi",
                new BigDecimal("100.00"), PIN_1234);

        Transaction tx = settlement.settle(inst, "hash-001", "bridge-test", 3);

        assertEquals(Transaction.Status.SETTLED, tx.getStatus());

        Account alice = accounts.findByVpaAndIsActiveTrue("test-alice@upi").orElseThrow();
        Account bob   = accounts.findByVpaAndIsActiveTrue("test-bob@upi").orElseThrow();
        assertEquals(new BigDecimal("4900.00"), alice.getBalance());
        assertEquals(new BigDecimal("1100.00"), bob.getBalance());
    }

    @Test
    void wrongPinIsRejected() {
        String wrongPin = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        PaymentInstruction inst = instruction("test-alice@upi", "test-bob@upi",
                new BigDecimal("100.00"), wrongPin);

        Transaction tx = settlement.settle(inst, "hash-002", "bridge-test", 2);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertEquals("invalid_pin", tx.getRejectReason());
    }

    @Test
    void insufficientBalanceIsRejected() {
        PaymentInstruction inst = instruction("test-alice@upi", "test-bob@upi",
                new BigDecimal("99999.00"), PIN_1234);

        Transaction tx = settlement.settle(inst, "hash-003", "bridge-test", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertEquals("insufficient_balance", tx.getRejectReason());
    }

    @Test
    void selfTransferIsRejected() {
        PaymentInstruction inst = instruction("test-alice@upi", "test-alice@upi",
                new BigDecimal("50.00"), PIN_1234);

        Transaction tx = settlement.settle(inst, "hash-004", "bridge-test", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertEquals("self_transfer_not_allowed", tx.getRejectReason());
    }

    @Test
    void belowMinAmountIsRejected() {
        PaymentInstruction inst = instruction("test-alice@upi", "test-bob@upi",
                new BigDecimal("0.50"), PIN_1234);

        Transaction tx = settlement.settle(inst, "hash-005", "bridge-test", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertTrue(tx.getRejectReason().startsWith("amount_too_low"));
    }

    @Test
    void unknownSenderIsRejected() {
        PaymentInstruction inst = instruction("ghost@upi", "test-bob@upi",
                new BigDecimal("100.00"), PIN_1234);

        Transaction tx = settlement.settle(inst, "hash-006", "bridge-test", 1);

        assertEquals(Transaction.Status.REJECTED, tx.getStatus());
        assertEquals("sender_not_found_or_inactive", tx.getRejectReason());
    }

    // ── Helper ─────────────────────────────────────────────────
    private PaymentInstruction instruction(String sender, String receiver,
                                           BigDecimal amount, String pinHash) {
        return new PaymentInstruction(sender, receiver, amount, pinHash,
                "test-nonce-" + System.nanoTime(), Instant.now().toEpochMilli());
    }
}
