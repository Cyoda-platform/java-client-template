package com.java_template.common.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.java_template.common.auth.Authentication;
import com.java_template.common.grpc.client.ClientAuthorizationInterceptor;
import com.java_template.common.grpc.client.CyodaCalculationMemberClient;
import com.java_template.common.grpc.client_v2.CalculationExecutionStrategy;
import com.java_template.common.grpc.client_v2.DefaultReconnectionStrategy;
import com.java_template.common.grpc.client_v2.ReconnectionStrategy;
import com.java_template.common.util.SslUtils;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;
import io.grpc.ManagedChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.java_template.common.config.Config.GRPC_ADDRESS;
import static com.java_template.common.config.Config.GRPC_SERVER_PORT;

@Configuration
public class GrpcClientAutoConfiguration {

    private static final int EXTERNAL_CALCULATIONS_THREAD_POOL = 10; // TODO: Move to props

    @Bean
    public ManagedChannel managedChannel() {
        return SslUtils.createGrpcChannelBuilder(GRPC_ADDRESS, GRPC_SERVER_PORT).build();
    }

    @Bean
    public Cache<String, CyodaCalculationMemberClient.EventAndTrigger> sentEventsCache() {
        return Caffeine.newBuilder()
                .maximumSize(100) // TODO: Move to props
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .build();
    }

    @Bean
    public CloudEventsServiceGrpc.CloudEventsServiceStub cloudEventsServiceStub(
            final Authentication authentication,
            final ManagedChannel managedChannel
    ) {
        final var authInterceptor = new ClientAuthorizationInterceptor(authentication);
        return CloudEventsServiceGrpc.newStub(managedChannel)
                .withWaitForReady()
                .withInterceptors(authInterceptor);
    }

    @Bean
    public EventFormat eventFormat() {
        return EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "platform", matchIfMissing = true)
    public CalculationExecutionStrategy platformThreadsCalculationExecutor() {
        final var executor = createExternalCalculationExecutor(Thread.ofPlatform());
        try (executor) {
            return executor::submit;
        }
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "virtual")
    public CalculationExecutionStrategy virtualThreadsCalculationExecutor() {
        final var executor = createExternalCalculationExecutor(Thread.ofVirtual());
        try (executor) {
            return executor::submit;
        }
    }

    private ExecutorService createExternalCalculationExecutor(final Thread.Builder factoryBuilder) {
        return Executors.newFixedThreadPool(
                EXTERNAL_CALCULATIONS_THREAD_POOL,
                factoryBuilder.name("external-calculation").factory()
        );
    }

    @Bean
    @ConditionalOnProperty(name = "reconnection.strategy", havingValue = "default", matchIfMissing = true)
    public ReconnectionStrategy reconnectionStrategy() {
        return new DefaultReconnectionStrategy();
    }

}
