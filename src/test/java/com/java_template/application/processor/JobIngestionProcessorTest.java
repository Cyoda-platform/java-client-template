package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class JobIngestionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Start a simple local HTTP server to simulate the remote source
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            String responseBody = """
                    {
                      "records": [
                        {
                          "fields": {
                            "id": 1,
                            "firstname": "Marie",
                            "surname": "Curie",
                            "year": "1903",
                            "born": "1867-11-07"
                          }
                        }
                      ]
                    }
                    """;
            server.createContext("/data", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try {
                        byte[] bytes = responseBody.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } catch (Exception e) {
                        try {
                            exchange.sendResponseHeaders(500, -1);
                        } catch (Exception ignored) {}
                    } finally {
                        exchange.close();
                    }
                }
            });
            server.start();
            int port = server.getAddress().getPort();
            String sourceUrl = "http://localhost:" + port + "/data";

            // Mock EntityService (only allowed mock)
            EntityService entityService = mock(EntityService.class);
            when(entityService.addItem(anyString(), anyString(), any()))
                    .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

            // Create processor under test
            JobIngestionProcessor processor = new JobIngestionProcessor(serializerFactory, entityService, objectMapper);

            // Build request payload for Job entity - include fields expected by processor
            ObjectNode jobJson = objectMapper.createObjectNode();
            jobJson.put("jobId", "job-1");
            jobJson.put("id", "job-1");
            jobJson.put("sourceEndpoint", sourceUrl);

            DataPayload payload = new DataPayload();
            payload.setData(jobJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("JobIngestionProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                    return null;
                }

                @Override
                public EntityProcessorCalculationRequest getEvent() {
                    return request;
                }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(ctx);

            // Assert
            assertNotNull(response);
            assertTrue(response.getSuccess(), "Processor should indicate success in sunny day");

            // Inspect returned payload data for expected sunny-day state
            assertNotNull(response.getPayload());
            JsonNode out = response.getPayload().getData();
            assertNotNull(out, "Response payload data should not be null");
            assertEquals("SUCCEEDED", out.get("state").asText(), "Job should have SUCCEEDED state");
            assertEquals(1, out.get("recordsFetchedCount").asInt(), "One record should have been fetched/persisted");

            // Verify entityService was used to persist the laureate
            verify(entityService, atLeastOnce()).addItem(eq("laureate"), anyString(), any());
        } finally {
            server.stop(0);
        }
    }
}