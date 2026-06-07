package com.featureflag.api.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class FlagChangeListener implements MessageListener {

    private final FlagCacheService cacheService;


    @Override
    public void onMessage(Message message, byte[] pattern){

        String payload = new String(message.getBody());

        String[] parts = payload.split(":", 2);

        if (parts.length != 2) {

            log.error("Received malformed cache invalidation payload: '{}' " +
                    "— expected format 'flagKey:environment'", payload);
            return;
        }

        String flagKey = parts[0];
        String environment = parts[1];

        cacheService.invalidate(flagKey, environment);

        log.info("Cache invalidated via Pub/Sub for flag: {} in {}",
                flagKey, environment);
    }

}
