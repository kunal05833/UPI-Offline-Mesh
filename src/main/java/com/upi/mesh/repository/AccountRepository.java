package com.upi.mesh.repository;

import com.upi.mesh.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, String> {

    @Lock(LockModeType.OPTIMISTIC)
    Optional<Account> findByVpaAndIsActiveTrue(String vpa);

    boolean existsByVpa(String vpa);
}
