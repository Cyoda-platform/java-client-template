package com.java_template.application.processor;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AutoMarkPaidProcessorTest {

    @Test
    void sunnyDay_process_test() {
        // Arrange - real Jackson serializers and SerializerFactory
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                List.of(processorSerializer),
                List.of(criterionSerializer)
        );

        // Processor under test (no EntityService required)
        AutoMarkPaidProcessor processor = new AutoMarkPaidProcessor(serializerFactory);

        // Build a valid Payment entity that passes isValid() and is in INITIATED state
        Payment payment = new Payment();
        payment.setPaymentId("payment-1");
        payment.setCartId("cart-1");
        payment.setAmount(100.0);
        payment.setProvider("DUMMY");
        payment.setStatus("INITIATED");
        payment.setCreatedAt(Instant.now().toString());
        // updatedAt left null initially

        JsonNode entityJson = objectMapper.valueToTree(payment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("AutoMarkPaidProcessor");
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
        assertNotNull(response);
        assertTrue(response.getSuccess(), "Processor should report success on sunny path");
        assertNotNull(response.getPayload(), "Response payload should be present");

        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data must be present");
        assertEquals("PAID", responseData.get("status").asText(), "Payment status should be updated to PAID");
        assertTrue(responseData.hasNonNull("updatedAt"), "updatedAt should be set after processing");
        assertFalse(responseData.get("updatedAt").asText().isBlank(), "updatedAt should be a non-blank timestamp");
    }
}