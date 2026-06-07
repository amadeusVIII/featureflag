package com.featureflag.api.domain.flag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.api.api.dto.CreateFlagRequest;
import com.featureflag.api.api.dto.FlagResponse;
import com.featureflag.api.api.dto.UpdateFlagRequest;
import com.featureflag.api.cache.FlagChangePublisher;
import com.featureflag.api.domain.audit.AuditLog;
import com.featureflag.api.domain.audit.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.featureflag.api.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Slf4j
public class FlagService {

    private final FlagRepository flagRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final FlagChangePublisher flagChangePublisher;

    @Transactional
    public Flag create(CreateFlagRequest request) {

        if (flagRepository.existsByKeyAndEnvironment(request.getKey(), request.getEnvironment())) {
            throw new IllegalArgumentException(
                    "Flag key '" + request.getKey() +
                            "' already exists in environment '" + request.getEnvironment() + "'");
        }

        Instant now = Instant.now();
        UUID currentUserId = getCurrentUserId();

        Flag flag = Flag.builder()
                .key(request.getKey())
                .name(request.getName())
                .description(request.getDescription())
                .enabled(request.isEnabled())
                .environment(request.getEnvironment())
                .rolloutPercentage(request.getRolloutPercentage())
                .flagType(FlagType.valueOf(request.getFlagType()))
                .stringValue(request.getStringValue())
                .createdBy(currentUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();

        Flag saved = flagRepository.save(flag);

        writeAuditLog("FLAG", saved.getId(), "CREATED", currentUserId, null, saved);

        log.info("Flag created: {} in {} by {}", saved.getKey(), saved.getEnvironment(), currentUserId);

        return saved;
    }


    @Transactional(readOnly = true)
    public Page<Flag> findByEnvironment(String environment, Pageable pageable) {
        return flagRepository.findByEnvironment(environment, pageable);
    }


    @Transactional(readOnly = true)
    public Flag findById(UUID id) {
        return flagRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Flag not found: " + id));
    }


    @Transactional
    public Flag update(UUID id, UpdateFlagRequest request) {

        UUID currentUserId = getCurrentUserId(); // FIX: resolve once, reuse below
        Flag flag = findById(id);
        String oldValue = toJson(flag);

        if (request.getName() != null) {
            flag.setName(request.getName());
        }
        if (request.getDescription() != null) {
            flag.setDescription(request.getDescription());
        }
        if (request.getEnabled() != null) {
            flag.setEnabled(request.getEnabled());
        }
        if (request.getRolloutPercentage() != null) {
            flag.setRolloutPercentage(request.getRolloutPercentage());
        }
        if (request.getStringValue() != null) {
            flag.setStringValue(request.getStringValue());
        }

        flag.setUpdatedAt(Instant.now());
        Flag saved = flagRepository.save(flag);

        writeAuditLog("FLAG", saved.getId(), "UPDATED", currentUserId, oldValue, saved);


        flagChangePublisher.publishInvalidation(saved.getKey(), saved.getEnvironment());

        log.info("Flag updated: {} by {}", saved.getKey(), currentUserId);

        return saved;

    }


    @Transactional
    public Flag toggle(UUID id) {
        UUID currentUserId = getCurrentUserId();
        Flag flag = findById(id);
        String oldValue = toJson(flag);

        flag.setEnabled(!flag.isEnabled());
        flag.setUpdatedAt(Instant.now());

        Flag saved = flagRepository.save(flag);

        writeAuditLog("FLAG", saved.getId(), "TOGGLED", currentUserId, oldValue, saved);

        flagChangePublisher.publishInvalidation(saved.getKey(), saved.getEnvironment());

        log.info("Flag toggled: {} → enabled={} by {}", saved.getKey(), saved.isEnabled(), currentUserId); // FIX: reuse
        return saved;
    }


    @Transactional
    public void delete(UUID id) {
        UUID currentUserId = getCurrentUserId(); // FIX: resolve once, reuse below
        Flag flag = findById(id);
        String oldValue = toJson(flag);

        String flagKey = flag.getKey();
        String environment = flag.getEnvironment();

        writeAuditLog("FLAG", flag.getId(), "DELETED", currentUserId, oldValue, null);

        flagRepository.delete(flag);

        flagChangePublisher.publishInvalidation(flagKey, environment);

        log.info("Flag deleted: {} by {}", flag.getKey(), currentUserId);
    }


    @Transactional(readOnly = true)
    public List<AuditLog> getAuditLog(UUID flagId) {
        return auditLogRepository.findByEntityIdOrderByCreatedAtDesc(flagId);
    }


    // helpers

    private UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("No authenticated user found");
        }

        String email = auth.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated user not found in database: " + email))
                .getId();
    }


    private void writeAuditLog(String entityType, UUID entityId,
                               String action, UUID changedBy,
                               String oldValue, Flag newState) {
        AuditLog entry = AuditLog.builder()
                .entityType(entityType)
                .entityId(entityId)
                .action(action)
                .changedBy(changedBy)
                .oldValue(oldValue)
                .newValue(newState != null ? toJson(newState) : null)
                .createdAt(Instant.now())
                .build();

        auditLogRepository.save(entry);
    }

    private String toJson(Flag flag) {
        try {
            return objectMapper.writeValueAsString(flag);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize flag for audit log: {}", e.getMessage());
            return "{}";
        }
    }
}