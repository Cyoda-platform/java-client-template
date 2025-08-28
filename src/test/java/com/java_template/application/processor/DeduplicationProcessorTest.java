package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.laureate.version_1.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.criterion.CriterionSerializer;
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

public class DeduplicationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);
        // Sunny path: no existing items found -> expect "NEW"
        when(entityService.getItemsByCondition(anyString(), any(), any(), anyBoolean()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        DeduplicationProcessor processor = new DeduplicationProcessor(serializerFactory, entityService, objectMapper);

        ObjectNode data = objectMapper.createObjectNode();
        data.put("id", 1);
        data.put("firstname", "John");
        data.put("surname", "Doe");
        data.put("category", "Physics");
        data.put("year", "2024");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("DeduplicationProcessor");
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
        // normalizedCountryCode should be set to "NEW" in the sunny path
        assertEquals("NEW", out.path("normalizedCountryCode").asText());
    }
}