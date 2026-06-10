package com.featureflag.sdk.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.featureflag.sdk.FlagEvaluationResult;
import com.featureflag.sdk.SdkConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FlagApiClient {

    private static final Logger log = Logger.getLogger(FlagApiClient.class.getName());
    private final SdkConfig config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public FlagApiClient(SdkConfig config) {
        this.config = config;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(config.getConnectTimeout())
                .build();

        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public FlagEvaluationResult evaluate(String flagKey, String userId, String environment) {
        String url = buildEvaluateUrl(flagKey, userId, environment);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    // API key authentication — SDK clients use X-API-Key, not JWT
                    .header("X-API-Key", config.getApiKey())
                    .header("Accept", "application/json")
                    // Per-request timeout — prevents one slow server from blocking forever
                    .timeout(config.getReadTimeout())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseEvaluationResponse(flagKey, response.body());
            } else {
                // Non-200 response — log and return safe default
                log.warning(String.format(
                        "FeatureFlag server returned %d for flag '%s'. Defaulting to OFF.",
                        response.statusCode(), flagKey));
                return FlagEvaluationResult.defaultOff(flagKey);
            }
        } catch (java.net.http.HttpTimeoutException e) {
            // Server is slow or unreachable — degrade gracefully
            log.log(Level.WARNING,
                    "Timeout evaluating flag '" + flagKey + "'. Defaulting to OFF.", e);
            return FlagEvaluationResult.defaultOff(flagKey);

        } catch (java.io.IOException | InterruptedException e) {
            // Network error — degrade gracefully
            log.log(Level.WARNING,
                    "Network error evaluating flag '" + flagKey + "'. Defaulting to OFF.", e);
            if (e instanceof InterruptedException) {
                // Restore the interrupted status — best practice when catching InterruptedException
                Thread.currentThread().interrupt();
            }
            return FlagEvaluationResult.defaultOff(flagKey);

        } catch (Exception e) {
            // Catch-all safety net — should never reach here, but we cannot let
            // an unexpected exception escape the SDK into the user's application
            log.log(Level.SEVERE,
                    "Unexpected error evaluating flag '" + flagKey + "'. Defaulting to OFF.", e);
            return FlagEvaluationResult.defaultOff(flagKey);
        }
    }


    private FlagEvaluationResult parseEvaluationResponse(String flagKey, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);

            boolean enabled = root.path("enabled").asBoolean(false);
            String reason   = root.path("reason").asText("DEFAULT");

            return FlagEvaluationResult.fromServer(flagKey, enabled, reason);

        } catch (Exception e) {
            log.log(Level.WARNING,
                    "Failed to parse server response for flag '" + flagKey + "': " + json, e);
            return FlagEvaluationResult.defaultOff(flagKey);
        }
    }

    private String buildEvaluateUrl(String flagKey, String userId, String environment) {
        return config.getServerUrl()
                + "/api/v1/flags/evaluate"
                + "?key=" + encode(flagKey)
                + "&userId=" + encode(userId)
                + "&environment=" + encode(environment);
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value; // should never fail with UTF_8, but be safe
        }
    }

    public void shutdown() {
        log.info("FlagApiClient shut down.");
    }

}
