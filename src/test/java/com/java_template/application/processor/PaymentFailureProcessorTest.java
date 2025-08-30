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

import static org.junit.jupiter.api.Assertions.*;

public class PaymentFailureProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real objects)
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Instantiate processor (no EntityService required)
        PaymentFailureProcessor processor = new PaymentFailureProcessor(serializerFactory);

        // Build a valid Payment entity that should be marked FAILED because provider != "DUMMY"
        Payment payment = new Payment();
        payment.setPaymentId("payment-123");
        payment.setCartId("cart-456");
        payment.setAmount(100.0);
        payment.setProvider("STRIPE"); // non-DUMMY provider -> should trigger failure
        payment.setStatus("INITIATED");
        payment.setCreatedAt(Instant.now().toString());

        JsonNode entityJson = objectMapper.valueToTree(payment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("PaymentFailureProcessor");
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
        assertNotNull(response, "Response should not be null");
        assertTrue(response.getSuccess(), "Processing should be successful");

        // Inspect payload - should have status set to FAILED and updatedAt set
        assertNotNull(response.getPayload(), "Response payload should not be null");
        JsonNode responseData = response.getPayload().getData();
        assertNotNull(responseData, "Response data node should not be null");

        // Convert to Payment for convenience of assertions
        Payment processed = objectMapper.treeToValue(responseData, Payment.class);
        assertNotNull(processed, "Processed Payment should be deserializable");

        assertEquals("FAILED", processed.getStatus(), "Payment status should be transitioned to FAILED");
        assertNotNull(processed.getUpdatedAt(), "updatedAt should be set on failure");
        assertFalse(processed.getUpdatedAt().isBlank(), "updatedAt should not be blank");
    }
}