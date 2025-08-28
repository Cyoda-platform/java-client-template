package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class AnalyzeDataProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock EntityService (only mocked dependency allowed)
        EntityService entityService = mock(EntityService.class);
        // Assume addItem is void; ensure it does nothing if called
        try {
            doNothing().when(entityService).addItem(anyString(), any(), any());
        } catch (Exception ignored) {
            // Some implementations might return non-void; ignore if stubbing fails
        }

        AnalyzeDataProcessor processor = new AnalyzeDataProcessor(serializerFactory, entityService, objectMapper);

        // Start a simple HTTP server to serve CSV content
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String csv = "price,bedrooms,area\n100.0,2,North\n200.0,3,South\n";
        server.createContext("/sample.csv", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    byte[] resp = csv.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "text/csv");
                    exchange.sendResponseHeaders(200, resp.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(resp);
                    }
                } catch (Exception e) {
                    try {
                        exchange.sendResponseHeaders(500, -1);
                    } catch (Exception ex) { /* ignore */ }
                } finally {
                    exchange.close();
                }
            }
        });
        server.start();
        int port = server.getAddress().getPort();
        String url = "http://127.0.0.1:" + port + "/sample.csv";

        // Build a valid ReportJob entity (must pass isValid)
        ReportJob job = new ReportJob();
        job.setJobId("job-" + UUID.randomUUID());
        job.setDataSourceUrl(url);
        job.setGeneratedAt(Instant.now().toString()); // required by isValid
        job.setStatus("NEW"); // required by isValid
        job.setTriggerType("MANUAL"); // required by isValid
        job.setRequestedMetrics("avg_price,median_price,price_by_bedrooms,distribution_by_area");

        JsonNode entityJson = objectMapper.valueToTree(job);
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(job.getJobId());
        request.setProcessorName("AnalyzeDataProcessor");
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
            assertTrue(response.getSuccess(), "Processor should report success on sunny day");

            assertNotNull(response.getPayload());
            JsonNode out = (JsonNode) response.getPayload().getData();
            assertNotNull(out);

            // Verify core sunny-day changes: reportLocation set and status should be ANALYZING
            assertTrue(out.has("reportLocation"), "reportLocation should be set");
            String reportLocation = out.get("reportLocation").asText();
            assertNotNull(reportLocation);
            assertTrue(reportLocation.startsWith("analysis://"), "reportLocation should start with analysis://");

            assertTrue(out.has("status"), "status should be present");
            assertEquals("ANALYZING", out.get("status").asText(), "status should be ANALYZING");

            // generatedAt should be present and non-blank (updated during processing)
            assertTrue(out.has("generatedAt"));
            assertFalse(out.get("generatedAt").asText().isBlank());

            // Verify that EntityService.addItem was called to persist DataSource (at least once)
            verify(entityService, atLeastOnce()).addItem(eq(com.java_template.application.entity.datasource.version_1.DataSource.ENTITY_NAME), any(), any());
        } finally {
            server.stop(0);
        }
    }
}