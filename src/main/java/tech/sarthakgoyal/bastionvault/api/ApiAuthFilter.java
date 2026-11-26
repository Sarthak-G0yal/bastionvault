package tech.sarthakgoyal.bastionvault.api;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tech.sarthakgoyal.bastionvault.service.AttemptLimiter;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.TokenService;
import tech.sarthakgoyal.bastionvault.service.TooManyAttemptsException;

/**
 * Authenticates /api/** requests from {@code Authorization: Bearer <credential>}, where the
 * credential is a session token ({@code bvt_}) or an API key ({@code bvk_}). Bad credentials count
 * against the caller's IP lockout. Registered only in the API filter chain, not as a servlet filter.
 */
public class ApiAuthFilter extends OncePerRequestFilter {

    private final TokenService tokens;
    private final ClientService clients;
    private final AttemptLimiter limiter;

    public ApiAuthFilter(TokenService tokens, ClientService clients, AttemptLimiter limiter) {
        this.tokens = tokens;
        this.clients = clients;
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String ipKey = AttemptLimiter.ip(request.getRemoteAddr());
            try {
                limiter.assertAllowed(ipKey);
            } catch (TooManyAttemptsException e) {
                ApiErrors.write(response, 429, e.getMessage());
                return;
            }
            String credential = header.substring(7).trim();
            Optional<String> clientId = credential.startsWith(ClientService.API_KEY_PREFIX)
                    ? clients.authenticateApiKey(credential)
                    : tokens.validate(credential);
            if (clientId.isPresent()) {
                var auth = new UsernamePasswordAuthenticationToken(clientId.get(), null,
                        List.of(new SimpleGrantedAuthority("ROLE_CLIENT")));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                limiter.recordFailure(ipKey);
            }
        }
        chain.doFilter(request, response);
    }
}
