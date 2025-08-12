package com.java_template.prototype.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.java_template.common.auth.Authentication;
import com.java_template.common.grpc.client.CyodaCalculationMemberClient;
import com.java_template.common.grpc.client.EventHandlingStrategy;
import org.cyoda.cloud.api.event.common.BaseEvent;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import java.util.Collections;
import java.util.List;

/**
 * Mock configuration for gRPC components in prototype mode.
 * Provides mock beans that simulate gRPC behavior without requiring
 * actual gRPC connections or external services.
 */
@TestConfiguration
@Profile("prototype")
public class MockGrpcConfig {

    /**
     * Creates a mock CyodaCalculationMemberClient that doesn't attempt to connect to gRPC services.
     * This allows prototype mode to work without requiring actual gRPC server connections.
     */
    @Bean
    @Primary
    public CyodaCalculationMemberClient mockCyodaCalculationMemberClient(
            ObjectMapper objectMapper,
            Authentication authentication) {
        
        // Create empty list of event handling strategies for the mock
        List<EventHandlingStrategy<? extends BaseEvent>> mockStrategies = Collections.emptyList();
        
        // Create a mock client that doesn't initialize gRPC connections
        CyodaCalculationMemberClient mockClient = Mockito.mock(CyodaCalculationMemberClient.class);
        
        // Mock the afterPropertiesSet method to do nothing (no gRPC initialization)
        try {
            Mockito.doNothing().when(mockClient).afterPropertiesSet();
        } catch (Exception e) {
            // This shouldn't happen with mocks, but handle it just in case
            throw new RuntimeException("Failed to configure mock CyodaCalculationMemberClient", e);
        }
        
        // Mock the destroy method to do nothing (no cleanup needed)
        try {
            Mockito.doNothing().when(mockClient).destroy();
        } catch (Exception e) {
            // This shouldn't happen with mocks, but handle it just in case
            throw new RuntimeException("Failed to configure mock CyodaCalculationMemberClient destroy", e);
        }
        
        return mockClient;
    }
    
    /**
     * Provides an ObjectMapper bean for the mock gRPC client if not already available.
     * This ensures the mock client has all required dependencies and properly handles Java 8 time types.
     */
    @Bean
    @Profile("prototype")
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}
