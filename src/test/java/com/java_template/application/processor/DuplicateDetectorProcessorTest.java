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
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class DuplicateDetectorProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Mock only EntityService
        EntityService entityService = mock(EntityService.class);
        // Sunny-day: no existing laureates found
        when(entityService.getItemsByCondition(anyString(), anyInt(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        // Construct processor under test
        DuplicateDetectorProcessor processor = new DuplicateDetectorProcessor(serializerFactory, entityService, objectMapper);

        // Build a valid Laureate entity (must pass isValid)
        Laureate laureate = new Laureate();
        laureate.setId(853);
        laureate.setFirstname("Akira");
        laureate.setSurname("Suzuki");
        laureate.setCategory("Chemistry");
        laureate.setYear("2010");
        laureate.setAgeAtAward(80); // optional but valid

        JsonNode entityJson = objectMapper.valueToTree(laureate);

        DataPayload payload = new DataPayload();
        payload.setData(entityJson);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DuplicateDetectorProcessor");
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

        // Inspect returned payload data
        assertNotNull(response.getPayload());
        JsonNode out = response.getPayload().getData();
        assertNotNull(out);
        assertEquals(853, out.get("id").asInt());
        assertEquals("Akira", out.get("firstname").asText());
        assertEquals("Suzuki", out.get("surname").asText());

        // Since no existing records were returned by EntityService, processor returns the incoming entity unchanged.
        // The lastUpdatedAt field was not set in that path, so it should be null in the serialized output.
        assertTrue(out.has("lastUpdatedAt"));
        assertTrue(out.get("lastUpdatedAt").isNull());

        // Verify the processor attempted to search for existing laureates
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Laureate.ENTITY_NAME), eq(Laureate.ENTITY_VERSION), any(), eq(true));
    }
}