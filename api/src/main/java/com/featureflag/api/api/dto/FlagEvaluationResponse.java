package com.featureflag.api.api.dto;

import com.featureflag.api.domain.flag.EvaluationReason;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;

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