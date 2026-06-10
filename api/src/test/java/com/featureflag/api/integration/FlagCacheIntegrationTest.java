package com.featureflag.api.integration;

import com.featureflag.api.api.dto.*;
import com.featureflag.api.cache.FlagCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class FlagCacheIntegrationTest extends AbstractIntegrationTest{

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private FlagCacheService flagCacheService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private String adminToken;
    private String baseUrl;


    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;


        redisTemplate.getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();


        jdbcTemplate.execute("DELETE FROM flag_rules");
        jdbcTemplate.execute("DELETE FROM audit_log");
        jdbcTemplate.execute("DELETE FROM flags");
        jdbcTemplate.execute("DELETE FROM users");


        adminToken = registerAndLoginAdmin();
    }


    @Test
    @DisplayName("First evaluation is a cache MISS and hits the database")
    void firstEvaluation_isCacheMiss() {

        createTestFlag("cache-test", 100);


        FlagEvaluationResponse response = evaluate("cache-test", "user-001");


        assertThat(response).isNotNull();
        assertThat(response.isServedFromCache()).isFalse();
        assertThat(response.getEvaluationTimeMs()).isGreaterThan(2L); // DB latency
    }


    @Test
    @DisplayName("Second identical evaluation is a cache HIT and skips the database")
    void secondEvaluation_isCacheHit() {

        createTestFlag("cache-test", 100);


        evaluate("cache-test", "user-001");
        FlagEvaluationResponse secondResponse = evaluate("cache-test", "user-001");


        assertThat(secondResponse.isServedFromCache()).isTrue();
        assertThat(secondResponse.getEvaluationTimeMs()).isLessThan(10L); // Redis speed
    }

    @Test
    @DisplayName("Cache key appears in Redis after first evaluation")
    void afterFirstEvaluation_redisKeyExists() {

        createTestFlag("dark-mode", 100);


        assertThat(redisTemplate.hasKey("flag:dark-mode:production")).isFalse();

        evaluate("dark-mode", "user-001");

        assertThat(redisTemplate.hasKey("flag:dark-mode:production")).isTrue();
    }

    @Test
    @DisplayName("Redis key has a TTL between 1 and 300 seconds after caching")
    void afterCaching_keyHasTtlInRange() {
        // ARRANGE
        createTestFlag("ttl-test", 100);
        evaluate("ttl-test", "user-001");


        Long ttlSeconds = redisTemplate.getExpire("flag:ttl-test:production");


        assertThat(ttlSeconds)
                .as("TTL should be set and between 1 and 300 seconds")
                .isGreaterThan(0L)
                .isLessThanOrEqualTo(300L);
    }


    @Test
    @DisplayName("Missing flag returns 200 with enabled=false, not 404 or 500")
    void missingFlag_returnsDefaultGracefully() {

        FlagEvaluationResponse response = evaluate("nonexistent-flag", "user-001");


        assertThat(response).isNotNull();
        assertThat(response.isEnabled()).isFalse();

    }

    //helpers

    private String registerAndLoginAdmin() {
        // Register
        RegisterRequest register = new RegisterRequest();
        register.setEmail("admin@test.com");
        register.setPassword("securePassword123!");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register",
                register, AuthResponse.class);

        jdbcTemplate.execute(
                "UPDATE users SET role = 'ADMIN' WHERE email = 'admin@test.com'");


        LoginRequest login = new LoginRequest();
        login.setEmail("admin@test.com");
        login.setPassword("securePassword123!");
        ResponseEntity<AuthResponse> loginResp = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login", login, AuthResponse.class);

        return loginResp.getBody().getAccessToken();
    }


    private void createTestFlag(String key, int rolloutPct) {
        CreateFlagRequest req = new CreateFlagRequest();
        req.setKey(key);
        req.setName("Test Flag: " + key);
        req.setEnvironment("production");
        req.setEnabled(true);
        req.setRolloutPercentage(rolloutPct);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        restTemplate.exchange(
                baseUrl + "/api/v1/admin/flags",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                String.class
        );
    }

    private FlagEvaluationResponse evaluate(String key, String userId) {
        ResponseEntity<FlagEvaluationResponse> response = restTemplate.getForEntity(
                baseUrl + "/api/v1/flags/evaluate?key={key}&userId={userId}&environment=production",
                FlagEvaluationResponse.class,
                key, userId
        );
        return response.getBody();
    }


}
