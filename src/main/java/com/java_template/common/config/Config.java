package com.java_template.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Base64;
import java.util.Arrays;
import java.util.List;

public class Config {
    private static final Dotenv dotenv = Dotenv.load();

    public static final String CYODA_HOST = getEnv("CYODA_HOST");
    public static final String CYODA_API_URL = getEnv("CYODA_API_URL", "https://" + CYODA_HOST + "/api");
    public static final String GRPC_ADDRESS = getEnv("GRPC_ADDRESS", "grpc-" + CYODA_HOST);
    public static final int GRPC_SERVER_PORT = Integer.parseInt(getEnv("GRPC_SERVER_PORT", "443"));
    public static final String GRPC_PROCESSOR_TAG = getEnv("GRPC_PROCESSOR_TAG", "cloud_manager_app");

    public static final String CYODA_CLIENT_ID = getEnv("CYODA_CLIENT_ID");
    public static final String CYODA_CLIENT_SECRET = getEnv("CYODA_CLIENT_SECRET");

    public static final String CHAT_ID = dotenv.get("CHAT_ID");

    public static final long KEEP_ALIVE_WARNING_THRESHOLD = Long.parseLong(dotenv.get("KEEP_ALIVE_WARNING_THRESHOLD", "60000"));

    // SSL Configuration
    public static final boolean SSL_TRUST_ALL = Boolean.parseBoolean(getEnv("SSL_TRUST_ALL", "false"));
    public static final String SSL_TRUSTED_HOSTS = getEnv("SSL_TRUSTED_HOSTS", "");

    public static final boolean INCLUDE_DEFAULT_OPERATIONS = Boolean.parseBoolean(getEnv("INCLUDE_DEFAULT_OPERATIONS", "false"));

    /**
     * Get list of hosts that should be trusted even with self-signed certificates
     * @return List of trusted hosts
     */
    public static List<String> getTrustedHosts() {
        if (SSL_TRUSTED_HOSTS.isBlank()) {
            return List.of();
        }
        return Arrays.stream(SSL_TRUSTED_HOSTS.split(","))
                .map(String::trim)
                .filter(host -> !host.isEmpty())
                .toList();
    }

    private static String getEnv(String key, String defaultValue) {
        String value = dotenv.get(key);
        if (value == null) {
            value = System.getenv(key);
        }
        return value != null ? value : defaultValue;
    }

    private static String getEnv(String key) {
        String value = dotenv.get(key);
        if (value == null) {
            value = System.getenv(key);
        }
        if (value == null) {
            throw new RuntimeException("Missing required environment variable: " + key);
        }
        return value;
    }

}
