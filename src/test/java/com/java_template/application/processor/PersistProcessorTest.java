package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.pet.version_1.Pet;
import com.java_template.application.entity.petimportjob.version_1.PetImportJob;
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

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PersistProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Start a simple HTTP server to serve the pets JSON (avoid external network)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String petsJson = objectMapper.writeValueAsString(List.of(
                java.util.Map.of(
                        "id", "pet-1",
                        "name", "Fido",
                        "age", 3,
                        "status", "available",
                        "source", "Test"
                ),
                java.util.Map.of(
                        "id", "pet-2",
                        "name", "Whiskers",
                        "age", 2,
                        "status", "adopted",
                        "source", "Test"
                )
        ));
        server.createContext("/pets", exchange -> {
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

        // Stub addItems to return two UUIDs (simulate persisted pets)
        when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID(), UUID.randomUUID())));

        // Build processor
        PersistProcessor processor = new PersistProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid PetImportJob payload (must pass isValid)
        PetImportJob job = new PetImportJob();
        job.setRequestId("r1");
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setSourceUrl(sourceUrl);
        job.setStatus("PENDING");
        job.setImportedCount(0);
        job.setErrors(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
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
            assertNotNull(response, "Response should not be null");
            assertTrue(response.getSuccess(), "Response should indicate success");

            assertNotNull(response.getPayload(), "Response payload must be present");
            JsonNode responseData = response.getPayload().getData();
            assertNotNull(responseData, "Response data must be present");

            // Verify expected sunny-day changes: importedCount == 2, status == "COMPLETED", errors null or missing
            assertEquals(2, responseData.path("importedCount").asInt(), "importedCount should be number of persisted pets");
            assertEquals("COMPLETED", responseData.path("status").asText(), "status should be COMPLETED on success");
            assertTrue(responseData.path("errors").isMissingNode() || responseData.path("errors").isNull(),
                    "errors should be null or missing on success");

            // Verify EntityService.addItems was called
            verify(entityService, atLeastOnce()).addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), any());
        } finally {
            server.stop(0);
        }
    }
}