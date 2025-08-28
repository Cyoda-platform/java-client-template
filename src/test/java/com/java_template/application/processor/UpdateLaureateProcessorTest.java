package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UpdateLaureateProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);

        // Prepare stored laureate (existing record in DB) with some optional fields populated
        Laureate stored = new Laureate();
        stored.setId(123);
        stored.setFirstname("StoredFirst");
        stored.setSurname("StoredLast");
        stored.setCategory("Physics");
        stored.setYear("1999");
        stored.setAffiliationName("StoredAffiliation");
        stored.setLastUpdatedAt("2025-08-01T12:00:00Z");

        DataPayload existingPayload = new DataPayload();
        existingPayload.setData(objectMapper.valueToTree(stored));
        // Provide a technical id so processor will attempt to call updateItem(...)
        String technicalId = UUID.randomUUID().toString();
        // Note: DataPayload#setId is not available in this environment, so we do not call it here.

        when(entityService.getItemsByCondition(anyString(), any(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(java.util.List.of(existingPayload)));
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString(technicalId)));

        // Instantiate processor (real serializer factory, mocked EntityService)
        UpdateLaureateProcessor processor = new UpdateLaureateProcessor(serializerFactory, entityService, objectMapper);

        // Incoming (triggering) laureate: valid required fields present, optional affiliationName is null
        Laureate incoming = new Laureate();
        incoming.setId(123); // source id used for lookup
        incoming.setFirstname("IncomingFirst");
        incoming.setSurname("IncomingLast");
        incoming.setCategory("Physics");
        incoming.setYear("1999");
        incoming.setAffiliationName(null); // should be filled from stored during merge

        // Build request payload using actual entity object (not raw JsonNode)
        DataPayload payload = new DataPayload();
        payload.setData(objectMapper.valueToTree(incoming));

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("req-1");
        request.setRequestId("req-1");
        request.setEntityId("ent-1");
        request.setProcessorName(UpdateLaureateProcessor.class.getSimpleName());
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

        JsonNode out = response.getPayload().getData();
        // After processing, incoming.affiliationName should be populated from stored record
        assertEquals("StoredAffiliation", out.get("affiliationName").asText());
        // lastUpdatedAt should be set on incoming to stored's lastUpdatedAt (merged)
        assertEquals("2025-08-01T12:00:00Z", out.get("lastUpdatedAt").asText());

        // Verify that entityService.updateItem was attempted with the technical UUID and a Laureate object
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString(technicalId)), any());
    }
}