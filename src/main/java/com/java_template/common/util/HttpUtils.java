package com.java_template.common.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;


/**
 * ABOUTME: Utility component providing HTTP client operations for REST API communication
 * with JSON request/response handling and error management.
 */
@Component
public class HttpUtils {
    private final HttpClient client = SslUtils.createHttpClient();
    private final Logger logger = LoggerFactory.getLogger(HttpUtils.class);
    private final ObjectMapper om;
    private final JsonUtils jsonUtils;

    public HttpUtils(JsonUtils jsonUtils, ObjectMapper om) {
        this.jsonUtils = jsonUtils;
        this.om = om;
    }

    private String ensureBearerToken(String token) {
        return token.startsWith("Bearer") ? token : "Bearer " + token;
    }

    private HttpRequest.Builder createRequestBuilder(String url, String token, String method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
        if (token != null && !token.isBlank()) {
            builder.header("Authorization", ensureBearerToken(token));
        }
        return builder.method(method, HttpRequest.BodyPublishers.noBody());
    }


    private HttpRequest createRequest(String url, String token, String method, Object data) {
        HttpRequest.Builder builder = createRequestBuilder(url, token, method);
        if (data != null) {
            builder.method(method, HttpRequest.BodyPublishers.ofString(jsonUtils.toJson(data), StandardCharsets.UTF_8));
        }
        return builder.build();
    }

    private CompletableFuture<ObjectNode> sendRequest(String url, String token, String method, Object data) {
        HttpRequest request = createRequest(url, token, method, data);
        ObjectNode result = om.createObjectNode();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String responseBody = response.body();

                    if (statusCode >= 200 && statusCode < 300) {
                        logger.info("[{}] {} {} succeeded", statusCode, method, url);
                    } else if (statusCode >= 300 && statusCode < 400) {
                        logger.info("[{}] {} {} redirect: {}", statusCode, method, url, responseBody);
                    } else if (statusCode >= 400 && statusCode < 500) {
                        throw new ResponseStatusException(HttpStatus.valueOf(statusCode), extractErrorMessage(responseBody));
                    } else if (statusCode >= 500) {
                        throw new ResponseStatusException(HttpStatus.valueOf(statusCode), extractErrorMessage(responseBody));
                    }

                    try {
                        JsonNode responseJson = om.readTree(responseBody);
                        if (responseJson.isObject()) {
                            result.set("json", (ObjectNode) responseJson);
                        } else if (responseJson.isTextual()) {
                            try {
                                JsonNode parsedJson = om.readTree(responseJson.asText());
                                if (parsedJson.isObject()) {
                                    result.set("json", (ObjectNode) parsedJson);
                                } else {
                                    result.set("json", parsedJson);
                                }
                            } catch (Exception e) {
                                result.put("json", responseJson.asText());
                            }
                        } else {
                            result.put("json", responseJson);
                        }
                        result.put("status", statusCode);
                        return result;
                    } catch (Exception e) {
                        logger.warn("Failed to parse response JSON: {}", e.getMessage());
                        result.put("status", statusCode);
                        result.put("json", responseBody);
                        return result;
                    }
                });
    }

    public CompletableFuture<ObjectNode> sendGetRequest(String token, String apiUrl, String path, Map<String, String> params) {
        String fullUrl = buildUrlWithParams(apiUrl, path, params);
        return sendRequest(fullUrl, token, "GET", null);
    }

    public CompletableFuture<ObjectNode> sendGetRequest(String token, String apiUrl, String path) {
        String fullUrl = buildUrlWithParams(apiUrl, path, null);
        return sendRequest(fullUrl, token, "GET", null);
    }

    public CompletableFuture<ObjectNode> sendPostRequest(String token, String apiUrl, String path, Object data) {
        String fullUrl = buildUrlWithParams(apiUrl, path, null);
        return sendRequest(fullUrl, token, "POST", data);
    }

    public CompletableFuture<ObjectNode> sendPostRequest(String token, String apiUrl, String path, Object data, Map<String, String> params) {
        String fullUrl = buildUrlWithParams(apiUrl, path, params);
        return sendRequest(fullUrl, token, "POST", data);
    }

    public CompletableFuture<ObjectNode> sendPutRequest(String token, String apiUrl, String path, Object data) {
        String fullUrl = buildUrlWithParams(apiUrl, path, null);
        return sendRequest(fullUrl, token, "PUT", data);
    }

    public CompletableFuture<ObjectNode> sendDeleteRequest(String token, String apiUrl, String path) {
        String fullUrl = buildUrlWithParams(apiUrl, path, null);
        return sendRequest(fullUrl, token, "DELETE", null);
    }

    private String buildUrlWithParams(String apiUrl, String path, Map<String, String> params) {
        String baseUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        String fullUrl = (path == null || path.isBlank()) ? baseUrl : baseUrl + "/" + path;

        if (params == null || params.isEmpty()) {
            return fullUrl;
        }
        String queryString = params.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return fullUrl + "?" + queryString;
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode errorNode = om.readTree(responseBody);
            if (errorNode.has("errorMessage")) {
                return errorNode.get("errorMessage").asText();
            } else if (errorNode.has("message")) {
                return errorNode.get("message").asText();
            } else if (errorNode.has("detail")) {
                return errorNode.get("detail").asText();
            }
        } catch (Exception ignored) {}
        return responseBody;
    }
}
