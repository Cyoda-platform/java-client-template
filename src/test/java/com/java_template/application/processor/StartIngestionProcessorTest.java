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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class StartIngestionProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - setup serializers
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService and stub addItems to simulate successful persistence
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItems(anyString(), anyInt(), anyList()))
                .thenReturn(CompletableFuture.completedFuture(List.of(UUID.randomUUID())));

        // Start an embedded HTTP server to serve a minimal JSON with one laureate record
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = """
            {
              "records": [
                {
                  "fields": {
                    "id": 1,
                    "firstname": "Ada",
                    "surname": "Lovelace",
                    "category": "Physics",
                    "year": "1930"
                  }
                }
              ]
            }
            """;
        server.createContext("/data", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] bytes = responseBody.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String sourceUrl = "http://localhost:" + port + "/data";

            // Instantiate real processor (will use embedded HTTP server)
            StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

            // Build a valid Job payload so entity.isValid() passes
            JsonNode jobNode = objectMapper.createObjectNode()
                    .put("jobId", "job-1")
                    .put("sourceUrl", sourceUrl)
                    .put("schedule", "ON_DEMAND")
                    .put("notifyOn", "BOTH")
                    .put("status", "NOT_STARTED");

            DataPayload payload = new DataPayload();
            payload.setData(jobNode);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("StartIngestionProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
                @Override
                public EntityProcessorCalculationRequest getEvent() { return request; }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(context);

            // Assert basic response and success
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect returned payload for expected sunny-day changes
            assertNotNull(response.getPayload());
            JsonNode out = response.getPayload().getData();
            assertNotNull(out);

            // Expect status changed to SUCCEEDED and ingestResult.countAdded == 1
            assertEquals("SUCCEEDED", out.path("status").asText());
            assertEquals(1, out.path("ingestResult").path("countAdded").asInt());

            // Verify entityService.addItems was invoked (at least once)
            verify(entityService, atLeastOnce()).addItems(anyString(), anyInt(), anyList());

        } finally {
            server.stop(0);
        }
    }
}