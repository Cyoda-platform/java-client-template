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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ActivateOwnerProcessorTest {

    @Test
    void sunnyDay_activateOwner_process_test() {
        // Arrange - real Jackson serializer setup
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        ActivateOwnerProcessor processor = new ActivateOwnerProcessor(serializerFactory);

        // Build a valid Owner entity that will be considered "verified"
        Owner owner = new Owner();
        owner.setId("owner-1");
        owner.setName("John Doe");
        owner.setEmail("john.doe@example.com");
        owner.setVerified(Boolean.TRUE);
        // petsOwned left as null to exercise initialization to empty list
        owner.setPhone(" 123 "); // will be trimmed to "123"

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("owner-1");
        request.setProcessorName("ActivateOwnerProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);
        request.setPayload(payload);

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

        // Inspect resulting payload
        assertNotNull(response.getPayload());
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);

        // phone should be trimmed to "123"
        JsonNode phoneNode = resultData.get("phone");
        assertNotNull(phoneNode);
        assertEquals("123", phoneNode.asText());

        // petsOwned should be initialized to an empty array
        JsonNode petsNode = resultData.get("petsOwned");
        assertNotNull(petsNode);
        assertTrue(petsNode.isArray());
        assertEquals(0, petsNode.size());

        // Ensure core identity fields preserved
        assertEquals("owner-1", resultData.get("id").asText());
        assertEquals("John Doe", resultData.get("name").asText());
        assertTrue(resultData.get("verified").asBoolean());
    }
}