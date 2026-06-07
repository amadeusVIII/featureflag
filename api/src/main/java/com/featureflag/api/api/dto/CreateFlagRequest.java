package com.featureflag.api.api.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateFlagRequest {

    @NotBlank(message = "Flag key is required")
    @Pattern(
            regexp = "^[a-z0-9-]+$",
            message = "Key must be lowercase letters, numbers, and hyphens only"
    )
    @Size(max = 100, message = "Key must be 100 characters or less")
    private String key;

    @NotBlank(message = "Flag name is required")
    @Size(max = 200, message = "Name must be 200 characters or less")
    private String name;

    private String description;

    @NotBlank(message = "Environment is required")
    private  String environment = "production";

    private boolean enabled = false;

    @Min(value = 0, message = "Rollout percentage must be 0-100")
    @Max(value = 100, message = "Rollout percentage must be 0-100")
    private int rolloutPercentage = 100;

    private String flagType = "BOOLEAN";

    private String stringValue;
}
