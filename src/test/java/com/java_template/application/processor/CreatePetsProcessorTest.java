package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
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
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class CreatePetsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Setup real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        // Return a single UUID for created pet
        UUID createdId = UUID.randomUUID();
        when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection()))
                .thenReturn(CompletableFuture.completedFuture(List.of(createdId)));

        // Start a simple HTTP server to serve pet JSON payload
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String petsJson = "[{\"petId\":\"p1\",\"name\":\"Fluffy\",\"species\":\"cat\",\"photoUrls\":[],\"tags\":[]}]";
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

        // Instantiate processor (no Spring)
        CreatePetsProcessor processor = new CreatePetsProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid PetImportJob entity and wrap in request payload
        PetImportJob job = new PetImportJob();
        job.setJobId("job-1");
        job.setSourceUrl(sourceUrl);
        job.setRequestedAt(Instant.now().toString());
        job.setStatus("PENDING");
        job.setFetchedCount(0);
        job.setCreatedCount(0);
        job.setError(null);

        JsonNode jobJson = objectMapper.valueToTree(job);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreatePetsProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(jobJson);
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
            @Override
            public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
            @Override
            public EntityProcessorCalculationRequest getEvent() { return request; }
        };

        // Act
        EntityProcessorCalculationResponse response = processor.process(context);

        // Stop server
        server.stop(0);

        // Assert basic success and expected job updates
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should succeed for sunny day");

        assertNotNull(response.getPayload(), "Response should include a payload");
        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData, "Response payload data should not be null");

        assertEquals("COMPLETED", respData.get("status").asText(), "Job status should be COMPLETED");
        assertEquals(1, respData.get("fetchedCount").asInt(), "Fetched count should reflect one pet fetched");
        assertEquals(1, respData.get("createdCount").asInt(), "Created count should reflect one pet persisted");
        // error can be null or missing; if present it should be null or blank
        if (respData.has("error") && !respData.get("error").isNull()) {
            assertTrue(respData.get("error").asText().isBlank());
        }

        // Verify EntityService.addItems was invoked to persist pets
        verify(entityService, atLeastOnce()).addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection());
    }
}