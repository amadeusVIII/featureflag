// api/src/main/java/com/featureflag/api/api/AuthController.java
package com.featureflag.api.api;

import com.featureflag.api.api.dto.AuthResponse;
import com.featureflag.api.api.dto.LoginRequest;
import com.featureflag.api.api.dto.RegisterRequest;
import com.featureflag.api.domain.user.User;
import com.featureflag.api.domain.user.UserService;
import com.featureflag.api.security.JwtTokenProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController handles the two public authentication endpoints:
 *   POST /api/v1/auth/register  — create a new account
 *   POST /api/v1/auth/login     — get a JWT token
 *
 * These are the ONLY endpoints that don't require a JWT.
 * (Defined as public in SecurityConfig.)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final UserService userService;
    private final JwtTokenProvider jwtTokenProvider;
    // AuthenticationManager handles the actual username+password verification
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user account.
     * Returns 201 Created with a JWT so the user is immediately logged in.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        // @Valid triggers Jakarta Bean Validation (email format, password length)
        // If validation fails, Spring auto-returns 400 with error details

        User user = userService.register(request);

        // Issue a JWT immediately so the user doesn't have to log in separately
        String token = jwtTokenProvider.createToken(
                user.getEmail(), user.getRole().name());

        log.info("User registered: {}", user.getEmail());

        // 201 Created is semantically correct for resource creation
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(token, user.getEmail(), user.getRole().name()));
    }

    /**
     * Log in with email + password.
     * Returns 200 OK with a JWT token if credentials are valid.
     * Returns 401 Unauthorized if credentials are wrong.
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {
        try {
            // AuthenticationManager verifies email + password using our
            // DaoAuthenticationProvider (configured in SecurityConfig)
            // Internally it: loads user by email → checks bcrypt hash
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // If we reach here, authentication succeeded
            String email = authentication.getName();

            // Load the user to get their role for the token
            User user = userService.loadUserByUsername(email) instanceof
                    org.springframework.security.core.userdetails.User springUser
                    ? null : null; // We need the actual role

            // Load from DB to get the role
            // In a larger system you'd store the role in the JWT claims directly
            String role = authentication.getAuthorities()
                    .stream()
                    .findFirst()
                    .map(a -> a.getAuthority().replace("ROLE_", ""))
                    .orElse("VIEWER");

            String token = jwtTokenProvider.createToken(email, role);

            // Update last login timestamp asynchronously (non-critical)
            userService.updateLastLogin(email);

            log.info("User logged in: {}", email);

            return ResponseEntity.ok(new AuthResponse(token, email, role));

        } catch (AuthenticationException e) {
            // Wrong password, user not found — both return 401
            // We intentionally give the same error for both cases
            // (telling an attacker "that email doesn't exist" is an info leak)
            log.warn("Failed login attempt for: {}", request.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}