package com.featureflag.api.cache;

import com.featureflag.api.domain.flag.EvaluationReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedEvaluationResult {


    private boolean enabled;


    private EvaluationReason reason;


    private Instant cachedAt;
}
