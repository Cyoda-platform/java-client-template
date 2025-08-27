package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ValidationProcessorTest {

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

        // ValidationProcessor requires EntityService in constructor; mock it per project rules
        EntityService entityService = mock(EntityService.class);

        ValidationProcessor processor = new ValidationProcessor(serializerFactory, entityService, objectMapper);

        // Build a payload that satisfies Laureate.isValid() and the processor's validation logic
        ObjectNode data = objectMapper.createObjectNode();
        // Laureate.id is Integer; set as number
        data.put("id", 1);
        data.put("firstname", "Albert");
        data.put("surname", "Einstein");
        data.put("category", "Physics");
        data.put("year", "1921");
        // Provide an initial validationStatus (will be overwritten by processor)
        data.put("validationStatus", "PENDING");

        DataPayload payload = new DataPayload();
        payload.setData(data);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("1");
        request.setProcessorName("ValidationProcessor");
        request.setPayload(payload);

        CyodaEventContext<EntityProcessorCalculationRequest> context = new CyodaEventContext<>() {
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
        EntityProcessorCalculationResponse response = processor.process(context);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());

        // The processor should have set validationStatus to "VALID" for a correct entity
        ObjectNode out = (ObjectNode) response.getPayload().getData();
        assertNotNull(out);
        assertEquals("VALID", out.get("validationStatus").asText());
        // Ensure id remained present
        assertEquals(1, out.get("id").asInt());
        // Ensure name fields preserved
        assertEquals("Albert", out.get("firstname").asText());
        assertEquals("Einstein", out.get("surname").asText());
    }
}