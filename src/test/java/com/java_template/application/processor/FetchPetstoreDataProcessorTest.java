package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.pet.version_1.Pet;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class FetchPetstoreDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real Jackson)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Start a simple local HTTP server to serve petstore JSON (so HttpClient in processor can fetch it)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        try {
            String petsJson = "[{\"id\":\"pet-1\",\"name\":\"Fido\",\"species\":\"dog\",\"status\":\"AVAILABLE\"}]";
            server.createContext("/pets", (HttpExchange exchange) -> {
                byte[] resp = petsJson.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            });
            server.start();
            int port = server.getAddress().getPort();
            String sourceUrl = "http://localhost:" + port + "/pets";

            // Mock only EntityService
            EntityService entityService = mock(EntityService.class);
            // Stub addItems to return one UUID to indicate one created pet
            when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

            // Create processor (no Spring)
            FetchPetstoreDataProcessor processor = new FetchPetstoreDataProcessor(serializerFactory, entityService, objectMapper);

            // Build a valid IngestionJob payload (must pass IngestionJob.isValid())
            IngestionJob job = new IngestionJob();
            job.setRequestedBy("requester-1");
            job.setSourceUrl(sourceUrl);
            job.setStartedAt(Instant.now().toString());
            job.setStatus("PENDING");

            // Convert to JsonNode and wrap in DataPayload
            com.fasterxml.jackson.databind.JsonNode jobJson = objectMapper.valueToTree(job);
            DataPayload payload = new DataPayload();
            // Use reflection-safe setter if available; typical generated class has setData
            payload.setData(jobJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("FetchPetstoreDataProcessor");
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
            assertNotNull(response, "Response should not be null");
            assertTrue(response.getSuccess(), "Response.success should be true");

            // Inspect returned payload -> should contain updated IngestionJob with summary and COMPLETED status
            assertNotNull(response.getPayload(), "Response payload should not be null");
            com.fasterxml.jackson.databind.JsonNode responseData = response.getPayload().getData();
            assertNotNull(responseData, "Response payload.data should not be null");

            String status = responseData.has("status") ? responseData.get("status").asText() : null;
            assertEquals("COMPLETED", status, "IngestionJob should be marked COMPLETED on sunny path");

            com.fasterxml.jackson.databind.JsonNode summaryNode = responseData.get("summary");
            assertNotNull(summaryNode, "Summary should be present");
            int created = summaryNode.has("created") ? summaryNode.get("created").asInt(-1) : -1;
            assertEquals(1, created, "One pet should have been created");

            // Verify entityService.addItems was invoked
            verify(entityService, atLeastOnce()).addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection());
        } finally {
            server.stop(0);
        }
    }
}