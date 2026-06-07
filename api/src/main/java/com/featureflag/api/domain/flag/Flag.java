package com.featureflag.api.domain.flag;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;


@Entity
@Table(
        name = "flags",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_flags_key_environment",
                        columnNames = {"key", "environment"}
                )
        }
)

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Flag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String key;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private  boolean enabled;

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String environment = "production";

    @Column(name = "rollout_pct", nullable = false)
    @Builder.Default
    private int rolloutPercentage = 100;


    @Enumerated(EnumType.STRING)
    @Column(name = "flag_type", nullable = false, length = 20)
    @Builder.Default
    private FlagType flagType = FlagType.BOOLEAN;


    @Column(name = "string_value", length = 500)
    private String stringValue;


    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
