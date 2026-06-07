
package com.featureflag.api.domain.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;


    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;


    @Column(name = "entity_id", nullable = false)
    private UUID entityId;


    @Column(nullable = false, length = 50)
    private String action;


    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private String oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private String newValue;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}