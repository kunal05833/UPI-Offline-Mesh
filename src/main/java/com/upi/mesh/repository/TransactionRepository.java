package com.upi.mesh.repository;

import com.upi.mesh.model.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    List<Transaction> findTop50ByOrderByIdDesc();

    long countByStatus(Transaction.Status status);

    @Query("SELECT AVG(t.hopCount) FROM Transaction t WHERE t.status = 'SETTLED'")
    Double findAvgHopCountSettled();

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.status = 'SETTLED'")
    java.math.BigDecimal totalSettledAmount();

    boolean existsByPacketHash(String packetHash);
}
