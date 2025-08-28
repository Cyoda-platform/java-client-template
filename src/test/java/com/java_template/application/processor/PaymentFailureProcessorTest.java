package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.reservation.version_1.Reservation;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.jackson.JacksonCriterionSerializer;
import com.java_template.common.serializer.jackson.JacksonProcessorSerializer;
import com.java_template.common.service.EntityService;
import com.java_template.common.workflow.CyodaEventContext;
import org.cyoda.cloud.api.event.common.DataPayload;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class PaymentFailureProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange
        ObjectMapper objectMapper = new ObjectMapper();
        JacksonProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        JacksonCriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        EntityService entityService = mock(EntityService.class);

        // Prepare a valid Payment that represents a FAILED payment (sunny path)
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID().toString());
        payment.setCartId(UUID.randomUUID().toString());
        payment.setCreatedAt(Instant.now().toString());
        payment.setUserId(UUID.randomUUID().toString());
        payment.setAmount(100.0);
        payment.setStatus("FAILED");
        payment.setApprovedAt(null);

        JsonNode paymentJson = objectMapper.valueToTree(payment);

        // Prepare a Reservation that will be returned by EntityService.getItemsByCondition
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setCartId(payment.getCartId());
        reservation.setProductId(UUID.randomUUID().toString());
        reservation.setWarehouseId(UUID.randomUUID().toString());
        reservation.setQty(1);
        reservation.setStatus("ACTIVE");
        reservation.setCreatedAt(Instant.now().toString());
        reservation.setExpiresAt(Instant.now().plusSeconds(3600).toString());

        JsonNode reservationJson = objectMapper.valueToTree(reservation);
        DataPayload reservationPayload = new DataPayload();
        reservationPayload.setData(reservationJson);

        // Stub EntityService.getItemsByCondition to return our reservation
        when(entityService.getItemsByCondition(eq(Reservation.ENTITY_NAME), eq(Reservation.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(reservationPayload)));

        // Stub updateItem to succeed
        UUID reservationUuid = UUID.fromString(reservation.getId());
        when(entityService.updateItem(eq(reservationUuid), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        // Build processor
        PaymentFailureProcessor processor = new PaymentFailureProcessor(serializerFactory, entityService, objectMapper);

        // Build request
        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId(payment.getId());
        request.setProcessorName("PaymentFailureProcessor");
        DataPayload payload = new DataPayload();
        payload.setData(paymentJson);
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

        // Inspect returned payment entity payload
        DataPayload respPayload = response.getPayload();
        assertNotNull(respPayload);
        JsonNode respData = respPayload.getData();
        assertNotNull(respData);
        Payment outPayment = objectMapper.treeToValue(respData, Payment.class);
        assertNotNull(outPayment);
        assertEquals(payment.getId(), outPayment.getId());
        assertEquals("FAILED", outPayment.getStatus());
        assertNull(outPayment.getApprovedAt());

        // Verify that updateItem was called for the reservation and that the reservation was released
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(entityService, atLeastOnce()).updateItem(eq(reservationUuid), captor.capture());
        Object updatedObj = captor.getValue();
        // Convert captured object to Reservation (it should be the Reservation instance that was modified)
        Reservation updatedReservation = objectMapper.convertValue(updatedObj, Reservation.class);
        assertEquals("RELEASED", updatedReservation.getStatus());
        assertNotNull(updatedReservation.getExpiresAt());
    }
}