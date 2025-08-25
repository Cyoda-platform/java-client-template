package com.java_template.common.grpc.client;

import com.java_template.common.grpc.client.event_handling.AbstractEventStrategy;
import io.cloudevents.v1.proto.CloudEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * ABOUTME: Tests for AbstractEventStrategy, focusing on the recoverRequestIdFromCloudEvent method
 * which handles string-based parsing of potentially corrupted JSON to extract request IDs.
 */
@ExtendWith(MockitoExtension.class)
class AbstractEventStrategyTest {


    @Mock
    private CloudEvent cloudEvent;

    @Test
    void testRecoverRequestIdFromCloudEvent_ValidJsonWithQuotes() {
        // Given
        String jsonData = "{\"requestId\": \"12345678-1234-1234-1234-123456789abc\", \"entityId\": \"entity123\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("12345678-1234-1234-1234-123456789abc", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_ValidJsonNoSpaces() {
        // Given
        String jsonData = "{\"requestId\":\"test-request-id-456\",\"entityId\":\"entity123\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("test-request-id-456", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_SingleQuotes() {
        // Given
        String jsonData = "{'requestId': 'single-quote-id-789', 'entityId': 'entity123'}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("single-quote-id-789", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_MixedQuotes() {
        // Given
        String jsonData = "{\"requestId\": 'mixed-quote-id-101', \"entityId\": \"entity123\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("mixed-quote-id-101", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_MultilineJson() {
        // Given
        String jsonData = "{\n  \"requestId\": \"multiline-id-202\",\n  \"entityId\": \"entity123\"\n}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("multiline-id-202", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_UuidFallbackPattern() {
        // Given - corrupted JSON where quotes are missing but UUID is intact
        String jsonData = "{requestId: 12345678-1234-1234-1234-123456789abc, entityId: entity123}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("12345678-1234-1234-1234-123456789abc", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_GeneralPatternFallback() {
        // Given - corrupted JSON with non-UUID requestId
        String jsonData = "{requestId: simple-request-id-303, entityId: entity123}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("simple-request-id-303", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_CaseInsensitive() {
        // Given
        String jsonData = "{\"REQUESTID\": \"case-insensitive-id-404\", \"entityId\": \"entity123\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("case-insensitive-id-404", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_NullCloudEvent() {
        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(null);

        // Then
        assertFalse(result.requestId().isPresent());
        assertEquals("CloudEvent is null, cannot recover requestId", result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_EmptyTextData() {
        // Given
        when(cloudEvent.getTextData()).thenReturn("");

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertFalse(result.requestId().isPresent());
        assertEquals("CloudEvent text data is empty, cannot recover requestId", result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_NoRequestIdField() {
        // Given
        String jsonData = "{\"entityId\": \"entity123\", \"processorName\": \"test-processor\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertFalse(result.requestId().isPresent());
        assertEquals("Could not recover requestId from CloudEvent text data. No matching patterns found.", result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_CorruptedJsonWithPartialRequestId() {
        // Given - heavily corrupted JSON but requestId is still extractable
        String jsonData = "{\"req corrupted but requestId: \"recoverable-id-505\" still here}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("recoverable-id-505", result.requestId().get());
        assertNull(result.error());
    }

    @Test
    void testRecoverRequestIdFromCloudEvent_RequestIdWithSpecialCharacters() {
        // Given
        String jsonData = "{\"requestId\": \"req-id_with.special@chars#606\", \"entityId\": \"entity123\"}";
        when(cloudEvent.getTextData()).thenReturn(jsonData);

        // When
        AbstractEventStrategy.RequestIdRecoveryResult result = AbstractEventStrategy.recoverRequestIdFromCloudEvent(cloudEvent);

        // Then
        assertTrue(result.requestId().isPresent());
        assertEquals("req-id_with.special@chars#606", result.requestId().get());
        assertNull(result.error());
    }
}
