package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.salesrecord.version_1.SalesRecord;
import com.java_template.application.entity.weeklyreport.version_1.WeeklyReport;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PersistAnalysisProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // ObjectMapper + serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked
        EntityService entityService = mock(EntityService.class);
        when(entityService.addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Instantiate processor directly (no Spring)
        PersistAnalysisProcessor processor = new PersistAnalysisProcessor(serializerFactory, entityService, objectMapper);

        // Prepare a valid SalesRecord that passes isValid()
        SalesRecord salesRecord = new SalesRecord();
        salesRecord.setRecordId("rec-123");
        salesRecord.setDateSold(OffsetDateTime.now().toString()); // parseable by OffsetDateTime.parse
        salesRecord.setProductId("prod-1");
        salesRecord.setQuantity(5);
        salesRecord.setRevenue(25.0);
        salesRecord.setRawSource("origSource");

        JsonNode entityJson = objectMapper.valueToTree(salesRecord);

        // Build request with payload
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PersistAnalysisProcessor");
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

        // Assert basic success
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // Inspect returned payload data and ensure analyzer appended analysisTag to rawSource
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertTrue(returnedData.has("rawSource"));
        String rawSourceValue = returnedData.get("rawSource").asText();
        assertTrue(rawSourceValue.contains("origSource"));
        assertTrue(rawSourceValue.contains("analysisTag:"));

        // Verify that EntityService.addItem was called to persist WeeklyReport
        verify(entityService, atLeastOnce()).addItem(eq(WeeklyReport.ENTITY_NAME), eq(WeeklyReport.ENTITY_VERSION), any());
    }
}