package tech.sarthakgoyal.bastionvault.service;

import java.time.Instant;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.sarthakgoyal.bastionvault.domain.AdminUser;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;
import tech.sarthakgoyal.bastionvault.repository.AdminUserRepository;

/** UI accounts (decision 1A). Login is separate from unsealing. */
@Service
public class AdminAccountService implements UserDetailsService {

    private final AdminUserRepository admins;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;
    private final AttemptLimiter limiter;

    public AdminAccountService(AdminUserRepository admins, PasswordEncoder passwordEncoder, AuditService audit,
            AttemptLimiter limiter) {
        this.admins = admins;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.limiter = limiter;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AdminUser admin = admins.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user"));
        return User.withUsername(admin.getUsername())
                .password(admin.getPasswordHash())
                .disabled(!admin.isEnabled())
                .roles("ADMIN")
                .build();
    }

    @Transactional
    public void recordLogin(String username) {
        admins.findByUsername(username).ifPresent(a -> {
            a.setLastLoginAt(Instant.now());
            admins.save(a);
        });
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        AdminUser admin = admins.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Unknown user"));
        // Same lockout as sign-in, so a hijacked session cannot brute-force the current password here.
        String userKey = "admin:" + username;
        String ipKey = AttemptLimiter.ip(ClientIp.current());
        limiter.begin(userKey, ipKey);
        boolean ok = false;
        try {
            ok = currentPassword != null && passwordEncoder.matches(currentPassword, admin.getPasswordHash());
        } finally {
            limiter.end(ok, userKey, ipKey);
        }
        if (!ok) {
            audit.admin(username, "CHANGE_LOGIN_PASSWORD", username, Outcome.DENIED);
            throw new IllegalArgumentException("Current password is wrong");
        }
        Names.password(newPassword, "New password");
        admin.setPasswordHash(passwordEncoder.encode(newPassword));
        admins.save(admin);
        audit.admin(username, "CHANGE_LOGIN_PASSWORD", username, Outcome.SUCCESS);
    }
}
