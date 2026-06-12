package com.featureflag.sdk;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureFlagClientTest {

    private MockWebServer mockServer;
    private FeatureFlagClient client;

    @BeforeEach
    void setUp() throws IOException {
        mockServer = new MockWebServer();
        mockServer.start(); // picks a random available port

        // Build the client pointing at our mock server
        client = FeatureFlagClient.builder()
                .serverUrl(mockServer.url("/").toString()) // "http://localhost:PORT/"
                .apiKey("test-api-key")
                .localCacheTtl(Duration.ofSeconds(5)) // short TTL for tests
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        mockServer.shutdown();
    }

    @Test
    @DisplayName("isEnabled() returns true when server responds with enabled=true")
    void isEnabled_serverReturnsTrue_returnsTrue() {
        // ARRANGE — tell MockWebServer what to reply
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":true,\"reason\":\"ROLLOUT\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":2}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        // ACT
        boolean result = client.isEnabled("dark-mode", "user-123");

        // ASSERT
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isEnabled() returns false when server responds with enabled=false")
    void isEnabled_serverReturnsFalse_returnsFalse() {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":false,\"reason\":\"DISABLED\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":1}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(200));

        boolean result = client.isEnabled("dark-mode", "user-123");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Second call uses L1 cache — server is called only once")
    void secondCall_usesL1Cache_serverCalledOnlyOnce() throws InterruptedException {
        // ARRANGE — enqueue ONE response for the first call
        // If the SDK makes a second HTTP call, MockWebServer will return a 500
        // because there's no second response enqueued
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":true,\"reason\":\"ROLLOUT\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":2}")
                .addHeader("Content-Type", "application/json"));

        // ACT — call twice for the same flag + user combination
        FlagEvaluationResult first  = client.evaluate("dark-mode", "user-123");
        FlagEvaluationResult second = client.evaluate("dark-mode", "user-123");

        // ASSERT — both return the same result
        assertThat(first.enabled()).isTrue();
        assertThat(second.enabled()).isTrue();

        // CRITICAL — the first call was a network call, the second was from L1 cache
        assertThat(first.servedFromCache()).isFalse();
        assertThat(second.servedFromCache()).isTrue();

        // CRITICAL — only ONE request was made to the server
        assertThat(mockServer.getRequestCount())
                .as("Server should be called exactly once (second call served from L1 cache)")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("Correct request headers and URL are sent to the server")
    void evaluate_sendsCorrectRequestToServer() throws InterruptedException {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":true,\"reason\":\"ROLLOUT\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":2}")
                .addHeader("Content-Type", "application/json"));

        client.isEnabled("dark-mode", "user-abc");

        // Inspect the actual HTTP request the SDK sent
        RecordedRequest request = mockServer.takeRequest();

        // Verify the API key header was sent
        assertThat(request.getHeader("X-API-Key"))
                .as("X-API-Key header must be present")
                .isEqualTo("test-api-key");

        // Verify the correct endpoint and parameters
        assertThat(request.getPath())
                .as("Request path must include flag key and userId")
                .contains("/api/v1/flags/evaluate")
                .contains("key=dark-mode")
                .contains("userId=user-abc")
                .contains("environment=production");
    }

    @Test
    @DisplayName("Server returns 500 — SDK returns false (safe default), never throws")
    void serverError_returnsDefaultOff_neverThrows() {
        // ARRANGE — simulate a server error
        mockServer.enqueue(new MockResponse().setResponseCode(500));

        // ACT — this must not throw
        boolean result = client.isEnabled("dark-mode", "user-123");

        // ASSERT — safe default
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Server is unreachable — SDK returns false (safe default), never throws")
    void serverUnreachable_returnsDefaultOff_neverThrows() throws IOException {
        // ARRANGE — shut down the mock server to simulate an unreachable host
        mockServer.shutdown();

        // ACT — this must not throw even though the server is gone
        boolean result = client.isEnabled("dark-mode", "user-123");

        // ASSERT
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Different users get independently cached results")
    void differentUsers_cachedIndependently() {
        // ARRANGE — two responses, one per user
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":true,\"reason\":\"ROLLOUT\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":2}")
                .addHeader("Content-Type", "application/json"));
        mockServer.enqueue(new MockResponse()
                .setBody("{\"flagKey\":\"dark-mode\",\"enabled\":false,\"reason\":\"DEFAULT\"," +
                        "\"servedFromCache\":false,\"evaluationTimeMs\":1}")
                .addHeader("Content-Type", "application/json"));

        // ACT
        boolean user1Result = client.isEnabled("dark-mode", "user-in-rollout");
        boolean user2Result = client.isEnabled("dark-mode", "user-not-in-rollout");

        // ASSERT — different users can have different results
        assertThat(user1Result).isTrue();
        assertThat(user2Result).isFalse();

        // Both were cache misses (different cache keys: different userId)
        assertThat(mockServer.getRequestCount()).isEqualTo(2);
    }
}
