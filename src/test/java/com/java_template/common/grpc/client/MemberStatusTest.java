package com.java_template.common.grpc.client;

import io.grpc.ConnectivityState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ABOUTME: Unit tests for MemberStatus record class to verify lifecycle state tracking functionality.
 */
class MemberStatusTest {

    @Test
    void testStartingStatus() {
        MemberStatus status = MemberStatus.starting();

        assertEquals(MemberStatus.MemberState.STARTING, status.state());
        assertNull(status.lastJoinEventId());
        assertEquals(0, status.retryCount());
        assertEquals(0L, status.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, status.lastKnownState());
    }

    @Test
    void testJoiningStatus() {
        String joinEventId = "test-join-event-123";
        MemberStatus status = MemberStatus.joining(joinEventId);

        assertEquals(MemberStatus.MemberState.JOINING, status.state());
        assertEquals(joinEventId, status.lastJoinEventId());
        assertEquals(0, status.retryCount());
        assertEquals(0L, status.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, status.lastKnownState());
    }

    @Test
    void testJoinedStatus() {
        String joinEventId = "test-join-event-123";
        MemberStatus joiningStatus = MemberStatus.joining(joinEventId);
        MemberStatus joinedStatus = joiningStatus.joined();

        assertEquals(MemberStatus.MemberState.JOINED, joinedStatus.state());
        assertEquals(joinEventId, joinedStatus.lastJoinEventId());
        assertEquals(0, joinedStatus.retryCount());
        assertEquals(0L, joinedStatus.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, joinedStatus.lastKnownState());
    }

    @Test
    void testRejectedStatus() {
        String joinEventId = "test-join-event-123";
        MemberStatus joiningStatus = MemberStatus.joining(joinEventId);
        MemberStatus rejectedStatus = joiningStatus.rejected();

        assertEquals(MemberStatus.MemberState.REJECTED, rejectedStatus.state());
        assertEquals(joinEventId, rejectedStatus.lastJoinEventId());
        assertEquals(0, rejectedStatus.retryCount());
        assertEquals(0L, rejectedStatus.lastKeepAliveTimestamp());
        assertEquals(ConnectivityState.IDLE, rejectedStatus.lastKnownState());
    }

    @Test
    void testOfflineStatus() {
        String joinEventId = "test-join-event-123";
        MemberStatus joiningStatus = MemberStatus.joining(joinEventId);
        MemberStatus offlineStatus = joiningStatus.offline();

        assertEquals(MemberStatus.MemberState.OFFLINE, offlineStatus.state());
        assertEquals(joinEventId, offlineStatus.lastJoinEventId());
    }

    @Test
    void testStatusTransitions() {
        // Test complete lifecycle
        MemberStatus starting = MemberStatus.starting();
        assertEquals(MemberStatus.MemberState.STARTING, starting.state());

        String joinEventId = "test-event-456";
        MemberStatus joining = MemberStatus.joining(joinEventId);
        assertEquals(MemberStatus.MemberState.JOINING, joining.state());
        assertEquals(joinEventId, joining.lastJoinEventId());

        MemberStatus joined = joining.joined();
        assertEquals(MemberStatus.MemberState.JOINED, joined.state());
        assertEquals(joinEventId, joined.lastJoinEventId());

        MemberStatus offline = joined.offline();
        assertEquals(MemberStatus.MemberState.OFFLINE, offline.state());
        assertEquals(joinEventId, offline.lastJoinEventId());
    }

    @Test
    void testRecordEquality() {
        String joinEventId = "test-event-789";
        MemberStatus status1 = MemberStatus.joining(joinEventId);
        MemberStatus status2 = MemberStatus.joining(joinEventId);

        assertEquals(status1, status2);
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void testRecordToString() {
        String joinEventId = "test-event-abc";
        MemberStatus status = MemberStatus.joining(joinEventId);

        String toString = status.toString();
        assertTrue(toString.contains("JOINING"));
        assertTrue(toString.contains(joinEventId));
    }

    @Test
    void testWithRetryCount() {
        MemberStatus status = MemberStatus.starting();
        MemberStatus updatedStatus = status.withRetryCount(3);

        assertEquals(MemberStatus.MemberState.STARTING, updatedStatus.state());
        assertEquals(3, updatedStatus.retryCount());
        assertEquals(0L, updatedStatus.lastKeepAliveTimestamp());
    }

    @Test
    void testWithIncrementedRetryCount() {
        MemberStatus status = MemberStatus.starting().withRetryCount(2);
        MemberStatus incrementedStatus = status.withIncrementedRetryCount();

        assertEquals(MemberStatus.MemberState.STARTING, incrementedStatus.state());
        assertEquals(3, incrementedStatus.retryCount());
    }

    @Test
    void testWithKeepAliveTimestamp() {
        long timestamp = System.currentTimeMillis();
        MemberStatus status = MemberStatus.starting();
        MemberStatus updatedStatus = status.withKeepAliveTimestamp(timestamp);

        assertEquals(MemberStatus.MemberState.STARTING, updatedStatus.state());
        assertEquals(timestamp, updatedStatus.lastKeepAliveTimestamp());
        assertEquals(0, updatedStatus.retryCount());
    }

    @Test
    void testJoiningWithEventId() {
        String originalEventId = "original-event";
        String newEventId = "new-event";
        MemberStatus status = MemberStatus.joining(originalEventId).withRetryCount(5);
        MemberStatus updatedStatus = status.joiningWithEventId(newEventId);

        assertEquals(MemberStatus.MemberState.JOINING, updatedStatus.state());
        assertEquals(newEventId, updatedStatus.lastJoinEventId());
        assertEquals(5, updatedStatus.retryCount()); // Should not reset retry count if previous state was JOINING
        assertEquals(ConnectivityState.IDLE, updatedStatus.lastKnownState());
    }

    @Test
    void testWithConnectivityState() {
        MemberStatus status = MemberStatus.starting();
        MemberStatus updatedStatus = status.withConnectivityState(ConnectivityState.READY);

        assertEquals(MemberStatus.MemberState.STARTING, updatedStatus.state());
        assertEquals(ConnectivityState.READY, updatedStatus.lastKnownState());
        assertEquals(0, updatedStatus.retryCount());
        assertEquals(0L, updatedStatus.lastKeepAliveTimestamp());
    }

    @Test
    void testConnectivityStatePreservation() {
        MemberStatus status = MemberStatus.starting()
                .withConnectivityState(ConnectivityState.CONNECTING)
                .withRetryCount(3);

        MemberStatus joinedStatus = status.joined();

        assertEquals(MemberStatus.MemberState.JOINED, joinedStatus.state());
        assertEquals(ConnectivityState.CONNECTING, joinedStatus.lastKnownState());
        assertEquals(3, joinedStatus.retryCount());
    }
}
