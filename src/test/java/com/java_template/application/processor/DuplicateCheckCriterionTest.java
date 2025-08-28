package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DuplicateCheckCriterionTest {

    @Test
    void sunnyDay_noDuplicate_marks_not_invalid_and_returns_success() throws Exception {
        // Arrange: real ObjectMapper configured to ignore unknown properties
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked per requirements
        EntityService entityService = mock(EntityService.class);
        // Sunny-day: no existing laureates found -> empty list
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(Collections.emptyList()));

        // Instantiate processor with real serializers and mocked EntityService
        DuplicateCheckCriterion processor = new DuplicateCheckCriterion(serializerFactory, entityService, objectMapper);

        // Build a valid Laureate entity that passes isValid()
        Laureate laureate = new Laureate();
        laureate.setId("technical-1");
        laureate.setFirstname("Marie");
        laureate.setSurname("Curie");
        laureate.setCategory("Physics");
        laureate.setYear("1903");
        // optional fields left null

        // Convert entity to JsonNode for payload
        com.fasterxml.jackson.databind.JsonNode entityJson = objectMapper.valueToTree(laureate);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DuplicateCheckCriterion");
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

        // Assert
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processor should report success on sunny day");

        // Inspect returned payload entity - convert to Laureate
        assertNotNull(response.getPayload(), "Response payload should not be null");
        com.fasterxml.jackson.databind.JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData, "Returned payload data should not be null");

        Laureate returned = objectMapper.convertValue(returnedData, Laureate.class);
        assertNotNull(returned, "Returned Laureate should be present");
        // Sunny-day: no duplicate found -> processor should NOT mark validated as "INVALID"
        assertNotEquals("INVALID", returned.getValidated(), "Laureate should not be marked INVALID when no duplicates found");

        // Verify EntityService was queried for duplicates
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), any(), eq(true));
    }
}