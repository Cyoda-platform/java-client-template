package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
import com.java_template.application.entity.laureate.version_1.Laureate;
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
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class IngestionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Start a simple HTTP server to serve a minimal laureate payload
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/data", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    // Return an array of records with fields containing laureate data
                    String body = """
                        {
                          "records": [
                            {
                              "fields": {
                                "id": 1,
                                "firstname": "Ada",
                                "surname": "Lovelace",
                                "category": "Physics",
                                "year": "2000"
                              }
                            }
                          ]
                        }
                        """;
                    byte[] bytes = body.getBytes();
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    try { exchange.sendResponseHeaders(500, -1); } catch (Exception ex) {}
                } finally {
                    exchange.close();
                }
            }
        });
        server.setExecutor(java.util.concurrent.Executors.newSingleThreadExecutor());
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/data";

        try {
            // Mock EntityService - only allowed mocked dependency
            EntityService entityService = mock(EntityService.class);
            when(entityService.addItems(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

            // Create processor under test
            IngestionProcessor processor = new IngestionProcessor(serializerFactory, entityService, objectMapper);

            // Build a valid Job entity (must pass Job.isValid())
            Job job = new Job();
            job.setId("job-1");
            job.setSourceUrl(url);
            job.setSchedule("*/5 * * * *");
            job.setState("SCHEDULED");
            job.setProcessedCount(0);
            job.setFailedCount(0);

            // Build request payload using real Jackson serializer
            JsonNode jobJson = objectMapper.valueToTree(job);
            DataPayload payload = new DataPayload();
            payload.setData(jobJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("IngestionProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
                @Override
                public EntityProcessorCalculationRequest getEvent() { return request; }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(context);

            // Assert
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect returned payload - ensure job was updated to SUCCEEDED and processedCount increased
            assertNotNull(response.getPayload());
            JsonNode out = response.getPayload().getData();
            assertNotNull(out);
            // state should be SUCCEEDED for successful ingestion
            assertEquals("SUCCEEDED", out.get("state").asText());
            // processedCount should be >= 1
            assertTrue(out.get("processedCount").asInt() >= 1);
            // failedCount should be 0
            assertEquals(0, out.get("failedCount").asInt());

            // Verify that addItems was called to persist laureates
            verify(entityService, atLeastOnce()).addItems(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), anyList());
        } finally {
            server.stop(0);
        }
    }
}