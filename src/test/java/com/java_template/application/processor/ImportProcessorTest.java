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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class ImportProcessorTest {

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
        // Simulate persistence returning one UUID
        when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

        // Start a simple HTTP server to serve pet data (avoid external network)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String petsJson = "[{\"id\":\"pet-1\",\"name\":\"Fluffy\",\"age\":2,\"status\":\"AVAILABLE\"}]";
        server.createContext("/pets", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] resp = petsJson.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String sourceUrl = "http://127.0.0.1:" + port + "/pets";

        // Instantiate processor (real, no Spring)
        ImportProcessor processor = new ImportProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid PetImportJob that passes isValid()
        PetImportJob job = new PetImportJob();
        job.setRequestId("req-1");
        job.setRequestedAt("2025-01-01T00:00:00Z");
        job.setSourceUrl(sourceUrl);
        job.setStatus("PENDING");
        job.setImportedCount(0);
        job.setErrors("");

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("ImportProcessor");
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
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect returned payload for sunny-day state changes: status COMPLETED and importedCount == 1
            assertNotNull(response.getPayload());
            JsonNode returnedData = response.getPayload().getData();
            assertNotNull(returnedData);
            // Check status
            assertEquals("COMPLETED", returnedData.get("status").asText());
            // Check importedCount
            assertEquals(1, returnedData.get("importedCount").asInt());
        } finally {
            server.stop(0);
        }

        // Verify EntityService.addItems was called
        verify(entityService, atLeastOnce()).addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection());
    }
}