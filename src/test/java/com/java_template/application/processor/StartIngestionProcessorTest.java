package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class StartIngestionProcessorTest {

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

        // Mock EntityService and stub addItem to return a successful UUID future
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Start a lightweight HTTP server to serve a minimal JSON payload with one record
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        String responseBody = objectMapper.writeValueAsString(
                objectMapper.createObjectNode()
                        .set("records", objectMapper.createArrayNode()
                                .add(objectMapper.createObjectNode()
                                        .put("id", "L1")
                                        .put("year", "2020")
                                        .put("category", "physics")
                                        .put("firstname", "Albert")
                                        .put("surname", "Einstein")
                                )
                        )
        );
        server.createContext("/data", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                try {
                    byte[] bytes = responseBody.getBytes();
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } catch (Exception ignored) {
                } finally {
                    exchange.close();
                }
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            String sourceUrl = "http://localhost:" + port + "/data";

            StartIngestionProcessor processor = new StartIngestionProcessor(serializerFactory, entityService, objectMapper);

            // Build Job payload minimal fields to satisfy validation (id and source)
            ObjectNode jobNode = objectMapper.createObjectNode();
            jobNode.put("id", "job-1");
            jobNode.put("source", sourceUrl);

            DataPayload payload = new DataPayload();
            payload.setData(jobNode);

            EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
            request.setId("r1");
            request.setRequestId("r1");
            request.setEntityId("e1");
            request.setProcessorName("StartIngestionProcessor");
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

            // Assert
            assertNotNull(response);
            assertTrue(response.getSuccess());

            JsonNode out = response.getPayload().getData();
            assertNotNull(out);
            // Expect one ingested record and status SUCCEEDED on sunny path
            assertEquals("SUCCEEDED", out.get("status").asText());
            assertNotNull(out.get("resultSummary"));
            assertEquals(1, out.get("resultSummary").get("ingestedCount").asInt());
            assertEquals(0, out.get("resultSummary").get("errorCount").asInt());

            // Verify EntityService.addItem was invoked at least once for laureate ingestion
            verify(entityService, atLeastOnce()).addItem(eq("Laureate"), anyString(), any());
        } finally {
            server.stop(0);
        }
    }
}