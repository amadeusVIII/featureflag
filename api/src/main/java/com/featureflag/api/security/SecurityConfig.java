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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity          // enables @PreAuthorize on controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;


    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http

                .csrf(AbstractHttpConfigurer::disable)


                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))


                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — no token required
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // Actuator health endpoint — needed by Docker health checks
                        .requestMatchers("/actuator/health").permitAll()
                        // Swagger UI — useful during development
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // Flag evaluation endpoints — authenticated via API key (handled separately)
                        .requestMatchers("/api/v1/flags/**").permitAll()
                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )


                .addFilterBefore(jwtAuthFilter,
                        UsernamePasswordAuthenticationFilter.class);

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
        provider.setPasswordEncoder(passwordEncoder);  // use field, not method call
        return provider;
    }
}