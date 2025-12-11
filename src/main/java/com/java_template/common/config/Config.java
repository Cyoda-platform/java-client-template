package com.java_template.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import java.util.Arrays;
import java.util.List;
import org.cyoda.cloud.api.event.common.DataFormat;

/**
 * ABOUTME: Central configuration class providing environment-based settings
 * for Cyoda platform connection, gRPC communication, and application parameters.
 */
public class Config {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()
            .load();

    public static final String CYODA_HOST = getEnv("CYODA_HOST");
    public static final String CYODA_API_URL = getEnv("CYODA_API_URL", "https://" + CYODA_HOST + "/api");
    public static final String GRPC_ADDRESS = getEnv("GRPC_ADDRESS", "grpc-" + CYODA_HOST);
    public static final int GRPC_SERVER_PORT = Integer.parseInt(getEnv("GRPC_SERVER_PORT", "443"));
    public static final String GRPC_PROCESSOR_TAG = getEnv("GRPC_PROCESSOR_TAG", "cloud_manager_app");

    // gRPC Channel Configuration
    // Message size limits
    public static final int GRPC_MAX_INBOUND_MESSAGE_SIZE = Integer.parseInt(getEnv("GRPC_MAX_INBOUND_MESSAGE_SIZE", "16777216")); // 16MB
    public static final int GRPC_MAX_INBOUND_METADATA_SIZE = Integer.parseInt(getEnv("GRPC_MAX_INBOUND_METADATA_SIZE", "16384")); // 16KB
    // HTTP/2 flow control window - CRITICAL for handling burst traffic from bulk operations
    // Default 1MB is too small for 1000+ simultaneous workflow events
    // Increased to 64MB to handle very high burst traffic
    public static final int GRPC_FLOW_CONTROL_WINDOW = Integer.parseInt(getEnv("GRPC_FLOW_CONTROL_WINDOW", "67108864")); // 64MB
    // Keep-alive settings to prevent stream resets during intensive operations
    public static final long GRPC_KEEP_ALIVE_TIME_SECONDS = Long.parseLong(getEnv("GRPC_KEEP_ALIVE_TIME_SECONDS", "30"));
    public static final long GRPC_KEEP_ALIVE_TIMEOUT_SECONDS = Long.parseLong(getEnv("GRPC_KEEP_ALIVE_TIMEOUT_SECONDS", "10"));
    public static final long GRPC_IDLE_TIMEOUT_SECONDS = Long.parseLong(getEnv("GRPC_IDLE_TIMEOUT_SECONDS", "300")); // 5 minutes

    // Thread pool configurations for different event types
    public static final int PROCESSOR_THREAD_POOL = Integer.parseInt(getEnv("PROCESSOR_THREAD_POOL", "20"));
    public static final int CRITERIA_THREAD_POOL = Integer.parseInt(getEnv("CRITERIA_THREAD_POOL", "20"));
    public static final int CONTROL_THREAD_POOL = Integer.parseInt(getEnv("CONTROL_THREAD_POOL", "3"));

    public static final int HANDSHAKE_TIMEOUT_MS = Integer.parseInt(getEnv("HANDSHAKE_TIMEOUT_MS", "5000"));

    public static final int INITIAL_RECONNECT_DELAY_MS = Integer.parseInt(getEnv("INITIAL_RECONNECT_DELAY_MS", "200"));
    public static final int MAX_RECONNECT_DELAY_MS = Integer.parseInt(getEnv("MAX_RECONNECT_DELAY_MS", "10000"));
    public static final int FAILED_RECONNECTS_LIMIT = Integer.parseInt(getEnv("FAILED_RECONNECTS_LIMIT", "10"));


    public static final String CYODA_CLIENT_ID = getEnv("CYODA_CLIENT_ID");
    public static final String CYODA_CLIENT_SECRET = getEnv("CYODA_CLIENT_SECRET");

    public static final DataFormat GRPC_COMMUNICATION_DATA_FORMAT = DataFormat.fromValue(getEnv("GRPC_COMMUNICATION_DATA_FORMAT", DataFormat.JSON.value()));
    public static final String EVENT_SOURCE_URI = "urn:cyoda:calculation-member:" + GRPC_PROCESSOR_TAG;

    // Monitoring
    public static final int SENT_EVENTS_CACHE_MAX_SIZE = Integer.parseInt(getEnv("SENT_EVENTS_CACHE_MAX_SIZE", "100"));
    public static final int MONITORING_SCHEDULER_INITIAL_DELAY_SECONDS = Integer.parseInt(getEnv("MONITORING_SCHEDULER_INITIAL_DELAY_SECONDS", "1"));
    public static final int MONITORING_SCHEDULER_DELAY_SECONDS = Integer.parseInt(getEnv("MONITORING_SCHEDULER_DELAY_SECONDS", "3"));
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
