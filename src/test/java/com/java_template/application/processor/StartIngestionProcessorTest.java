package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StartIngestionProcessorTest {

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

        // Start a simple HTTP server to serve laureate payloads
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            // Prepare a single valid laureate in the response array
            ObjectNode laureateFields = objectMapper.createObjectNode();
            laureateFields.put("id", 1);
            laureateFields.put("firstname", "Ada");
            laureateFields.put("surname", "Lovelace");
            laureateFields.put("category", "Physics");
            laureateFields.put("year", "2020");

            // The records array where each item may be the fields object directly
            JsonNode responseBody = objectMapper.createArrayNode().add(laureateFields);

            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    try {
                        byte[] bytes = objectMapper.writeValueAsBytes(responseBody);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    } catch (Exception ex) {
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
            String sourceUrl = "http://localhost:" + port + "/";

            EntityService entityService = mock(EntityService.class);
            // Stub addItems to return a single UUID for the one laureate
            when(entityService.addItems(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), anyList()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

            StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

            // Build a valid Job payload so validation passes
            ObjectNode jobNode = objectMapper.createObjectNode();
            jobNode.put("id", "job-1");
            jobNode.put("runTimestamp", Instant.now().toString());
            jobNode.put("sourceUrl", sourceUrl);
            jobNode.put("state", "NEW");

            DataPayload payload = new DataPayload();
            payload.setData(jobNode);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("StartIngestionProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
                @Override
                public EntityProcessorCalculationRequest getEvent() { return request; }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(ctx);

            // Assert
            assertNotNull(response);
            assertTrue(response.getSuccess());

            JsonNode out = response.getPayload().getData();
            assertNotNull(out);
            // The processor sets state to "SUCCEEDED" on happy path
            assertEquals("SUCCEEDED", out.get("state").asText());
            // Summary should reflect one ingested, zero failed and empty errors
            assertNotNull(out.get("summary"));
            JsonNode summary = out.get("summary");
            assertEquals(1, summary.get("ingestedCount").asInt());
            assertEquals(0, summary.get("failedCount").asInt());
            assertTrue(summary.get("errors").isArray());
            assertEquals(0, summary.get("errors").size());

            // Verify entityService was used to persist laureates
            verify(entityService, atLeastOnce()).addItems(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), anyList());
        } finally {
            server.stop(0);
        }
    }
}