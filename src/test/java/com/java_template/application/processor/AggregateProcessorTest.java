package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class AggregateProcessorTest {

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

        // Stub getItems to return an empty list (no existing sales -> still generates weekly report)
        when(entityService.getItems(eq(SalesRecord.ENTITY_NAME), eq(SalesRecord.ENTITY_VERSION), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(new ArrayList<>()));

        // Stub addItem for WeeklyReport persistence
        when(entityService.addItem(anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor (no Spring)
        AggregateProcessor processor = new AggregateProcessor(serializerFactory, entityService, objectMapper);

        // Create a valid SalesRecord that passes isValid()
        SalesRecord salesRecord = new SalesRecord();
        salesRecord.setRecordId("rec-123");
        salesRecord.setProductId("prod-1");
        salesRecord.setQuantity(10);
        salesRecord.setRevenue(100.0);
        salesRecord.setRawSource("{\"source\":\"unit-test\"}");
        // use a parseable ISO instant
        salesRecord.setDateSold(Instant.now().toString());

        // Build request with payload
        JsonNode entityJson = objectMapper.valueToTree(salesRecord);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AggregateProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(entityJson);
        request.setPayload(payload);

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

        // Inspect response payload contains the original SalesRecord fields
        assertNotNull(response.getPayload());
        assertNotNull(response.getPayload().getData());
        JsonNode respData = response.getPayload().getData();
        assertEquals(salesRecord.getRecordId(), respData.get("recordId").asText());
        assertEquals(salesRecord.getProductId(), respData.get("productId").asText());
        assertEquals(salesRecord.getQuantity().intValue(), respData.get("quantity").asInt());
        assertEquals(salesRecord.getRevenue().doubleValue(), respData.get("revenue").asDouble(), 0.0001);

        // Verify that aggregation attempted to fetch SalesRecord items and persisted a WeeklyReport
        verify(entityService, atLeastOnce()).getItems(eq(SalesRecord.ENTITY_NAME), eq(SalesRecord.ENTITY_VERSION), any(), any(), any());
        verify(entityService, atLeastOnce()).addItem(eq("WeeklyReport"), eq(1), any());
    }
}