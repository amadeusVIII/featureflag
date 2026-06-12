package com.featureflag.api.security;

import com.featureflag.api.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final ApiKeyAuthFilter apiKeyAuthFilter; // NEW — validates X-API-Key header
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CSRF disabled — we are a stateless REST API using JWT/API keys.
                // CSRF protection is for browser-based session cookies, which we don't use.
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless session — Spring Security will never create an HttpSession.
                // Every request must carry its own authentication (JWT or API key).
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token required
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // Docker and ECS health checks — must be reachable without auth
                        .requestMatchers("/actuator/health").permitAll()

                        // Swagger UI — useful during development
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

                        // Flag evaluation endpoints — require a valid API key (SDK clients)
                        // OR a valid JWT (admin users calling evaluate directly).
                        // Previously this was permitAll() — that meant ANYONE could query
                        // any flag in any environment with no credentials at all.
                        // Now it requires authentication: ApiKeyAuthFilter or JwtAuthFilter
                        // must have succeeded for this request to reach the controller.
                        .requestMatchers("/api/v1/flags/**").authenticated()

                        // Everything else (admin endpoints) requires authentication too
                        .anyRequest().authenticated()
                )

                // Register both filters. Both run on every request but each only acts
                // if its relevant header is present.
                //
                // Order: ApiKeyAuthFilter runs BEFORE JwtAuthFilter.
                // Reason: SDK clients send X-API-Key. Admin users send Authorization: Bearer.
                // Running API key first means SDK requests are authenticated before
                // JwtAuthFilter even looks at them — slightly more efficient.
                .addFilterBefore(apiKeyAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, ApiKeyAuthFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }
}
