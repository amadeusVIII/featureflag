package com.featureflag.api.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class AuthResponse {
    private final String accessToken;
    private final String tokenType = "Bearer"; // always Bearer for JWTs
    private final String email;
    private final String role;
}