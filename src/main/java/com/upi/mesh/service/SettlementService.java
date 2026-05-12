package com.upi.mesh.service;

import com.upi.mesh.model.Account;
import com.upi.mesh.model.PaymentInstruction;
import com.upi.mesh.model.Transaction;
import com.upi.mesh.repository.AccountRepository;
import com.upi.mesh.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Actual ledger update wrapped in @Transactional.
 * New in production version:
 *   - PIN hash verification against stored hash
 *   - Amount range validation
 *   - Inactive account check
 *   - rejectReason recorded on every rejection
 */
@Service
public class SettlementService {

    private static final Logger log = LoggerFactory.getLogger(SettlementService.class);

    @Autowired private AccountRepository accounts;
    @Autowired private TransactionRepository transactions;

    @Value("${upi.mesh.max-amount:100000}")
    private BigDecimal maxAmount;

    @Value("${upi.mesh.min-amount:1}")
    private BigDecimal minAmount;

    @Transactional
    public Transaction settle(PaymentInstruction instruction,
                              String packetHash, String bridgeNodeId, int hopCount) {

        // ── Validate amount range ──
        BigDecimal amount = instruction.getAmount();
        if (amount == null || amount.compareTo(minAmount) < 0) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "amount_too_low: min=" + minAmount);
        }
        if (amount.compareTo(maxAmount) > 0) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "amount_too_high: max=" + maxAmount);
        }

        // ── Load sender ──
        Account sender = accounts.findByVpaAndIsActiveTrue(instruction.getSenderVpa())
                .orElse(null);
        if (sender == null) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "sender_not_found_or_inactive");
        }

        // ── PIN verification ──
        if (!sender.getPinHash().equals(instruction.getPinHash())) {
            log.warn("PIN mismatch for sender {}", instruction.getSenderVpa());
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "invalid_pin");
        }

        // ── Load receiver ──
        Account receiver = accounts.findByVpaAndIsActiveTrue(instruction.getReceiverVpa())
                .orElse(null);
        if (receiver == null) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "receiver_not_found_or_inactive");
        }

        // ── Sender != Receiver ──
        if (sender.getVpa().equals(receiver.getVpa())) {
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "self_transfer_not_allowed");
        }

        // ── Balance check ──
        if (sender.getBalance().compareTo(amount) < 0) {
            log.warn("Insufficient balance: {} has ₹{}, tried ₹{}",
                    sender.getVpa(), sender.getBalance(), amount);
            return recordRejected(instruction, packetHash, bridgeNodeId, hopCount,
                    "insufficient_balance");
        }

        // ── Debit + Credit (atomic) ──
        sender.setBalance(sender.getBalance().subtract(amount));
        receiver.setBalance(receiver.getBalance().add(amount));
        accounts.save(sender);
        accounts.save(receiver);

        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa());
        tx.setReceiverVpa(instruction.getReceiverVpa());
        tx.setAmount(amount);
        tx.setSignedAt(Instant.ofEpochMilli(instruction.getSignedAt()));
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.SETTLED);
        transactions.save(tx);

        log.info("SETTLED ₹{} from {} to {} | bridge={} hops={}",
                amount, sender.getVpa(), receiver.getVpa(), bridgeNodeId, hopCount);
        return tx;
    }

    private Transaction recordRejected(PaymentInstruction instruction, String packetHash,
                                       String bridgeNodeId, int hopCount, String reason) {
        log.warn("REJECTED packet {} reason={}", packetHash.substring(0, 12) + "...", reason);
        Transaction tx = new Transaction();
        tx.setPacketHash(packetHash);
        tx.setSenderVpa(instruction.getSenderVpa() != null ? instruction.getSenderVpa() : "unknown");
        tx.setReceiverVpa(instruction.getReceiverVpa() != null ? instruction.getReceiverVpa() : "unknown");
        tx.setAmount(instruction.getAmount() != null ? instruction.getAmount() : BigDecimal.ZERO);
        tx.setSignedAt(instruction.getSignedAt() != null
                ? Instant.ofEpochMilli(instruction.getSignedAt()) : Instant.now());
        tx.setSettledAt(Instant.now());
        tx.setBridgeNodeId(bridgeNodeId);
        tx.setHopCount(hopCount);
        tx.setStatus(Transaction.Status.REJECTED);
        tx.setRejectReason(reason);
        return transactions.save(tx);
    }
}
