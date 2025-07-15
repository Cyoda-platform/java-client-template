package com.java_template.common.grpc.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.auth.Authentication;
import io.grpc.ConnectivityState;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Integration tests for MemberStatus functionality in CyodaCalculationMemberClient.
 * Tests the member status lifecycle tracking without requiring actual gRPC connections.
 */
class CyodaCalculationMemberClientMemberStatusTest {

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private Authentication authentication;

    @Mock
    private EventHandlingStrategy<BaseEvent> eventHandlingStrategy;

    private CyodaCalculationMemberClient client;

    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        List<EventHandlingStrategy<? extends BaseEvent>> strategies = List.of(eventHandlingStrategy);
        client = new CyodaCalculationMemberClient(objectMapper, strategies, authentication);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void testInitialMemberStatus() {
        // Test that the client starts with STARTING status
        MemberStatus status = client.getMemberStatus();

        assertNotNull(status);
        assertEquals(MemberStatus.MemberState.STARTING, status.state());
        assertNull(status.lastJoinEventId());
        assertEquals(0, status.retryCount());
        assertEquals(0L, status.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, status.lastKnownState());
    }

    @Test
    void testMemberStatusIsAtomic() {
        // Test that getMemberStatus returns the current atomic reference value
        MemberStatus initialStatus = client.getMemberStatus();
        MemberStatus secondCall = client.getMemberStatus();

        // Should be the same object reference since it's the same atomic reference
        assertEquals(initialStatus, secondCall);
        assertEquals(MemberStatus.MemberState.STARTING, initialStatus.state());
        assertEquals(MemberStatus.MemberState.STARTING, secondCall.state());
        assertEquals(0, initialStatus.retryCount());
        assertEquals(0L, initialStatus.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, initialStatus.lastKnownState());
    }

    @Test
    void testMemberStatusRecord() {
        // Test the record functionality
        MemberStatus status = client.getMemberStatus();

        // Test record methods
        assertNotNull(status.toString());
        assertTrue(status.toString().contains("STARTING"));

        // Test equality
        MemberStatus anotherStartingStatus = MemberStatus.starting();
        assertEquals(status, anotherStartingStatus);
        assertEquals(status.hashCode(), anotherStartingStatus.hashCode());
    }

    @Test
    void testMemberStateEnum() {
        // Test all enum values are accessible
        MemberStatus.MemberState[] states = MemberStatus.MemberState.values();

        assertEquals(5, states.length);
        assertEquals(MemberStatus.MemberState.STARTING, states[0]);
        assertEquals(MemberStatus.MemberState.JOINING, states[1]);
        assertEquals(MemberStatus.MemberState.JOINED, states[2]);
        assertEquals(MemberStatus.MemberState.REJECTED, states[3]);
        assertEquals(MemberStatus.MemberState.OFFLINE, states[4]);
    }

    @Test
    void testMemberStateEnumToString() {
        // Test enum toString methods
        assertEquals("STARTING", MemberStatus.MemberState.STARTING.toString());
        assertEquals("JOINING", MemberStatus.MemberState.JOINING.toString());
        assertEquals("JOINED", MemberStatus.MemberState.JOINED.toString());
        assertEquals("REJECTED", MemberStatus.MemberState.REJECTED.toString());
        assertEquals("OFFLINE", MemberStatus.MemberState.OFFLINE.toString());
    }
}
