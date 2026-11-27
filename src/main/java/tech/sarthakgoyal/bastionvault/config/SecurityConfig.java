package tech.sarthakgoyal.bastionvault.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.vaadin.flow.spring.security.VaadinSecurityConfigurer;

import tech.sarthakgoyal.bastionvault.api.ApiAuthFilter;
import tech.sarthakgoyal.bastionvault.service.AdminAccountService;
import tech.sarthakgoyal.bastionvault.service.AttemptLimiter;
import tech.sarthakgoyal.bastionvault.service.AuditService;
import tech.sarthakgoyal.bastionvault.service.ClientService;
import tech.sarthakgoyal.bastionvault.service.LockoutAuthenticationProvider;
import tech.sarthakgoyal.bastionvault.service.TokenService;
import tech.sarthakgoyal.bastionvault.ui.views.LoginView;

/**
 * Two independent filter chains:
 * <ol>
 * <li>/api/** - stateless, Bearer token or API key, no sessions, no CSRF (machines only).</li>
 * <li>everything else - the Vaadin UI behind a real per-browser form login (decision 1A).
 * Logging in does not unseal the vault; that is a separate step.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, TokenService tokens, ClientService clients,
            AttemptLimiter limiter) throws Exception {
        http.securityMatcher("/api/**")
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/token").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/health", "/api/v1/health/ready").permitAll()
                        .anyRequest().hasRole("CLIENT"))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> writeError(res, 401, "Authentication required"))
                        .accessDeniedHandler((req, res, ex) -> writeError(res, 403, "Access denied")))
                .addFilterBefore(new ApiAuthFilter(tokens, clients, limiter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain uiChain(HttpSecurity http, AdminAccountService accounts, PasswordEncoder encoder,
            AttemptLimiter limiter, AuditService audit) throws Exception {
        http.authenticationProvider(new LockoutAuthenticationProvider(accounts, encoder, limiter, audit))
                .headers(h -> h
                        .referrerPolicy(r -> r.policy(ReferrerPolicy.NO_REFERRER))
                        .frameOptions(f -> f.deny()))
                .authorizeHttpRequests(auth -> auth.requestMatchers("/styles.css", "/fonts/**", "/icons/**").permitAll())
                .with(VaadinSecurityConfigurer.vaadin(), vaadin -> vaadin.loginView(LoginView.class));
        return http.build();
    }

    private static void writeError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
