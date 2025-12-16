package com.java_template.common.config;

import com.java_template.common.auth.Authentication;
import com.java_template.common.grpc.client.ClientAuthorizationInterceptor;
import com.java_template.common.grpc.client.CalculationExecutionStrategy;
import com.java_template.common.grpc.client.ControlThreadExecutor;
import com.java_template.common.grpc.client.CriteriaThreadExecutor;
import com.java_template.common.grpc.client.DefaultEventExecutionRouter;
import com.java_template.common.grpc.client.EventExecutionRouter;
import com.java_template.common.grpc.client.ProcessorThreadExecutor;
import com.java_template.common.grpc.client.connection.DefaultReconnectionStrategy;
import com.java_template.common.grpc.client.monitoring.ConnectionStateTracker;
import com.java_template.common.grpc.client.connection.ReconnectionStrategy;
import com.java_template.common.util.SslUtils;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;


/**
 * ABOUTME: Spring Boot auto-configuration for gRPC client components including
 * channel setup, authentication, SSL configuration, and execution strategies.
 */
@Configuration
public class GrpcClientAutoConfiguration {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private final ManagedChannel managedChannel;
    private final Config config;

    public GrpcClientAutoConfiguration(@Lazy final ManagedChannel managedChannel, final Config config) {
        this.managedChannel = managedChannel;
        this.config = config;
    }

    @Bean
    public ManagedChannel managedChannel(
            final ConnectionStateTracker connectionStateTracker,
            @Value("${connection.grpc.skip-ssl:false}") final boolean grpcSkipSsl
    ) {
        final ManagedChannel channel = SslUtils.createGrpcChannelBuilder(
                config.getGrpcAddress(),
                config.getGrpcServerPort(),
                grpcSkipSsl,
                config
        ).build();

        final Supplier<ConnectivityState> currentStateProvider = () -> channel.getState(false);

        final Runnable initSubscription = () -> connectionStateTracker.trackConnectionStateChanged(
                currentStateProvider,
                channel::notifyWhenStateChanged
        );

        channel.notifyWhenStateChanged(currentStateProvider.get(), initSubscription);
        return channel;
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
    public CloudEventsServiceGrpc.CloudEventsServiceBlockingStub cloudEventsServiceBlockingStub(
            final Authentication authentication,
            final ManagedChannel managedChannel
    ) {
        final var authInterceptor = new ClientAuthorizationInterceptor(authentication);
        return CloudEventsServiceGrpc.newBlockingStub(managedChannel)
                .withWaitForReady()
                .withInterceptors(authInterceptor);
    }

    @Bean
    public CloudEventsServiceGrpc.CloudEventsServiceFutureStub cloudEventsServiceFutureStub(
            final Authentication authentication,
            final ManagedChannel managedChannel
    ) {
        final var authInterceptor = new ClientAuthorizationInterceptor(authentication);
        return CloudEventsServiceGrpc.newFutureStub(managedChannel)
                .withWaitForReady()
                .withInterceptors(authInterceptor);
    }

    @Bean
    public EventFormat eventFormat() {
        return EventFormatProvider.getInstance().resolveFormat(ProtobufFormat.PROTO_CONTENT_TYPE);
    }

    // Separate thread pool executors for different event types

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "platform", matchIfMissing = true)
    public CalculationExecutionStrategy processorThreadExecutor() {
        return new ProcessorThreadExecutor(false, config.getProcessorThreadPool());
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "virtual")
    public CalculationExecutionStrategy processorThreadExecutorVirtual() {
        return new ProcessorThreadExecutor(true, config.getProcessorThreadPool());
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "platform", matchIfMissing = true)
    public CalculationExecutionStrategy criteriaThreadExecutor() {
        return new CriteriaThreadExecutor(false, config.getCriteriaThreadPool());
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "virtual")
    public CalculationExecutionStrategy criteriaThreadExecutorVirtual() {
        return new CriteriaThreadExecutor(true, config.getCriteriaThreadPool());
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "platform", matchIfMissing = true)
    public CalculationExecutionStrategy controlThreadExecutor() {
        return new ControlThreadExecutor(false, config.getControlThreadPool());
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "virtual")
    public CalculationExecutionStrategy controlThreadExecutorVirtual() {
        return new ControlThreadExecutor(true, config.getControlThreadPool());
    }

    @Bean
    public EventExecutionRouter eventExecutionRouter(
            @Value("${execution.mode:platform}") String executionMode
    ) {
        boolean useVirtual = "virtual".equals(executionMode);

        CalculationExecutionStrategy processorExecutor = new ProcessorThreadExecutor(useVirtual, config.getProcessorThreadPool());
        CalculationExecutionStrategy criteriaExecutor = new CriteriaThreadExecutor(useVirtual, config.getCriteriaThreadPool());
        CalculationExecutionStrategy controlExecutor = new ControlThreadExecutor(useVirtual, config.getControlThreadPool());

        return new DefaultEventExecutionRouter(processorExecutor, criteriaExecutor, controlExecutor);
    }

    @Bean
    @ConditionalOnProperty(name = "reconnection.strategy", havingValue = "default", matchIfMissing = true)
    public ReconnectionStrategy reconnectionStrategy() {
        return new DefaultReconnectionStrategy(config);
    }

    @PreDestroy
    private void shutdown() throws InterruptedException {
        log.info("Stopping managed channel...");
        if (managedChannel != null && !managedChannel.isShutdown() && !managedChannel.isTerminated()) {
            managedChannel.shutdown().awaitTermination(1, TimeUnit.SECONDS);
        }
        log.info("Managed channel stoped");
    }

}
