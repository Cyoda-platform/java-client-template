package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.petingestionjob.version_1.PetIngestionJob;
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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class FetchPetsProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService as required by constructor
        EntityService entityService = mock(EntityService.class);

        FetchPetsProcessor processor = new FetchPetsProcessor(serializerFactory, entityService, objectMapper);

        // Start a lightweight local HTTP server to simulate a remote pets source (sunny-path)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = "[{\"id\":\"p1\",\"name\":\"Fido\"}]";
        server.createContext("/data", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    byte[] bytes = responseBody.getBytes("UTF-8");
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception e) {
                    try {
                        exchange.sendResponseHeaders(500, -1);
                    } catch (Exception ignored) {}
                } finally {
                    exchange.close();
                }
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String sourceUrl = "http://localhost:" + port + "/data";

            // Build a valid PetIngestionJob entity payload (must pass isValid)
            PetIngestionJob job = new PetIngestionJob();
            job.setJobName("ingest-1");
            job.setSourceUrl(sourceUrl);
            job.setStartedAt(Instant.now().toString());
            job.setStatus("CREATED");
            job.setProcessedCount(0); // must be non-null and >= 0
            // errors list is initialized by default constructor

            JsonNode entityJson = objectMapper.valueToTree(job);

            DataPayload payload = new DataPayload();
            payload.setData(entityJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r-" + UUID.randomUUID());
            request.setRequestId("rq-" + UUID.randomUUID());
            request.setEntityId("e-" + UUID.randomUUID());
            request.setProcessorName("FetchPetsProcessor");
            request.setPayload(payload);

            CyodaEventContext<EntityProcessorCalculationRequest> ctx = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() {
                    return null;
                }

                @Override
                public EntityProcessorCalculationRequest getEvent() {
                    return request;
                }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(ctx);

            // Assert minimal sunny-day expectations
            assertNotNull(response, "Response should not be null");
            assertTrue(response.getSuccess(), "Response should indicate success");

            assertNotNull(response.getPayload(), "Response payload should not be null");
            JsonNode out = response.getPayload().getData();
            assertNotNull(out, "Output data should not be null");

            // processedCount should be updated to 1 (the HTTP server returned an array with one element)
            assertTrue(out.has("processedCount"), "processedCount should be present in output");
            assertEquals(1, out.get("processedCount").asInt(), "processedCount should be 1 after successful fetch");

            // status should remain FETCHING when items were found (as per processor logic)
            assertTrue(out.has("status"), "status should be present in output");
            assertEquals("FETCHING", out.get("status").asText(), "status should be FETCHING when items were fetched");

        } finally {
            server.stop(0);
        }
    }
}