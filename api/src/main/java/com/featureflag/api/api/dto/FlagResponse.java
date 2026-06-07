package com.featureflag.api.api.dto;

import com.featureflag.api.domain.flag.Flag;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FlagResponse {


    private UUID id;
    private String key;
    private String name;
    private String description;
    private boolean enabled;
    private String environment;
    private int rolloutPercentage;
    private String flagType;
    private String stringValue;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;


    public FlagResponse(Flag flag) {
        this.id = flag.getId();
        this.key = flag.getKey();
        this.name = flag.getName();
        this.description = flag.getDescription();
        this.enabled = flag.isEnabled();
        this.environment = flag.getEnvironment();
        this.rolloutPercentage = flag.getRolloutPercentage();
        this.flagType = flag.getFlagType().name();
        this.stringValue = flag.getStringValue();
        this.createdBy = flag.getCreatedBy();
        this.createdAt = flag.getCreatedAt();
        this.updatedAt = flag.getUpdatedAt();
    }

    public static FlagResponse from(Flag flag) {
        return new FlagResponse(flag);
    }
}