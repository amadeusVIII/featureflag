package com.featureflag.api;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
		"JWT_SECRET=test-secret-key-that-is-long-enough-for-hmac-sha256-algorithm-ok"
})
@Import(ApiApplicationTests.RedisTestConfig.class)
class ApiApplicationTests {


	@MockitoBean
	private RedisMessageListenerContainer redisMessageListenerContainer;

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class RedisTestConfig {

		@Bean
		public RedisConnectionFactory redisConnectionFactory() {
			RedisConnectionFactory factory = Mockito.mock(RedisConnectionFactory.class);
			RedisConnection connection = Mockito.mock(RedisConnection.class);

			Mockito.when(factory.getConnection()).thenReturn(connection);
			Mockito.when(connection.isSubscribed()).thenReturn(false);

			return factory;
		}
	}
}
