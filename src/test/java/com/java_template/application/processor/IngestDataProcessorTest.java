package com.java_template.application.processor;

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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;

public class IngestDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Start a simple HTTP server to serve a minimal laureate JSON payload
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String laureateBody = "[{\"id\":\"L1\",\"firstname\":\"Ada\",\"surname\":\"Lovelace\",\"category\":\"physics\",\"year\":\"2020\"}]";
        server.createContext("/", exchange -> {
            byte[] resp = laureateBody.getBytes();
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String sourceUrl = "http://127.0.0.1:" + port + "/";

        // Instantiate processor (no Spring)
        IngestDataProcessor processor = new IngestDataProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Job entity and put it in the request payload
        Job job = new Job();
        job.setJobId("job-1");
        job.setScheduledAt(OffsetDateTime.now().toString());
        job.setSourceUrl(sourceUrl);
        job.setStatus("PENDING");

        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(job));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("IngestDataProcessor");
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

            // Inspect returned payload data - should contain updated Job state
            assertNotNull(response.getPayload());
            assertNotNull(response.getPayload().getData());
            // Convert payload data back to Job to assert status and summary
            Job returned = objectMapper.treeToValue(response.getPayload().getData(), Job.class);
            assertNotNull(returned.getStartedAt(), "startedAt should be set by processor");
            assertNotNull(returned.getFinishedAt(), "finishedAt should be set by processor");
            assertEquals("SUCCEEDED", returned.getStatus(), "Job should have succeeded in sunny path");
            assertTrue(returned.getSummary().contains("ingested"), "Summary should indicate ingestion");
            // Verify entityService.addItem was invoked for laureate persistence
            verify(entityService, atLeastOnce()).addItem(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), any());
        } finally {
            server.stop(0);
        }
    }
}