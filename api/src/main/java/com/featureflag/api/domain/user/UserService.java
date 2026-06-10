package com.featureflag.api.domain.user;

import com.featureflag.api.api.dto.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService implements UserDetailsService  {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional
    public User register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered: " + request.getEmail());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.VIEWER)
                .createdAt(Instant.now())
                .build();
        User saved = userRepository.save(user);
        log.info("Registered new user: {}", saved.getEmail());

        return saved;

    }


    @Override
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException{
        User user = userRepository.findByEmail(email)
                .orElseThrow(()->{log.warn("User not found for email: {}", email);
                return new UsernameNotFoundException("User not found: " + email);
                });
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build();
    }


    @Transactional
    public void updateLastLogin (String email){
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setLastLogon(Instant.now());
            userRepository.save(user);
        });
    }


}
