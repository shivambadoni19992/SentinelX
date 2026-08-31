package com.sentinelx.auth.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.sentinelx.auth.domain.AccountStatus;
import com.sentinelx.auth.domain.Role;
import com.sentinelx.auth.entity.User;
import com.sentinelx.auth.repository.UserRepository;

/**
 * Seeds a set of development users on startup so the SOC console is usable out
 * of the box. Only active when the running profile is NOT {@code prod}.
 *
 * <p>Passwords are hashed with BCrypt at runtime then discarded; only hashes
 * are stored, and no password material is ever exposed through the API.
 */
@Component
@Profile("!prod")
public class DevUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public DevUserSeeder(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (DevUser dev : DevUser.values()) {
            if (users.findByUsername(dev.username).isPresent()) {
                continue;
            }
            User user = new User();
            user.setUsername(dev.username);
            user.setEmail(dev.email);
            user.setPasswordHash(passwordEncoder.encode(dev.password));
            user.setRole(dev.role.name());
            user.setAccountStatus(dev.status.name());
            users.save(user);
            log.info("Seeded dev user [{}] role={} status={}", dev.username, dev.role, dev.status);
        }
    }

    public enum DevUser {
        admin("admin", "admin@sentinelx.local", Role.ADMIN, AccountStatus.ACTIVE, "SentinelX!Dev1"),
        analyst("analyst", "analyst@sentinelx.local", Role.SOC_ANALYST, AccountStatus.ACTIVE, "SentinelX!Dev1"),
        engineer("engineer", "engineer@sentinelx.local", Role.SECURITY_ENGINEER, AccountStatus.ACTIVE, "SentinelX!Dev1"),
        support("support", "support@sentinelx.local", Role.SUPPORT, AccountStatus.ACTIVE, "SentinelX!Dev1"),
        auditor("auditor", "auditor@sentinelx.local", Role.AUDITOR, AccountStatus.ACTIVE, "SentinelX!Dev1"),
        monitored("monitored", "monitored@sentinelx.local", Role.SOC_ANALYST, AccountStatus.MONITORED, "SentinelX!Dev1"),
        blocked("blocked", "blocked@sentinelx.local", Role.SOC_ANALYST, AccountStatus.BLOCKED, "SentinelX!Dev1");

        public final String username;
        public final String email;
        public final Role role;
        public final AccountStatus status;
        public final String password;

        DevUser(String username, String email, Role role, AccountStatus status, String password) {
            this.username = username;
            this.email = email;
            this.role = role;
            this.status = status;
            this.password = password;
        }
    }
}