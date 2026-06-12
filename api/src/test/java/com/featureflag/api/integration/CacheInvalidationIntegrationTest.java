package com.featureflag.api.integration;

import com.featureflag.api.api.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class CacheInvalidationIntegrationTest extends AbstractIntegrationTest{

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminToken;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // Flush Redis and DB before each test
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
    @DisplayName("Toggling a flag deletes its Redis cache key")
    void toggle_deletesRedisCacheKey() throws InterruptedException {

        String flagId = createTestFlag("toggle-test", 100);
        evaluate("toggle-test", "user-001"); // warm cache


        assertThat(redisTemplate.hasKey("flag:toggle-test:production")).isTrue();


        toggleFlag(flagId);


        Thread.sleep(200);


        assertThat(redisTemplate.hasKey("flag:toggle-test:production"))
                .as("Cache key should be deleted after toggle")
                .isFalse();
    }

    @Test
    @DisplayName("After toggle, next evaluation is a fresh cache MISS from the database")
    void afterToggle_nextEvaluationIsDbRead() throws InterruptedException {

        String flagId = createTestFlag("freshread-test", 100);
        FlagEvaluationResponse before = evaluate("freshread-test", "user-001");
        assertThat(before.isEnabled()).isTrue();


        toggleFlag(flagId);
        Thread.sleep(200);


        FlagEvaluationResponse after = evaluate("freshread-test", "user-001");


        assertThat(after.isServedFromCache()).isFalse();
        assertThat(after.isEnabled()).isFalse();
    }


    @Test
    @DisplayName("Updating rolloutPct to 0% invalidates cache and serves new value")
    void update_invalidatesCacheAndServesNewRollout() throws InterruptedException {

        String flagId = createTestFlag("update-test", 100);
        FlagEvaluationResponse cached = evaluate("update-test", "user-001");
        assertThat(cached.isEnabled()).isTrue();

        assertThat(redisTemplate.hasKey("flag:update-test:production")).isTrue();


        updateFlag(flagId, 0, true);
        Thread.sleep(200);


        assertThat(redisTemplate.hasKey("flag:update-test:production")).isFalse();


        FlagEvaluationResponse after = evaluate("update-test", "user-001");
        assertThat(after.isEnabled()).isFalse();
        assertThat(after.isServedFromCache()).isFalse();
    }

    @Test
    @DisplayName("Deleting a flag invalidates its cache key")
    void delete_invalidatesCacheKey() throws InterruptedException {

        String flagId = createTestFlag("delete-test", 100);
        evaluate("delete-test", "user-001");
        assertThat(redisTemplate.hasKey("flag:delete-test:production")).isTrue();


        deleteFlag(flagId);
        Thread.sleep(200);


        assertThat(redisTemplate.hasKey("flag:delete-test:production")).isFalse();
    }


    //helpers

    private String registerAndLoginAdmin() {
        RegisterRequest register = new RegisterRequest();
        register.setEmail("admin-invalidation@test.com");
        register.setPassword("securePassword123!");
        restTemplate.postForEntity(baseUrl + "/api/v1/auth/register",
                register, AuthResponse.class);

        jdbcTemplate.execute(
                "UPDATE users SET role = 'ADMIN' WHERE email = 'admin-invalidation@test.com'");

        LoginRequest login = new LoginRequest();
        login.setEmail("admin-invalidation@test.com");
        login.setPassword("securePassword123!");
        ResponseEntity<AuthResponse> resp = restTemplate.postForEntity(
                baseUrl + "/api/v1/auth/login", login, AuthResponse.class);

        return resp.getBody().getAccessToken();
    }

    private String createTestFlag(String key, int rolloutPct) {
        CreateFlagRequest req = new CreateFlagRequest();
        req.setKey(key);
        req.setName("Test: " + key);
        req.setEnvironment("production");
        req.setEnabled(true);
        req.setRolloutPercentage(rolloutPct);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<FlagResponse> resp = restTemplate.exchange(
                baseUrl + "/api/v1/admin/flags",
                HttpMethod.POST,
                new HttpEntity<>(req, headers),
                FlagResponse.class
        );


        return resp.getBody().getId().toString();
    }


    private void toggleFlag(String flagId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        restTemplate.exchange(
                baseUrl + "/api/v1/admin/flags/" + flagId + "/toggle",
                HttpMethod.PATCH,
                new HttpEntity<>(headers),
                FlagResponse.class
        );
    }

    private void updateFlag(String flagId, int rolloutPct, boolean enabled) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "enabled", enabled,
                "rolloutPercentage", rolloutPct
        );

        restTemplate.exchange(
                baseUrl + "/api/v1/admin/flags/" + flagId,
                HttpMethod.PUT,
                new HttpEntity<>(body, headers),
                FlagResponse.class
        );
    }

    private void deleteFlag(String flagId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);

        restTemplate.exchange(
                baseUrl + "/api/v1/admin/flags/" + flagId,
                HttpMethod.DELETE,
                new HttpEntity<>(headers),
                Void.class
        );
    }

    private FlagEvaluationResponse evaluate(String key, String userId) {
        HttpHeaders headers = new HttpHeaders();
        // The /evaluate endpoint requires authentication.
        // We reuse the admin JWT — any valid user token works here.
        headers.setBearerAuth(adminToken);

        ResponseEntity<FlagEvaluationResponse> response = restTemplate.exchange(
                baseUrl + "/api/v1/flags/evaluate?key={key}&userId={userId}&environment=production",
                HttpMethod.GET,
                new HttpEntity<>(headers),    // pass headers with the request
                FlagEvaluationResponse.class,
                key, userId
        );

        // Use exchange() instead of getForObject() so we can see the status code
        // if something goes wrong — getForObject() silently returns null on errors.
        return response.getBody();
    }

}
