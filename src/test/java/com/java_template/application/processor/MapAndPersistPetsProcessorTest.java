package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.ingestionjob.version_1.IngestionJob;
import com.java_template.application.entity.pet.version_1.Pet;
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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MapAndPersistPetsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - set up ObjectMapper and serializers (real implementations)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Start a simple HTTP server to serve a pets JSON payload (avoids external network)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = "[{\"name\":\"Buddy\",\"species\":\"dog\"}]";
        server.createContext("/", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] bytes = responseBody.getBytes();
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
            exchange.close();
        });
        server.start();
        int port = server.getAddress().getPort();
        String sourceUrl = "http://127.0.0.1:" + port + "/";

        try {
            // Mock only EntityService
            EntityService entityService = mock(EntityService.class);
            when(entityService.addItems(eq(Pet.ENTITY_NAME), eq(Pet.ENTITY_VERSION), anyCollection()))
                    .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

            // Instantiate processor with real serializerFactory, mock EntityService and real ObjectMapper
            MapAndPersistPetsProcessor processor =
                    new MapAndPersistPetsProcessor(serializerFactory, entityService, objectMapper);

            // Build a valid IngestionJob payload JSON that passes isValid()
            IngestionJob job = new IngestionJob();
            job.setRequestedBy("requester-1");
            job.setSourceUrl(sourceUrl);
            job.setStartedAt(Instant.now().toString());
            job.setStatus("STARTED");
            // summary can be null; processor will initialize it

            JsonNode jobJson = objectMapper.valueToTree(job);
            DataPayload payload = new DataPayload();
            payload.setData(jobJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("MapAndPersistPetsProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
                @Override
                public EntityProcessorCalculationRequest getEvent() { return request; }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(context);

            // Assert - basic sunny-day expectations
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect payload data for expected completed state and summary changes
            assertNotNull(response.getPayload());
            JsonNode data = response.getPayload().getData();
            assertNotNull(data);
            assertEquals("COMPLETED", data.path("status").asText());
            JsonNode summary = data.path("summary");
            assertTrue(summary.isObject());
            assertEquals(1, summary.path("created").asInt());
            assertEquals(0, summary.path("failed").asInt());
        } finally {
            server.stop(0);
        }
    }
}