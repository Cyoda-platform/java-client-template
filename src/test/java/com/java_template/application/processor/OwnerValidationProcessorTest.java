package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.java_template.application.entity.owner.version_1.Owner;
import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class OwnerValidationProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - ObjectMapper and real serializers
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        OwnerValidationProcessor processor = new OwnerValidationProcessor(serializerFactory, objectMapper);

        // Build a valid Owner that will pass isValid() and be normalized by processor logic
        Owner owner = new Owner();
        owner.setId("o-1");
        owner.setName("  John Doe  "); // will be trimmed to "John Doe"
        owner.setContactEmail("Test@Example.COM "); // will be trimmed and lowercased
        owner.setPhone("(123) 456-7890"); // will become digits only: 1234567890
        owner.setAddress(" 123 Main St ");
        owner.setPreferences("{ \"notify\" : true }"); // will be compacted to {"notify":true}

        JsonNode entityJson = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("o-1");
        request.setProcessorName("OwnerValidationProcessor");
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

        // Inspect payload data for normalized values
        JsonNode data = response.getPayload().getData();
        assertNotNull(data);

        assertEquals("o-1", data.get("id").asText());
        assertEquals("John Doe", data.get("name").asText()); // trimmed
        assertEquals("test@example.com", data.get("contactEmail").asText()); // trimmed & lowercased
        assertEquals("1234567890", data.get("phone").asText()); // digits only
        assertEquals("123 Main St", data.get("address").asText()); // trimmed
        // preferences stored as compacted JSON string
        assertEquals("{\"notify\":true}", data.get("preferences").asText());
    }
}