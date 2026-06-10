package com.featureflag.api.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;


@Component
@Slf4j
public class JwtTokenProvider {

    private final SecretKey signingKey;
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    public JwtTokenProvider(@Value("${jwt.secret}") String secret) {

        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8));
    }


    public String createToken (String email,String role ){

        Instant now = Instant.now();
        Instant expiry = now.plusMillis(EXPIRATION_MS);

        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    public  String getEmailFromToken (String token){

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getSubject();
        }catch (JwtException | IllegalArgumentException e){
            log.debug("Invalid JWT token: {}", e.getMessage());
            return null;
        }
    }


    public boolean isTokenValid(String token) {
        return getEmailFromToken(token) != null;
    }
}
