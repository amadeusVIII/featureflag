package com.featureflag.sdk;

import java.time.Instant;


public record FlagEvaluationResult(


        String flagKey,


        boolean enabled,


        String reason,

        // Was this result served from the local L1 cache?
        // If true: this cost 0 network calls. If false: an HTTP call was made.
        boolean servedFromCache,

        Instant evaluatedAt
) {

    //Factory methods


    public static FlagEvaluationResult fromServer(
            String flagKey, boolean enabled, String reason) {
        return new FlagEvaluationResult(flagKey, enabled, reason, false, Instant.now());
    }


    public static FlagEvaluationResult fromCache(
            String flagKey, boolean enabled, String reason, Instant cachedAt) {
        return new FlagEvaluationResult(flagKey, enabled, reason, true, cachedAt);
    }


    public static FlagEvaluationResult defaultOff(String flagKey) {
        return new FlagEvaluationResult(
                flagKey, false, "SDK_DEFAULT", false, Instant.now());
    }
}