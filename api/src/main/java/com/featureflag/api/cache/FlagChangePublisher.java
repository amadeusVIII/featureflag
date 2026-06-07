package com.featureflag.api.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagChangePublisher {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String CHANNEL = "flag-updates";


    public void publishInvalidation(String flagKey, String environment){

        String payload = flagKey + ":" + environment;

        try {
            redisTemplate.convertAndSend(CHANNEL, payload);
            log.info("Published cache invalidation for flag: {} in {}",
                    flagKey, environment);

        } catch (RedisConnectionFailureException e) {
            log.warn("Failed to publish cache invalidation for flag: {} — " +
                            "Redis unavailable. Stale data will expire via TTL. Error: {}",
                    flagKey, e.getMessage());
        }
    }

}
