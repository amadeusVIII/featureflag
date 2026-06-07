package com.featureflag.api.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.featureflag.api.cache.FlagCacheService;
import com.featureflag.api.domain.flag.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class FlagEvaluationServiceTest {

    @Mock
    private FlagRepository flagRepository;

    @Mock
    private FlagRuleRepository flagRuleRepository;

    @Mock
    private FlagCacheService flagCacheService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();


    private FlagEvaluationService evaluationService;

    private Flag testFlag;

    @BeforeEach
    void setUp() {
        // 1. Manually instantiate the service, passing the mocks and the real ObjectMapper
        evaluationService = new FlagEvaluationService(
                flagRepository,
                flagRuleRepository,
                objectMapper,
                flagCacheService
        );

        testFlag = new Flag();
        testFlag.setId(UUID.randomUUID());
        testFlag.setKey("dark-mode");
        testFlag.setEnvironment("production");
        testFlag.setEnabled(true);
        testFlag.setRolloutPercentage(100);
        testFlag.setFlagType(FlagType.BOOLEAN);

        // (Optional) lenient() might be needed here if some tests don't use the cache
        lenient().when(flagCacheService.get(any(), any())).thenReturn(Optional.empty());
    }


    // TEST GROUP 1 — Global enabled/disabled behavior

    @Test
    @DisplayName("DISABLED flag returns false with reason DISABLED regardless of rollout")
    void evaluate_flagDisabled_returnsDisabledReason() {

        testFlag.setEnabled(false);
        testFlag.setRolloutPercentage(100); // even 100% rollout shouldn't matter


        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));

        EvaluationResult result = evaluationService.evaluate(
                "dark-mode", "production", "user-123", Map.of());


        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.DISABLED);

        // VERIFY SIDE EFFECTS — the rule repo must NOT be called when flag is disabled.
        // There is no point evaluating rules for a globally off flag.
        verify(flagRuleRepository, never()).findByFlagIdOrderByRuleOrder(any());
    }

    @Test
    @DisplayName("Missing flag returns false with reason DEFAULT — never throws")
    void evaluate_missingFlag_returnsDefaultGracefully() {

        when(flagRepository.findByKeyAndEnvironment("nonexistent", "production"))
                .thenReturn(Optional.empty());


        EvaluationResult result = evaluationService.evaluate(
                "nonexistent", "production", "user-123", Map.of());


        assertThat(result.enabled()).isFalse();
        assertThat(result.reason()).isEqualTo(EvaluationReason.DEFAULT);

        // A missing flag must NOT write anything to the cache.
        // If we cached "not found", a newly created flag would be invisible
        // until the TTL expired.
        verify(flagCacheService, never()).put(any(), any(), anyBoolean(), any());
    }

    // TEST GROUP 2 — Rollout percentage logic

    @Test
    @DisplayName("100% rollout — every user gets the flag")
    void evaluate_100PercentRollout_everyUserEnabled() {

        testFlag.setRolloutPercentage(100);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));
        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());

        // ACT & ASSERT — try 20 different users, all should get the flag
        for (int i = 0; i < 20; i++) {
            EvaluationResult result = evaluationService.evaluate(
                    "dark-mode", "production", "user-" + i, Map.of());
            assertThat(result.enabled())
                    .as("user-%d should be in 100%% rollout", i)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("0% rollout — no user gets the flag")
    void evaluate_0PercentRollout_noUserEnabled() {
        // ARRANGE
        testFlag.setRolloutPercentage(0);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));
        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());

        // ACT & ASSERT — 20 users, none should get it
        for (int i = 0; i < 20; i++) {
            EvaluationResult result = evaluationService.evaluate(
                    "dark-mode", "production", "user-" + i, Map.of());
            assertThat(result.enabled())
                    .as("user-%d should NOT be in 0%% rollout", i)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("50% rollout — approximately half the users get the flag")
    void evaluate_50PercentRollout_approximatelyHalfEnabled() {

        testFlag.setRolloutPercentage(50);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));
        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());

        // ACT — evaluate 1000 different users
        int enabledCount = 0;
        int total = 1000;
        for (int i = 0; i < total; i++) {
            EvaluationResult result = evaluationService.evaluate(
                    "dark-mode", "production", "user-" + i, Map.of());
            if (result.enabled()) enabledCount++;
        }

        // ASSERT — should be between 40% and 60% (±10% tolerance)
        double percentage = (double) enabledCount / total * 100;
        assertThat(percentage)
                .as("Expected approximately 50%% enabled, got %.1f%%", percentage)
                .isBetween(40.0, 60.0);
    }

    @Test
    @DisplayName("Rollout is deterministic — same user always gets same result")
    void evaluate_sameUser_alwaysGetsSameResult() {
        testFlag.setRolloutPercentage(50);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));
        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());

        // ACT — call the same user 5 times
        boolean firstResult = evaluationService.evaluate(
                "dark-mode", "production", "user-stable", Map.of()).enabled();

        for (int i = 0; i < 4; i++) {
            boolean repeatResult = evaluationService.evaluate(
                    "dark-mode", "production", "user-stable", Map.of()).enabled();
            assertThat(repeatResult)
                    .as("Call %d should match first result %b", i + 2, firstResult)
                    .isEqualTo(firstResult);
        }
    }

    //TEST GROUP 3 — Targeting rules


    @Test
    @DisplayName("IN rule — matching userId gets flagged, non-matching does not")
    void evaluate_inRule_matchesCorrectUsers() {
        // ARRANGE — 0% rollout (rules must override this)
        testFlag.setRolloutPercentage(0);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));

        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());


        FlagRule rule = new FlagRule();
        rule.setId(UUID.randomUUID());
        rule.setFlag(testFlag);
        rule.setRuleOrder(0);
        rule.setAttribute("userId");
        rule.setOperator(RuleOperator.IN);
        rule.setValues("[\"user-alpha\", \"user-beta\"]");
        rule.setServeValue(true);

        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(List.of(rule));

        EvaluationResult matchingUser = evaluationService.evaluate(
                "dark-mode", "production", "user-alpha", Map.of());
        assertThat(matchingUser.enabled()).isTrue();
        assertThat(matchingUser.reason()).isEqualTo(EvaluationReason.RULE_MATCH);

        EvaluationResult nonMatchingUser = evaluationService.evaluate(
                "dark-mode", "production", "user-gamma", Map.of());

        assertThat(nonMatchingUser.enabled()).isFalse();
        assertThat(nonMatchingUser.reason()).isEqualTo(EvaluationReason.DEFAULT);
    }


    @Test
    @DisplayName("Rule serveValue=false — rule matches but serves false (kill switch per user)")
    void evaluate_ruleWithServeValueFalse_servesDisabledForMatchingUser() {

        testFlag.setRolloutPercentage(100);
        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));

        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(Collections.emptyList());


        FlagRule blockRule = new FlagRule();
        blockRule.setId(UUID.randomUUID());
        blockRule.setFlag(testFlag);
        blockRule.setRuleOrder(0);
        blockRule.setAttribute("userId");
        blockRule.setOperator(RuleOperator.IN);
        blockRule.setValues("[\"blocked-user\"]");
        blockRule.setServeValue(false); // ← explicitly serve FALSE when rule matches

        when(flagRuleRepository.findByFlagIdOrderByRuleOrder(testFlag.getId()))
                .thenReturn(List.of(blockRule));


        EvaluationResult blocked = evaluationService.evaluate(
                "dark-mode", "production", "blocked-user", Map.of());
        EvaluationResult allowed = evaluationService.evaluate(
                "dark-mode", "production", "normal-user", Map.of());


        assertThat(blocked.enabled()).isFalse();
        assertThat(blocked.reason()).isEqualTo(EvaluationReason.RULE_MATCH);


        assertThat(allowed.enabled()).isTrue();
        assertThat(allowed.reason()).isEqualTo(EvaluationReason.ROLLOUT);
    }


    //TEST GROUP 4 — Cache layer interaction

    @Test
    @DisplayName("Cache hit — returns cached result and NEVER queries the database")
    void evaluate_cacheHit_neverCallsRepository() {

        when(flagCacheService.get("dark-mode", "production"))
                .thenReturn(Optional.of(

                        new com.featureflag.api.cache.CachedEvaluationResult(
                                true,
                                EvaluationReason.ROLLOUT,
                                java.time.Instant.now()
                        )
                ));


        EvaluationResult result = evaluationService.evaluate(
                "dark-mode", "production", "user-123", Map.of());

        assertThat(result.enabled()).isTrue();
        assertThat(result.servedFromCache()).isTrue();

        verify(flagRepository, never()).findByKeyAndEnvironment(any(), any());
        verify(flagRuleRepository, never()).findByFlagIdOrderByRuleOrder(any());
    }

    @Test
    @DisplayName("Cache miss — queries database and then writes result to cache")
    void evaluate_cacheMiss_queriesDbAndPopulatesCache() {

        when(flagRepository.findByKeyAndEnvironment("dark-mode", "production"))
                .thenReturn(Optional.of(testFlag));

        EvaluationResult result = evaluationService.evaluate(
                "dark-mode", "production", "user-123", Map.of());

        assertThat(result.servedFromCache()).isFalse();
        assertThat(result.enabled()).isTrue();


        verify(flagCacheService, times(1)).put(
                eq("dark-mode"),
                eq("production"),
                anyBoolean(),
                any(EvaluationReason.class)
        );
    }
}
