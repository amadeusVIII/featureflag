package com.featureflag.api.domain.flag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.api.cache.CachedEvaluationResult;
import com.featureflag.api.cache.FlagCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagEvaluationService {

    private final FlagRepository flagRepository;
    private final FlagRuleRepository flagRuleRepository;
    private final ObjectMapper objectMapper;

    // Inject the cache service — this is the only new dependency
    private final FlagCacheService flagCacheService;


    public EvaluationResult evaluate(String flagKey, String environment,
                                     String userId, Map<String, String> attributes) {


        Optional<CachedEvaluationResult> cached = flagCacheService.get(flagKey, environment);

        if (cached.isPresent()) {
            CachedEvaluationResult hit = cached.get();
            log.debug("Serving flag '{}' from cache for user '{}'", flagKey, userId);
            return EvaluationResult.ofCached(hit.isEnabled(), hit.getReason());
        }


        EvaluationResult result = evaluateFromDatabase(flagKey, environment, userId, attributes);


        if (result.reason() != EvaluationReason.DEFAULT) {
            flagCacheService.put(flagKey, environment, result.enabled(), result.reason());
            log.debug("Cached evaluation result for flag '{}' in '{}'", flagKey, environment);
        } else {
            
            log.debug("Flag '{}' not found in '{}' — skipping cache write", flagKey, environment);
        }

        return result;
    }


    private EvaluationResult evaluateFromDatabase(String flagKey, String environment,
                                                  String userId, Map<String, String> attributes) {
        Flag flag = flagRepository.findByKeyAndEnvironment(flagKey, environment)
                .orElse(null);

        if (flag == null) {
            log.debug("Flag not found: {} in {} — returning disabled", flagKey, environment);
            return EvaluationResult.of(false, EvaluationReason.DEFAULT);
        }

        if (!flag.isEnabled()) {
            log.debug("Flag {} is globally disabled", flagKey);
            return EvaluationResult.of(false, EvaluationReason.DISABLED);
        }

        List<FlagRule> rules = flagRuleRepository.findByFlagIdOrderByRuleOrder(flag.getId());

        for (FlagRule rule : rules) {
            if (ruleMatches(rule, userId, attributes)) {
                log.debug("Flag {} matched rule {} for user {}", flagKey, rule.getId(), userId);
                return EvaluationResult.of(rule.isServeValue(), EvaluationReason.RULE_MATCH);
            }
        }

        if (isInRollout(flagKey, userId, flag.getRolloutPercentage())) {
            log.debug("Flag {} — user {} is within rollout {}%",
                    flagKey, userId, flag.getRolloutPercentage());
            return EvaluationResult.of(true, EvaluationReason.ROLLOUT);
        }

        log.debug("Flag {} — user {} outside rollout, returning default false", flagKey, userId);
        return EvaluationResult.of(false, EvaluationReason.DEFAULT);
    }

    // Helper to check if a flag exists without loading full evaluation
    private boolean flagExists(String flagKey, String environment) {
        return flagRepository.findByKeyAndEnvironment(flagKey, environment).isPresent();
    }



    private boolean isInRollout(String flagKey, String userId, int rolloutPercentage) {
        if (rolloutPercentage >= 100){ return true;}
        if (rolloutPercentage <= 0){ return false;}
        String hashInput = flagKey + ":" + userId;
        int bucket = Math.abs(hashInput.hashCode()) % 100;
        return bucket < rolloutPercentage;
    }

    private boolean ruleMatches(FlagRule rule, String userId, Map<String, String> attributes) {
        String attributeValue;
        if ("userId".equals(rule.getAttribute())) {
            attributeValue = userId;
        } else {
            attributeValue = (attributes != null) ? attributes.get(rule.getAttribute()) : null;
        }

        if (attributeValue == null) {
            log.debug("Rule attribute '{}' not present in context — skipping rule",
                    rule.getAttribute());
            return false;
        }

        List<String> allowedValues = parseValues(rule.getValues());

        return switch (rule.getOperator()) {
            case IN -> allowedValues.contains(attributeValue);
            case NOT_IN -> !allowedValues.contains(attributeValue);
            case EQUALS -> allowedValues.size() == 1 && allowedValues.get(0).equals(attributeValue);
            case CONTAINS -> allowedValues.stream().anyMatch(attributeValue::contains);
        };
    }

    private List<String> parseValues(String valuesJson) {
        try {
            return objectMapper.readValue(valuesJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse rule values JSON: '{}' — error: {}", valuesJson, e.getMessage());
            return List.of();
        }
    }
}
