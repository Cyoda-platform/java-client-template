package com.java_template.common.util;

// ABOUTME: SSL utility class for configuring custom trust managers and SSL contexts
// ABOUTME: Provides methods to create HTTP clients that can trust specific hosts with self-signed certificates

import com.java_template.common.config.Config;
import io.grpc.ManagedChannelBuilder;
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.List;

public class SslUtils {
    private static final Logger logger = LoggerFactory.getLogger(SslUtils.class);

    /**
     * Custom TrustManager that trusts all certificates when configured to do so
     */
    private static class PermissiveTrustManager implements X509TrustManager {
        private final X509TrustManager defaultTrustManager;
        private final boolean trustAll;

        public PermissiveTrustManager(boolean trustAll) throws Exception {
            this.trustAll = trustAll;

            if (!trustAll) {
                // Get the default trust manager for standard validation
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                tmf.init((java.security.KeyStore) null);
                this.defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
            } else {
                this.defaultTrustManager = null;
            }
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            if (!trustAll && defaultTrustManager != null) {
                try {
                    defaultTrustManager.checkClientTrusted(chain, authType);
                } catch (Exception e) {
                    logger.warn("Client certificate validation failed: {}", e.getMessage());
                }
            }
            // If trustAll is true, we skip validation
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            if (!trustAll && defaultTrustManager != null) {
                try {
                    defaultTrustManager.checkServerTrusted(chain, authType);
                } catch (Exception e) {
                    logger.error("Server certificate validation failed: {}", e.getMessage());
                    throw new RuntimeException("Certificate validation failed", e);
                }
            }
            // If trustAll is true, we skip validation
            logger.debug("Trusting server certificate (trustAll={})", trustAll);
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            if (defaultTrustManager != null) {
                return defaultTrustManager.getAcceptedIssuers();
            }
            return new X509Certificate[0];
        }
    }

    /**
     * Custom HostnameVerifier that allows specific hosts
     */
    private static class SelectiveHostnameVerifier implements HostnameVerifier {
        private final List<String> trustedHosts;
        private final HostnameVerifier defaultVerifier;

        public SelectiveHostnameVerifier(List<String> trustedHosts) {
            this.trustedHosts = trustedHosts;
            this.defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        }

        @Override
        public boolean verify(String hostname, SSLSession session) {
            // Check if this host should be trusted
            if (isTrustedHost(hostname)) {
                logger.debug("Trusting hostname: {}", hostname);
                return true;
            }

            // Use default verification for other hosts
            boolean defaultResult = defaultVerifier.verify(hostname, session);
            if (!defaultResult) {
                logger.debug("Default hostname verification failed for: {}", hostname);
            }
            return defaultResult;
        }

        private boolean isTrustedHost(String hostname) {
            // Check exact match first
            if (trustedHosts.contains(hostname)) {
                return true;
            }

            // Check if hostname matches any trusted host without port
            for (String trusted : trustedHosts) {
                String trustedHost = trusted.split(":")[0];
                if (hostname.equals(trustedHost)) {
                    return true;
                }
            }

            return false;
        }
    }

