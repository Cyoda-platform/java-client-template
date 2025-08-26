package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.JacksonCriterionSerializer;
import com.java_template.common.serializer.JacksonProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.processing.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;

/**
 * Sunny-day unit test for StartIngestionProcessor.
 * - Uses real Jackson serializers and SerializerFactory
 * - Mocks only EntityService
 * - Starts a lightweight local HttpServer to serve expected API JSON
 */
public class StartIngestionProcessorTest {

    @Test
    public void testStartIngestionProcessorSunnyDay() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(List.of(processorSerializer), List.of(criterionSerializer));

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), anyString(), any()))
            .thenAnswer(invocation -> CompletableFuture.completedFuture(UUID.randomUUID()));

        StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

        // Start a simple HTTP server to simulate API endpoint returning records array
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = "{\"records\":[{\"id\":1,\"firstname\":\"Marie\",\"surname\":\"Curie\"}]}";
        byte[] responseBytes = responseBody.getBytes();
        server.createContext("/").setHandler(exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBytes.length);
            exchange.getResponseBody().write(responseBytes);
            exchange.getResponseBody().close();
        });
        server.start();
        int port = server.getAddress().getPort();
        try {
            // Build request payload for Job entity with apiEndpoint pointing to our local server
            ObjectNode jobNode = objectMapper.createObjectNode();
            jobNode.put("id", 123);
            jobNode.put("apiEndpoint", "http://127.0.0.1:" + port + "/");
            // initial attempts null or 0 is acceptable; leave out to let processor handle increment

            DataPayload payload = new DataPayload();
            payload.setData(jobNode);

            EntityProcessorCalculationRequest req = new EntityProcessorCalculationRequest();
            req.setId("test-id");
            req.setRequestId("request-id");
            req.setEntityId("entity-id");
            req.setProcessorName("StartIngestionProcessor");
            req.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
                @Override
                public Object getCloudEvent() {
                    return null;
                }

                @Override
                public EntityProcessorCalculationRequest getEvent() {
                    return req;
                }
            };

            // Act
            EntityProcessorCalculationResponse resp = processor.process(ctx);

            // Assert
            assertNotNull(resp, "Response should not be null");
            assertTrue(resp.getSuccess(), "Processing should be successful");

            assertNotNull(resp.getPayload(), "Payload should be present");
            assertNotNull(resp.getPayload().getData(), "Payload data should be present");

            // Expect the processor to have updated the job state to SUCCEEDED in the sunny path
            String state = resp.getPayload().getData().get("state").asText();
            assertEquals("SUCCEEDED", state, "Job state should be SUCCEEDED on sunny path");

        } finally {
            server.stop(0);
        }
    }
}