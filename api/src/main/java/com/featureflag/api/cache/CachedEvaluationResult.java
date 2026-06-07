package com.featureflag.api.cache;

import com.featureflag.api.domain.flag.EvaluationReason;
import java.time.Instant;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CachedEvaluationResult {


    private boolean enabled;


    private EvaluationReason reason;


    private Instant cachedAt;
}
