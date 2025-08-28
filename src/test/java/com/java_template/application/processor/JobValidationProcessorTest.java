package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

public class JobValidationProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService only (required by constructor)
        EntityService entityService = mock(EntityService.class);

        // Start a lightweight local HTTP server to simulate reachable sourceUrl (returns 200)
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        httpServer.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] resp = new byte[0];
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
        httpServer.setExecutor(null);
        httpServer.start();
        int port = httpServer.getAddress().getPort();
        String localUrl = "http://127.0.0.1:" + port + "/";

        try {
            // Construct processor with real serializerFactory (constructor expects only SerializerFactory)
            JobValidationProcessor processor = new JobValidationProcessor(serializerFactory);

            // Build a valid PetImportJob entity that passes isValid()
            PetImportJob job = new PetImportJob();
            job.setJobId("job-1");
            job.setSourceUrl(localUrl);
            job.setRequestedAt("2023-01-01T00:00:00Z");
            job.setStatus("NEW");
            job.setFetchedCount(0);
            job.setCreatedCount(0);
            job.setError(null);

            // Convert to JsonNode payload
            JsonNode entityJson = objectMapper.valueToTree(job);

            // Build request
            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("JobValidationProcessor");
            DataPayload payload = new DataPayload();
            payload.setData(entityJson);
            request.setPayload(payload);

            // Minimal CyodaEventContext
            CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
                @Override
                public io.cloudevents.v1.proto.CloudEvent getCloudEvent() { return null; }
                @Override
                public EntityProcessorCalculationRequest getEvent() { return request; }
            };

            // Act
            EntityProcessorCalculationResponse response = processor.process(context);

            // Assert basic response
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect returned payload data for expected sunny-day state (status -> FETCHING, counts non-null)
            assertNotNull(response.getPayload());
            assertNotNull(response.getPayload().getData());
            JsonNode returned = response.getPayload().getData();
            assertEquals("FETCHING", returned.get("status").asText());
            // fetchedCount / createdCount should be present and >= 0
            assertTrue(returned.has("fetchedCount"));
            assertTrue(returned.has("createdCount"));
            assertEquals(0, returned.get("fetchedCount").asInt());
            assertEquals(0, returned.get("createdCount").asInt());
            // error should be null (or missing)
            if (returned.has("error")) {
                assertTrue(returned.get("error").isNull());
            }
        } finally {
            httpServer.stop(0);
        }
    }
}