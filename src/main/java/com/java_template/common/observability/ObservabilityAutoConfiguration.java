package com.java_template.common.observability;

import io.opentelemetry.api.OpenTelemetry;
import com.java_template.common.grpc.client.event_handling.EventHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * ABOUTME: Auto-configuration that registers observability beans (event handler decorator,
 * gRPC interceptor, stream listener) conditionally when the OTel agent provides an OpenTelemetry bean.
 * When the OTel agent is not attached, these beans are not created and the app runs as before.
 */
@Configuration
@ConditionalOnBean(OpenTelemetry.class)
public class ObservabilityAutoConfiguration {

    @Bean
    public GrpcObservabilityInterceptor grpcObservabilityInterceptor(OpenTelemetry openTelemetry) {
        return new GrpcObservabilityInterceptor(openTelemetry);
    }

    @Bean
    public StreamObservabilityListener streamObservabilityListener(OpenTelemetry openTelemetry) {
        return new StreamObservabilityListener(openTelemetry);
    }

    @Bean
    @Primary
    public EventHandler observableEventHandler(EventHandler delegate, OpenTelemetry openTelemetry) {
        return new ObservableEventHandler(delegate, openTelemetry);
    }
}
