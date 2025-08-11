package com.java_template.common.config;

import com.java_template.common.auth.Authentication;
import com.java_template.common.grpc.client.ClientAuthorizationInterceptor;
import com.java_template.common.grpc.client_v2.CalculationExecutionStrategy;
import com.java_template.common.grpc.client_v2.DefaultReconnectionStrategy;
import com.java_template.common.grpc.client_v2.PlatformThreadsCalculationExecutor;
import com.java_template.common.grpc.client_v2.VirtualThreadsCalculationExecutor;
import com.java_template.common.grpc.client_v2.ConnectionStateTracker;
import com.java_template.common.grpc.client_v2.ReconnectionStrategy;
import com.java_template.common.util.SslUtils;
import io.cloudevents.core.format.EventFormat;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.protobuf.ProtobufFormat;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.cyoda.cloud.api.grpc.CloudEventsServiceGrpc;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.java_template.common.config.Config.EXTERNAL_CALCULATIONS_THREAD_POOL;
import static com.java_template.common.config.Config.GRPC_ADDRESS;
import static com.java_template.common.config.Config.GRPC_SERVER_PORT;

@Configuration
public class GrpcClientAutoConfiguration {

    @Bean
    public ManagedChannel managedChannel(final ConnectionStateTracker connectionStateTracker) {
        final ManagedChannel channel = SslUtils.createGrpcChannelBuilder(GRPC_ADDRESS, GRPC_SERVER_PORT, true).build();
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

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "platform", matchIfMissing = true)
    public CalculationExecutionStrategy platformThreadsCalculationExecutor() {
        return new PlatformThreadsCalculationExecutor();
    }

    @Bean
    @ConditionalOnProperty(name = "execution.mode", havingValue = "virtual")
    public CalculationExecutionStrategy virtualThreadsCalculationExecutor() {
        return new VirtualThreadsCalculationExecutor();
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
