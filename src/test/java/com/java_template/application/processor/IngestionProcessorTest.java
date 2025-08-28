package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.job.version_1.Job;
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

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class IngestionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService and stub addItem to return completed futures
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenAnswer(invocation -> CompletableFuture.completedFuture(UUID.randomUUID().toString()));

        // Start a simple HTTP server to serve a JSON array of records (sunny-path)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = objectMapper.writeValueAsString(
                List.of(
                        // a single record that's directly the fields node
                        objectMapper.createObjectNode()
                                .put("id", 1)
                                .put("firstname", "Albert")
                                .put("surname", "Einstein")
                                .put("category", "physics")
                                .put("year", "1921")
                )
        );
        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                byte[] bytes = responseBody.getBytes();
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String sourceUrl = "http://localhost:" + port + "/";

        // Instantiate processor under test
        IngestionProcessor processor = new IngestionProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job JSON payload to satisfy Job.isValid()
        JsonNode jobJson = objectMapper.createObjectNode()
                .put("jobId", "job-1")
                .put("sourceUrl", sourceUrl)
                .put("scheduleAt", "2025-01-01T00:00:00Z")
                .put("state", "PENDING")
                .put("failedCount", 0)
                .put("succeededCount", 0)
                .put("totalRecords", 0);

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

        try {
            // Act
            EntityProcessorCalculationResponse response = processor.process(context);

            // Assert
            assertNotNull(response);
            assertTrue(response.getSuccess());

            JsonNode out = response.getPayload().getData();
            assertNotNull(out);
            // After sunny-path ingestion, processor sets state to POSTPROCESSING
            assertEquals("POSTPROCESSING", out.get("state").asText());
            // One record processed
            assertEquals(1, out.get("totalRecords").asInt());
            assertEquals(1, out.get("succeededCount").asInt());
            assertEquals(0, out.get("failedCount").asInt());
            assertNotNull(out.get("startedAt"));
            assertNotNull(out.get("finishedAt"));

            // Verify entityService.addItem was invoked at least once for Laureate persistence
            verify(entityService, atLeastOnce()).addItem(eq("Laureate"), eq(1), any());
        } finally {
            server.stop(0);
        }
    }
}