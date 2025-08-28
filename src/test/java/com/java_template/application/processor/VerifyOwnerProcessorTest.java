package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.junit.jupiter.api.Assertions.*;

public class VerifyOwnerProcessorTest {

    @Test
    void sunnyDay_autoVerify_exampleDomain() {
        // Setup ObjectMapper as required
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // Real serializers and factory (no Spring)
        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required for this processor)
        VerifyOwnerProcessor processor = new VerifyOwnerProcessor(serializerFactory);

        // Build a valid Owner entity JSON that should pass isValid()
        Owner owner = new Owner();
        owner.setId("owner-1");
        owner.setName("Test Owner");
        owner.setEmail("user@example.com"); // example.com domain should auto-verify
        owner.setVerified(false); // initial state false -> processor should set true

        JsonNode ownerJson = objectMapper.valueToTree(owner);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("VerifyOwnerProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(ownerJson);
        request.setPayload(payload);

        // Minimal CyodaEventContext anonymous implementation
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

        JsonNode respData = response.getPayload().getData();
        assertNotNull(respData);

        Owner returned = objectMapper.convertValue(respData, Owner.class);
        assertNotNull(returned);
        // Sunny-day expectation: example.com email auto-verifies the owner
        assertTrue(returned.getVerified());
    }
}