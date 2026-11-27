package tech.sarthakgoyal.bastionvault.service;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

import tech.sarthakgoyal.bastionvault.domain.AuditEvent.ActorType;
import tech.sarthakgoyal.bastionvault.domain.AuditEvent.Outcome;

/** Admin login with per-user and per-IP lockout (decision 6A) and audit of every attempt. */
public class LockoutAuthenticationProvider extends DaoAuthenticationProvider {

    private final AttemptLimiter limiter;
    private final AuditService audit;
    private final AdminAccountService accounts;

    public LockoutAuthenticationProvider(AdminAccountService accounts, PasswordEncoder encoder,
            AttemptLimiter limiter, AuditService audit) {
        super(accounts);
        setPasswordEncoder(encoder);
        this.accounts = accounts;
        this.limiter = limiter;
        this.audit = audit;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String username = String.valueOf(authentication.getName());
        String ip = authentication.getDetails() instanceof WebAuthenticationDetails d ? d.getRemoteAddress() : "unknown";
        String userKey = "admin:" + username;
        String ipKey = AttemptLimiter.ip(ip);
        try {
            limiter.begin(userKey, ipKey);
        } catch (TooManyAttemptsException e) {
            audit.record(ActorType.ANONYMOUS, username, "LOGIN", null, Outcome.DENIED, "locked out");
            throw new LockedException("Too many failed attempts. Try again later.");
        }
        boolean success = false;
        try {
            Authentication result = super.authenticate(authentication);
            success = true;
            accounts.recordLogin(username);
            audit.admin(username, "LOGIN", null, Outcome.SUCCESS);
            return result;
        } catch (BadCredentialsException e) {
            audit.record(ActorType.ANONYMOUS, username, "LOGIN", null, Outcome.FAILED, null);
            throw e;
        } finally {
            // Anything but a successful login (wrong password, disabled account) counts as a failure.
            limiter.end(success, userKey, ipKey);
            if (success) {
                limiter.reset(userKey);
            }
        }
    }
}
