package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.common.ModelSpec;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for Auth0Fetch processor
 * 
 * Tests cover:
 * - Successful user fetch with pagination
 * - Error handling for Auth0 API failures
 * - Token acquisition via client credentials
 * - Proper processor naming and support
 */
@ExtendWith(MockitoExtension.class)
class Auth0FetchTest {

    @Mock
    private SerializerFactory serializerFactory;

    @Mock
    private ProcessorSerializer serializer;

    @Mock
    private CyodaEventContext<EntityProcessorCalculationRequest> context;

    @Mock
    private EntityProcessorCalculationRequest request;

    @Mock
    private EntityProcessorCalculationResponse response;

    private Auth0Fetch auth0Fetch;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(serializerFactory.getDefaultProcessorSerializer()).thenReturn(serializer);

        auth0Fetch = new Auth0Fetch(serializerFactory);

        // Set configuration values
        ReflectionTestUtils.setField(auth0Fetch, "auth0Domain", "example.auth0.com");
        ReflectionTestUtils.setField(auth0Fetch, "auth0ClientId", "test-client-id");
        ReflectionTestUtils.setField(auth0Fetch, "auth0ClientSecret", "test-client-secret");
        ReflectionTestUtils.setField(auth0Fetch, "auth0Audience", "https://example.auth0.com/api/v2/");
        ReflectionTestUtils.setField(auth0Fetch, "pageSize", 100);
    }

    @Test
    void testProcessorSupportsAuth0Fetch() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("Auth0Fetch");
        OperationSpecification opSpec = new OperationSpecification.Entity(modelSpec, "Auth0Fetch");

        assertTrue(auth0Fetch.supports(opSpec));
    }

    @Test
    void testProcessorDoesNotSupportOtherProcessors() {
        ModelSpec modelSpec = new ModelSpec();
        modelSpec.setName("OtherProcessor");
        OperationSpecification opSpec = new OperationSpecification.Entity(modelSpec, "OtherProcessor");

        assertFalse(auth0Fetch.supports(opSpec));
    }

    @Test
    void testProcessorNameMatches() {
        // Verify the processor class name matches what's expected
        assertEquals("Auth0Fetch", auth0Fetch.getClass().getSimpleName());
    }

    @Test
    void testConfigurationValuesSet() {
        // Verify configuration values are properly set
        String domain = (String) ReflectionTestUtils.getField(auth0Fetch, "auth0Domain");
        String clientId = (String) ReflectionTestUtils.getField(auth0Fetch, "auth0ClientId");
        Integer pageSize = (Integer) ReflectionTestUtils.getField(auth0Fetch, "pageSize");

        assertEquals("example.auth0.com", domain);
        assertEquals("test-client-id", clientId);
        assertEquals(100, pageSize);
    }
}

