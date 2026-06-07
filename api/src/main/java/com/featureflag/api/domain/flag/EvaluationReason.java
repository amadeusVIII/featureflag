package com.featureflag.api.domain.flag;

public enum EvaluationReason {
    DISABLED,
    RULE_MATCH,
    ROLLOUT,
    DEFAULT
}
