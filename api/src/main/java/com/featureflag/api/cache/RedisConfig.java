package com.featureflag.api.cache;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;


@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();


        template.setConnectionFactory(connectionFactory);


        template.setKeySerializer(new StringRedisSerializer());


        template.setValueSerializer(new StringRedisSerializer());


        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());

        return template;
    }


    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            FlagChangeListener flagChangeListener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();


        container.setConnectionFactory(connectionFactory);


        container.addMessageListener(flagChangeListener, new ChannelTopic("flag-updates"));

        return container;
    }

}

