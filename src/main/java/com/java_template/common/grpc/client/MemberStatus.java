package com.java_template.common.grpc.client;

import io.grpc.ConnectivityState;

/**
 * ABOUTME: Record class that tracks the client's lifecycle state and join event information.
 * Provides thread-safe status tracking for the calculation member client connection lifecycle.
 */
public record MemberStatus(
        MemberState state,
        String lastJoinEventId,
        int retryCount,
        long lastKeepAliveTimestamp,
        ConnectivityState lastKnownState
) {

    /**
     * Enum representing the different states of the member client lifecycle.
     */
    public enum MemberState {
        /**
         * Initial state during client initialization.
         */
        STARTING,

        /**
         * Sent join request, awaiting response.
         */
        JOINING,

        /**
         * Successfully joined the calculation network.
         */
        JOINED,

        /**
         * Join request was explicitly rejected.
         */
        REJECTED,

        /**
         * Final state after max retries or explicit shutdown.
         */
        OFFLINE
    }

    /**
     * Creates a new MemberStatus with STARTING state and no join event ID.
     */
    public static MemberStatus starting() {
        return new MemberStatus(MemberState.STARTING, null, 0, 0L, ConnectivityState.IDLE);
    }

    /**
     * Creates a new MemberStatus with JOINING state and the specified join event ID.
     */
    public static MemberStatus joining(String joinEventId) {
        return new MemberStatus(MemberState.JOINING, joinEventId, 0, 0L, ConnectivityState.IDLE);
    }

    /**
     * Creates a new MemberStatus with JOINED state, preserving all other fields.
     */
    public MemberStatus joined() {
        return new MemberStatus(MemberState.JOINED, this.lastJoinEventId, this.retryCount, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with REJECTED state, preserving all other fields.
     */
    public MemberStatus rejected() {
        return new MemberStatus(MemberState.REJECTED, this.lastJoinEventId, this.retryCount, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with OFFLINE state, preserving all other fields.
     */
    public MemberStatus offline() {
        return new MemberStatus(MemberState.OFFLINE, this.lastJoinEventId, this.retryCount, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with updated retry count, preserving all other fields.
     */
    public MemberStatus withRetryCount(int newRetryCount) {
        return new MemberStatus(this.state, this.lastJoinEventId, newRetryCount, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with incremented retry count, preserving all other fields.
     */
    public MemberStatus withIncrementedRetryCount() {
        return new MemberStatus(this.state, this.lastJoinEventId, this.retryCount + 1, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with updated keep alive timestamp, preserving all other fields.
     */
    public MemberStatus withKeepAliveTimestamp(long timestamp) {
        return new MemberStatus(this.state, this.lastJoinEventId, this.retryCount, timestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with JOINING state and updated join event ID, resetting retry count
     * if the prior state was NOT JOINING and NOT OFFLINE.
     */
    public MemberStatus joiningWithEventId(String joinEventId) {
        int cnt = this.state == MemberState.JOINING || this.state == MemberState.OFFLINE ? this.retryCount : 0;
        return new MemberStatus(MemberState.JOINING, joinEventId, cnt, this.lastKeepAliveTimestamp, this.lastKnownState);
    }

    /**
     * Creates a new MemberStatus with updated connectivity state, preserving all other fields.
     */
    public MemberStatus withConnectivityState(ConnectivityState connectivityState) {
        return new MemberStatus(this.state, this.lastJoinEventId, this.retryCount, this.lastKeepAliveTimestamp, connectivityState);
    }
}
