package tech.sarthakgoyal.bastionvault.config;

import java.io.IOException;
import java.util.Collections;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tech.sarthakgoyal.bastionvault.crypto.VaultSessionManager;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final VaultSessionManager sessionManager;

    public TokenAuthenticationFilter(VaultSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Skip token checks for the public token authentication endpoint itself
        if ("/api/v1/auth/token".equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String providedToken = authHeader.substring(7);

            // Validate against the transient session memory registry
            String authenticatedClientId = sessionManager.validateTokenAndGetClientId(providedToken);

            if (authenticatedClientId != null) {
                UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                        authenticatedClientId, null, Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }

        filterChain.doFilter(request, response);
    }
}