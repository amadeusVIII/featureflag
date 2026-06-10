package com.featureflag.api.api;

import com.featureflag.api.api.dto.CreateFlagRequest;
import com.featureflag.api.api.dto.FlagResponse;
import com.featureflag.api.api.dto.UpdateFlagRequest;
import com.featureflag.api.domain.audit.AuditLog;
import com.featureflag.api.domain.audit.AuditLogRepository;
import com.featureflag.api.domain.flag.Flag;
import com.featureflag.api.domain.flag.FlagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;


@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final FlagService flagService;

    // Injected directly here because global audit reads are not flag-scoped.
    // FlagService owns audit *writes*; the controller owns this read-only view.
    private final AuditLogRepository auditLogRepository;


    @GetMapping("/flags")
    public ResponseEntity<Page<FlagResponse>> listFlags(
            @RequestParam(defaultValue = "production") String environment,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {


        Page<Flag> flags = flagService.findByEnvironment(
                environment,
                PageRequest.of(page, size, Sort.by("createdAt").descending())
        );


        return ResponseEntity.ok(flags.map(FlagResponse::from));
    }


    @GetMapping("/flags/{id}")
    public ResponseEntity<FlagResponse> getFlag(@PathVariable UUID id) {
        Flag flag = flagService.findById(id);
        return ResponseEntity.ok(FlagResponse.from(flag));
    }


    @PostMapping("/flags")
    @PreAuthorize("hasRole('ADMIN')")  // VIEWER cannot create flags
    public ResponseEntity<FlagResponse> createFlag(
            @Valid @RequestBody CreateFlagRequest request) {
        Flag flag = flagService.create(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FlagResponse.from(flag));
    }


    @PutMapping("/flags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FlagResponse> updateFlag(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFlagRequest request) {
        Flag flag = flagService.update(id, request);
        return ResponseEntity.ok(FlagResponse.from(flag));
    }


    @PatchMapping("/flags/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FlagResponse> toggleFlag(@PathVariable UUID id) {
        Flag flag = flagService.toggle(id);
        return ResponseEntity.ok(FlagResponse.from(flag));
    }


    @DeleteMapping("/flags/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteFlag(@PathVariable UUID id) {
        flagService.delete(id);
        // 204 No Content = success, nothing to return
        return ResponseEntity.noContent().build();
    }


    @GetMapping("/flags/{id}/audit")
    public ResponseEntity<List<AuditLog>> getAuditLog(@PathVariable UUID id) {
        return ResponseEntity.ok(flagService.getAuditLog(id));
    }



    @GetMapping("/audit")
    public ResponseEntity<List<AuditLog>> getGlobalAuditLog() {

        List<AuditLog> entries = auditLogRepository.findAllByOrderByCreatedAtDesc();
        log.debug("Global audit log requested — {} entries returned", entries.size());
        return ResponseEntity.ok(entries);
    }


}
