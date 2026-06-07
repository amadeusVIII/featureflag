package com.featureflag.api.domain.flag;

public record EvaluationResult(boolean enabled, EvaluationReason reason, boolean servedFromCache) {


    public static EvaluationResult of(boolean enabled, EvaluationReason reason) {
        return new EvaluationResult(enabled, reason, false);
    }

    public static EvaluationResult ofCached(boolean enabled, EvaluationReason reason) {
        return new EvaluationResult(enabled, reason, true);
    }
}