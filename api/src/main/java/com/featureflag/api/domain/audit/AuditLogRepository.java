package com.featureflag.api.domain.audit;


import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;



@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByEntityIdOrderByCreatedAtDesc(UUID entityId);

    List<AuditLog> findByChangedByOrderByCreatedAtDesc(UUID changedBy);
}
