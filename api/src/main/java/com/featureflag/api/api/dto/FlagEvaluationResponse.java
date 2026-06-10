package com.featureflag.api.api.dto;

import com.featureflag.api.domain.flag.EvaluationReason;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class FlagEvaluationResponse {

    private String flagKey;


    private boolean enabled;

    private EvaluationReason reason;

    private Instant evaluatedAt;

    private boolean servedFromCache;

    private long evaluationTimeMs;
}
