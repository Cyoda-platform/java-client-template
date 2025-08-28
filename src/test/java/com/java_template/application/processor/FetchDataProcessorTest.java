package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.datasource.version_1.DataSource;
import com.java_template.application.entity.reportjob.version_1.ReportJob;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

public class FetchDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService and stub addItem to return a completed UUID
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(DataSource.ENTITY_NAME), eq(DataSource.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Start a simple HTTP server to serve CSV content (avoids external network)
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String csvBody = "colA,colB\n1,2\n3,4";
        server.createContext("/data", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] resp = csvBody.getBytes();
                exchange.getResponseHeaders().add("Content-Type", "text/csv");
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String url = "http://localhost:" + port + "/data";

        try {
            // Instantiate processor under test
            FetchDataProcessor processor = new FetchDataProcessor(serializerFactory, entityService, objectMapper);

            // Create a valid ReportJob entity (must satisfy isValid())
            ReportJob job = new ReportJob();
            job.setJobId("job-123");
            job.setDataSourceUrl(url);
            job.setGeneratedAt(Instant.now().toString());
            job.setStatus("PENDING");
            job.setTriggerType("MANUAL");

            // Convert entity to JsonNode payload
            JsonNode entityJson = objectMapper.valueToTree(job);
            DataPayload payload = new DataPayload();
            payload.setData(entityJson);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("FetchDataProcessor");
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
            assertNotNull(response);
            assertTrue(response.getSuccess());

            // Inspect payload - expect status to be advanced to VALIDATING on sunny path
            assertNotNull(response.getPayload());
            JsonNode out = response.getPayload().getData();
            assertNotNull(out);
            assertEquals("VALIDATING", out.get("status").asText());

            // Verify entityService was invoked to persist DataSource
            verify(entityService, atLeastOnce()).addItem(eq(DataSource.ENTITY_NAME), eq(DataSource.ENTITY_VERSION), any());
        } finally {
            server.stop(0);
        }
    }
}