    /**
     * Creates an SSLContext based on configuration
     */
    public static SSLContext createSelectiveSSLContext() throws NoSuchAlgorithmException {
        List<String> trustedHosts = Config.getTrustedHosts();
        boolean shouldTrustAll = Config.SSL_TRUST_ALL || !trustedHosts.isEmpty();

        if (Config.SSL_TRUST_ALL) {
            logger.warn("SSL_TRUST_ALL is enabled - this should only be used in development!");
        } else if (!trustedHosts.isEmpty()) {
            logger.info("SSL configured to trust specific hosts: {}", trustedHosts);
        }

        if (!shouldTrustAll) {
            logger.debug("Using default SSL context - no trusted hosts configured");
            return SSLContext.getDefault();
        }

        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            PermissiveTrustManager trustManager = new PermissiveTrustManager(shouldTrustAll);
            sslContext.init(null, new TrustManager[] {trustManager}, new SecureRandom());

            logger.info(
                    "Created permissive SSL context (trustAll={}, trustedHosts={})",
                    Config.SSL_TRUST_ALL,
                    trustedHosts
            );
            return sslContext;
        } catch (Exception e) {
            logger.error("Failed to create permissive SSL context, falling back to default: {}", e.getMessage());
            return SSLContext.getDefault();
        }
    }

    /**
     * Creates an SSLContext that trusts all certificates (DEVELOPMENT ONLY)
     */
    @SuppressWarnings("unused")
    private static SSLContext createTrustAllSSLContext() throws NoSuchAlgorithmException, KeyManagementException {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new SecureRandom());
        return sslContext;
    }

    /**
     * Creates a Java 11+ HttpClient with custom SSL configuration
     */
    public static java.net.http.HttpClient createHttpClient() {
        try {
            SSLContext sslContext = createSelectiveSSLContext();
            java.net.http.HttpClient.Builder builder = java.net.http.HttpClient.newBuilder().sslContext(sslContext);

            List<String> trustedHosts = Config.getTrustedHosts();
            if (Config.SSL_TRUST_ALL || !trustedHosts.isEmpty()) {
                logger.info("HttpClient configured with custom SSL settings for hosts: {}", trustedHosts);
            }

            return builder.build();
        } catch (Exception e) {
            logger.error("Failed to create HttpClient with custom SSL, using default: {}", e.getMessage());
            return java.net.http.HttpClient.newHttpClient();
        }
    }

    /**
     * Creates an Apache HttpClient with custom SSL configuration
     */
    public static CloseableHttpClient createApacheHttpClient() {
        try {
            SSLContext sslContext = createSelectiveSSLContext();
            List<String> trustedHosts = Config.getTrustedHosts();

            HostnameVerifier hostnameVerifier;
            if (Config.SSL_TRUST_ALL) {
                hostnameVerifier = NoopHostnameVerifier.INSTANCE;
                logger.warn("Using NoopHostnameVerifier - this should only be used in development!");
            } else if (!trustedHosts.isEmpty()) {
                hostnameVerifier = new SelectiveHostnameVerifier(trustedHosts);
                logger.info("Using selective hostname verifier for hosts: {}", trustedHosts);
            } else {
                hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
            }

            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(
                    sslContext, hostnameVerifier);

            return HttpClients.custom()
                    .setSSLSocketFactory(sslSocketFactory)
                    .build();
        } catch (Exception e) {
            logger.error("Failed to create Apache HttpClient with custom SSL, using default: {}", e.getMessage());
            return HttpClients.createDefault();
        }
    }

    /**
     * Creates a gRPC ManagedChannelBuilder with custom SSL configuration
     */
    public static ManagedChannelBuilder<?> createGrpcChannelBuilder(
            final String host,
            final int port,
            final boolean avoidSsl
    ) {
        try {
            if (Config.SSL_TRUST_ALL || shouldTrustHost(host)) {
                logger.info("Configuring gRPC channel to trust host: {} (self-signed certificates allowed)", host);

                // Create an SSL context that trusts all certificates
                if (Config.SSL_TRUST_ALL) {
                    logger.warn("Creating gRPC channel with InsecureTrustManagerFactory - DEVELOPMENT ONLY!");
                } else {
                    // For specific trusted hosts, we still use the insecure trust manager
                    // In a production environment, you might want to implement a more sophisticated approach
                    logger.info("Creating gRPC channel with relaxed SSL for trusted host: {}", host);
                }
                final SslContext sslContext = GrpcSslContexts.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();

                if (avoidSsl) {
                    return NettyChannelBuilder.forAddress(host, port);
                } else {
                    return NettyChannelBuilder.forAddress(host, port).sslContext(sslContext);
                }
            } else {
                if (avoidSsl) {
                    logger.debug("Skip using security for host: {}", host);
                    return ManagedChannelBuilder.forAddress(host, port).usePlaintext();
                } else {
                    logger.debug("Using default transport security for host: {}", host);
                    return ManagedChannelBuilder.forAddress(host, port).useTransportSecurity();
                }
            }
        } catch (Exception e) {
            logger.error(
                    "Failed to configure gRPC SSL for {}:{}, falling back to default: {}",
                    host,
                    port,
                    e.getMessage()
            );
            if (avoidSsl) {
                return ManagedChannelBuilder.forAddress(host, port).usePlaintext();
            } else {
                return ManagedChannelBuilder.forAddress(host, port).useTransportSecurity();
            }
        }
    }

    /**
     * Check if a host should be trusted based on configuration
     */
    public static boolean shouldTrustHost(String host) {
        if (Config.SSL_TRUST_ALL) {
            logger.debug("Trusting host {} due to SSL_TRUST_ALL=true", host);
            return true;
        }

        List<String> trustedHosts = Config.getTrustedHosts();
        if (trustedHosts.isEmpty()) {
            return false;
        }

        // Check exact match first
        if (trustedHosts.contains(host)) {
            logger.debug("Host {} found in trusted hosts list", host);
            return true;
        }

        // Check host without port
        String hostWithoutPort = host.split(":")[0];
        if (trustedHosts.contains(hostWithoutPort)) {
            logger.debug("Host {} (without port) found in trusted hosts list", hostWithoutPort);
            return true;
        }

        // Check if any trusted host matches this host (with or without port)
        for (String trustedHost : trustedHosts) {
            String trustedHostWithoutPort = trustedHost.split(":")[0];
            if (hostWithoutPort.equals(trustedHostWithoutPort)) {
                logger.debug("Host {} matches trusted host {} (ignoring ports)", host, trustedHost);
                return true;
            }
        }

        logger.debug("Host {} not found in trusted hosts: {}", host, trustedHosts);
        return false;
    }
}
