package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class UpsertProcessorTest {

    @Test
    void sunnyDay_upsert_existing_found() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);

        // Prepare an existing stored Laureate payload (simulates found existing record)
        Laureate existingLaureate = new Laureate();
        existingLaureate.setId(42);
        existingLaureate.setFirstname("Existing");
        existingLaureate.setSurname("Person");
        existingLaureate.setCategory("physics");
        existingLaureate.setYear("2020");
        existingLaureate.setValidationStatus("VALID");

        JsonNode existingJson = objectMapper.valueToTree(existingLaureate);
        DataPayload existingPayload = new DataPayload();
        existingPayload.setData(existingJson);
        // Provide a technical id to allow update path
        existingPayload.setId("11111111-1111-1111-1111-111111111111");

        when(entityService.getItemsByCondition(
                anyString(), anyInt(), any(), anyBoolean()
        )).thenReturn(CompletableFuture.completedFuture(List.of(existingPayload)));

        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.fromString("11111111-1111-1111-1111-111111111111")));

        UpsertProcessor processor = new UpsertProcessor(serializerFactory, entityService, objectMapper);

        // Incoming entity (source record) that will match the existing by source id
        Laureate incoming = new Laureate();
        incoming.setId(42); // matches existing source id
        incoming.setFirstname("NewFirst");
        incoming.setSurname("Person");
        incoming.setCategory("physics");
        incoming.setYear("2020");
        incoming.setValidationStatus("VALID"); // required by isValid()

        JsonNode incomingJson = objectMapper.valueToTree(incoming);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("UpsertProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(incomingJson);
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
        assertNotNull(out);
        assertTrue(out.hasNonNull("lastSeenAt"));
        assertFalse(out.get("lastSeenAt").asText().isBlank());
        assertEquals("VALID", out.get("validationStatus").asText());

        // Verify updateItem was invoked for existing record
        verify(entityService, atLeastOnce()).updateItem(eq(UUID.fromString("11111111-1111-1111-1111-111111111111")), any());
    }
}