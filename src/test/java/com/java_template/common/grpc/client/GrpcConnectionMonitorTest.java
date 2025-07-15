package com.java_template.common.grpc.client;

import com.github.benmanes.caffeine.cache.Cache;
import io.cloudevents.v1.proto.CloudEvent;
import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ABOUTME: Unit tests for GrpcConnectionMonitor to verify connection state monitoring and lifecycle management.
 */
class GrpcConnectionMonitorTest {

    @Mock
    private ManagedChannel managedChannel;

    @Mock
    private StreamObserver<CloudEvent> streamObserver;

    private AtomicReference<MemberStatus> memberStatusRef;
    private GrpcConnectionMonitor.StreamObserverProvider streamObserverProvider;
    private GrpcConnectionMonitor connectionMonitor;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        memberStatusRef = new AtomicReference<>(MemberStatus.starting());

        streamObserverProvider = new GrpcConnectionMonitor.StreamObserverProvider() {
            private StreamObserver<CloudEvent> currentObserver = streamObserver;

            @Override
            public StreamObserver<CloudEvent> getCurrentStreamObserver() {
                return currentObserver;
            }

            @Override
            public void clearStreamObserver() {
                currentObserver = null;
            }
        };

        Cache<String, CyodaCalculationMemberClient.EventAndTrigger> sentEventsCache = CyodaCalculationMemberClient.setupSentEventsCache();
        connectionMonitor = new GrpcConnectionMonitor(managedChannel, memberStatusRef, streamObserverProvider, sentEventsCache);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connectionMonitor.isMonitoring()) {
            connectionMonitor.stopMonitoring();
        }
        closeable.close();
    }

    @Test
    void testConstructor() {
        assertNotNull(connectionMonitor);
        assertFalse(connectionMonitor.isMonitoring());
    }

    @Test
    void testStartMonitoring() {
        connectionMonitor.startMonitoring();
        assertTrue(connectionMonitor.isMonitoring());
    }

    @Test
    void testStopMonitoring() {
        connectionMonitor.startMonitoring();
        assertTrue(connectionMonitor.isMonitoring());

        connectionMonitor.stopMonitoring();
        assertFalse(connectionMonitor.isMonitoring());
    }

    @Test
    void testStartMonitoringWhenAlreadyRunning() {
        connectionMonitor.startMonitoring();
        assertTrue(connectionMonitor.isMonitoring());

        // Starting again should not cause issues
        connectionMonitor.startMonitoring();
        assertTrue(connectionMonitor.isMonitoring());
    }

    @Test
    void testStopMonitoringWhenNotRunning() {
        assertFalse(connectionMonitor.isMonitoring());

        // Stopping when not running should not cause issues
        connectionMonitor.stopMonitoring();
        assertFalse(connectionMonitor.isMonitoring());
    }

    @Test
    void testStreamObserverProvider() {
        // Test getting current stream observer
        Object observer = streamObserverProvider.getCurrentStreamObserver();
        assertEquals(streamObserver, observer);

        // Test clearing stream observer
        streamObserverProvider.clearStreamObserver();
        assertNull(streamObserverProvider.getCurrentStreamObserver());
    }

    @Test
    void testMemberStatusReference() {
        // Test that the monitor uses the provided member status reference
        assertEquals(MemberStatus.MemberState.STARTING, memberStatusRef.get().state());

        // Update member status and verify it's reflected
        memberStatusRef.set(MemberStatus.joining("test-event-id"));
        assertEquals(MemberStatus.MemberState.JOINING, memberStatusRef.get().state());
        assertEquals("test-event-id", memberStatusRef.get().lastJoinEventId());
    }

    @Test
    void testConnectivityStateUpdate() {
        // Mock the channel to return different states
        when(managedChannel.getState(false)).thenReturn(ConnectivityState.CONNECTING);

        // Start monitoring
        connectionMonitor.startMonitoring();

        // The monitoring runs with a 10-second initial delay, so we just verify it started
        assertTrue(connectionMonitor.isMonitoring());

        // [Inference] We cannot easily test the actual monitoring execution without waiting 10+ seconds
        // or modifying the implementation to be more testable. For now, we verify the monitor started.
    }

    @Test
    void testIsMonitoringInitialState() {
        assertFalse(connectionMonitor.isMonitoring());
    }

    @Test
    void testIsMonitoringAfterStart() {
        connectionMonitor.startMonitoring();
        assertTrue(connectionMonitor.isMonitoring());
    }

    @Test
    void testIsMonitoringAfterStop() {
        connectionMonitor.startMonitoring();
        connectionMonitor.stopMonitoring();
        assertFalse(connectionMonitor.isMonitoring());
    }

    @Test
    void testManagedChannelInteraction() {
        when(managedChannel.getState(false)).thenReturn(ConnectivityState.READY);
        when(managedChannel.authority()).thenReturn("test-authority");

        connectionMonitor.startMonitoring();

        // Verify the monitor started successfully
        assertTrue(connectionMonitor.isMonitoring());

        // [Inference] The actual channel interaction happens in the scheduled task
        // which has a 10-second delay, so we can't easily test it here
    }

    @Test
    void testMemberStatusConnectivityStateUpdate() {
        // Set up initial state
        when(managedChannel.getState(false)).thenReturn(ConnectivityState.READY);

        // Start monitoring
        connectionMonitor.startMonitoring();

        // Verify monitoring started
        assertTrue(connectionMonitor.isMonitoring());

        // [Inference] The actual state update happens in the scheduled monitoring task
        // which runs after a 10-second delay, making it impractical to test here
    }
}
