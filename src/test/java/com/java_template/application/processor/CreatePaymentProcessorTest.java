package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.adoptionrequest.version_1.AdoptionRequest;
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
import static org.mockito.Mockito.mock;

public class CreatePaymentProcessorTest {

    @Test
    void sunnyDay_createPayment_setsPendingForPositiveFee() {
        // Arrange - real Jackson serializers and factory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Only EntityService is mocked (not used by this processor, but required by constructor)
        EntityService entityService = mock(EntityService.class);

        CreatePaymentProcessor processor = new CreatePaymentProcessor(serializerFactory, entityService);

        // Create a valid AdoptionRequest that represents the sunny-path:
        // status = "APPROVED", adoptionFee > 0, paymentStatus = "NOT_PAID"
        AdoptionRequest requestEntity = new AdoptionRequest();
        requestEntity.setRequestId("req-123");
        requestEntity.setPetId("pet-123");
        requestEntity.setUserId("user-123");
        requestEntity.setRequestedAt("2025-01-01T00:00:00Z");
        requestEntity.setStatus("APPROVED");
        requestEntity.setPaymentStatus("NOT_PAID");
        requestEntity.setAdoptionFee(50.0);
        requestEntity.setHomeVisitRequired(false);

        JsonNode entityJson = objectMapper.valueToTree(requestEntity);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreatePaymentProcessor");
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

        // Assert that the processor updated the entity to payment pending state
        JsonNode resultData = response.getPayload().getData();
        assertNotNull(resultData);
        assertEquals("PENDING", resultData.get("paymentStatus").asText());
        assertEquals("PAYMENT_PENDING", resultData.get("status").asText());
    }
}