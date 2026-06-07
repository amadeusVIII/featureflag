package com.featureflag.api.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import com.featureflag.api.domain.flag.EvaluationReason;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlagCacheService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final Duration FLAG_TTL = Duration.ofMinutes(5);

    private String cacheKey(String flagKey, String environment) {
        return "flag:" + flagKey + ":" + environment;
    }


    public Optional<CachedEvaluationResult> get(String flagKey, String environment) {

        String key = cacheKey(flagKey, environment);

        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {

                log.debug("Cache MISS for key: {}", key);
                return Optional.empty();
            }

            CachedEvaluationResult result = objectMapper.readValue(json, CachedEvaluationResult.class);
            log.debug("Cache HIT for key: {}", key);
            return Optional.of(result);
        } catch (RedisConnectionFailureException e) {

            log.warn("Redis unavailable during cache GET for key: {} — falling back to DB. Error: {}",
                    key, e.getMessage());
            return Optional.empty();

        } catch (JsonProcessingException e) {

            log.error("Failed to deserialize cached value for key: {} — treating as cache miss. Error: {}",
                    key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String flagKey, String environment, boolean enabled,
                    EvaluationReason reason){

        String key = cacheKey(flagKey, environment);


        try {
            CachedEvaluationResult toCache = CachedEvaluationResult.builder()
                    .enabled(enabled)
                    .reason(reason)
                    .cachedAt(Instant.now())
                    .build();

            String json = objectMapper.writeValueAsString(toCache);


            redisTemplate.opsForValue().set(key, json, FLAG_TTL);

            log.debug("Cached flag result for key: {} (TTL: {})", key, FLAG_TTL);

        } catch (RedisConnectionFailureException e) {

            log.warn("Redis unavailable during cache PUT for key: {} — skipping cache write. Error: {}",
                    key, e.getMessage());

        } catch (JsonProcessingException e) {

            log.error("Failed to serialize cache value for key: {}. Error: {}", key, e.getMessage());
        }
    }

    public void invalidate(String flagKey, String environment) {
        String key = cacheKey(flagKey, environment);

        try {
            redisTemplate.delete(key);
            log.info("Cache invalidated for key: {}", key);

        } catch (RedisConnectionFailureException e) {
            log.warn("Redis unavailable during cache invalidation for key: {} — " +
                    "stale data will expire via TTL. Error: {}", key, e.getMessage());
        }
    }

}