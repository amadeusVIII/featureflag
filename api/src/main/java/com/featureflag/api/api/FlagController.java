package com.featureflag.api.api;

import com.featureflag.api.api.dto.FlagEvaluationResponse;
import com.featureflag.api.domain.flag.EvaluationResult;
import com.featureflag.api.domain.flag.FlagEvaluationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/flags")
@RequiredArgsConstructor
@Slf4j
public class FlagController {

    private final FlagEvaluationService evaluationService;

    @GetMapping("/evaluate")
    public ResponseEntity<FlagEvaluationResponse> evaluate(
            @RequestParam String key,
            @RequestParam String userId,
            @RequestParam(defaultValue = "production") String environment) {

        long startTime = System.currentTimeMillis();

        EvaluationResult result = evaluationService.evaluate(key, environment, userId, Map.of());

        long elapsed = System.currentTimeMillis() - startTime;

        FlagEvaluationResponse response = FlagEvaluationResponse.builder()
                .flagKey(key)
                .enabled(result.enabled())
                .reason(result.reason())
                .evaluatedAt(Instant.now())
                .servedFromCache(result.servedFromCache()) // now comes from EvaluationResult
                .evaluationTimeMs(elapsed)
                .build();


        String cacheStatus = result.servedFromCache() ? "HIT" : "MISS";

        log.debug("Evaluated flag '{}' for user '{}': {} ({}) {}ms",
                key, userId, result.enabled(), result.reason(), elapsed);

        return ResponseEntity.ok()
                .header("X-Cache-Status", cacheStatus)
                .body(response);
    }
}
