package com.featureflag.api.security;

import com.featureflag.api.domain.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;


@Component
@RequiredArgsConstructor
@Slf4j
public class ApiKeyAuthFilter extends OncePerRequestFilter {


    static final String API_KEY_HEADER = "X-API-Key";

    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);


        if (apiKey == null || apiKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }


        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }


        userRepository.findByApiKey(apiKey).ifPresentOrElse(
                user -> {

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user.getEmail(),
                                    null, // no credentials needed after API key is validated
                                    List.of(new SimpleGrantedAuthority("ROLE_SDK_CLIENT"))
                            );
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    log.debug("Authenticated SDK client via API key for user: {}", user.getEmail());
                },
                () -> {

                    log.warn("Invalid API key provided — key not found in database. " +
                            "Request path: {}", request.getRequestURI());
                }
        );

        filterChain.doFilter(request, response);
    }
}
