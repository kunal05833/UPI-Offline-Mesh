package com.upi.mesh.service;

import com.upi.mesh.model.Account;
import com.upi.mesh.model.AppUser;
import com.upi.mesh.repository.AccountRepository;
import com.upi.mesh.repository.AppUserRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Seeds demo accounts and default users on first startup.
 * Uses JPA save() which is idempotent via existsById checks.
 * Works with H2 (dev) and PostgreSQL (prod) without any SQL differences.
 */
@Service
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    // SHA-256 of "1234"
    private static final String PIN_1234 =
            "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4";

    @Autowired private AccountRepository accounts;
    @Autowired private AppUserRepository users;
    @Autowired private PasswordEncoder passwordEncoder;

    @PostConstruct
    @Transactional
    public void seed() {
        seedAccounts();
        seedUsers();
    }

    private void seedAccounts() {
        if (accounts.count() > 0) return;

        accounts.save(new Account("alice@upi",    "Alice Sharma",  new BigDecimal("10000.00"), PIN_1234));
        accounts.save(new Account("bob@upi",      "Bob Verma",     new BigDecimal("5000.00"),  PIN_1234));
        accounts.save(new Account("carol@upi",    "Carol Singh",   new BigDecimal("7500.00"),  PIN_1234));
        accounts.save(new Account("dave@upi",     "Dave Patel",    new BigDecimal("3000.00"),  PIN_1234));
        accounts.save(new Account("merchant@upi", "Demo Merchant", BigDecimal.ZERO,            PIN_1234));

        log.info("Seeded 5 demo accounts (PIN: 1234 for all)");
    }

    private void seedUsers() {
        if (users.count() > 0) return;

        String bcrypt = passwordEncoder.encode("admin123");

        AppUser admin = new AppUser();
        admin.setUsername("admin");
        admin.setPassword(bcrypt);
        admin.setRole("ROLE_ADMIN");
        users.save(admin);

        AppUser bridge = new AppUser();
        bridge.setUsername("bridge1");
        bridge.setPassword(bcrypt);
        bridge.setRole("ROLE_BRIDGE");
        users.save(bridge);

        log.info("Seeded 2 users: admin (ROLE_ADMIN), bridge1 (ROLE_BRIDGE) — password: admin123");
    }
}
