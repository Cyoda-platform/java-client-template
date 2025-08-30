package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.cart.version_1.Cart;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.payment.version_1.Payment;
import com.java_template.application.entity.product.version_1.Product;
import com.java_template.application.entity.shipment.version_1.Shipment;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CreateOrderFromPaidProcessorTest {

    @Test
    void sunnyDay_process_test() throws Exception {
        // Arrange - ObjectMapper and serializers (real)
        ObjectMapper objectMapper = new ObjectMapper();
        // Configure ObjectMapper to ignore unknown properties during deserialization
        objectMapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        ProcessorSerializer processorSerializer = new JacksonProcessorSerializer(objectMapper);
        CriterionSerializer criterionSerializer = new JacksonCriterionSerializer(objectMapper);
        SerializerFactory serializerFactory = new SerializerFactory(
                java.util.List.of(processorSerializer),
                java.util.List.of(criterionSerializer)
        );

        // Mock EntityService (only mock allowed)
        EntityService entityService = mock(EntityService.class);

        // Prepare a Cart that will be returned by getItem(cartId)
        Cart cart = new Cart();
        cart.setCartId(UUID.randomUUID().toString());
        cart.setCreatedAt("2025-01-01T00:00:00Z");
        cart.setGrandTotal(100.0);
        cart.setStatus("ACTIVE");
        cart.setTotalItems(1);

        Cart.Line cartLine = new Cart.Line();
        cartLine.setName("Test Product");
        cartLine.setSku("SKU-123");
        cartLine.setPrice(100.0);
        cartLine.setQty(1);
        cart.getLines().add(cartLine);

        Cart.GuestContact guestContact = new Cart.GuestContact();
        Cart.Address address = new Cart.Address();
        address.setLine1("123 Test St");
        address.setCountry("Testland");
        address.setPostcode("T1 1ST");
        address.setCity("Testville");
        guestContact.setAddress(address);
        guestContact.setEmail("guest@example.com");
        guestContact.setName("Guest");
        guestContact.setPhone("0000000000");
        cart.setGuestContact(guestContact);

        // Wrap cart into DataPayload to be returned by entityService.getItem
        DataPayload cartPayload = new DataPayload();
        JsonNode cartNode = objectMapper.valueToTree(cart);
        cartPayload.setData(cartNode);

        // Stub getItem to return the cart
        String cartTechnicalId = cart.getCartId();
        when(entityService.getItem(UUID.fromString(cartTechnicalId)))
                .thenReturn(CompletableFuture.completedFuture(cartPayload));

        // Stub addItem for Order creation
        when(entityService.addItem(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Prepare a product to be returned by getItemsByCondition
        Product product = new Product();
        product.setName("Test Product");
        product.setSku("SKU-123");
        product.setPrice(100.0);
        product.setQuantityAvailable(10);

        DataPayload productPayload = new DataPayload();
        productPayload.setData(objectMapper.valueToTree(product));
        // meta must contain entityId for updateItem lookup
        JsonNode prodMeta = objectMapper.createObjectNode().put("entityId", UUID.randomUUID().toString());
        productPayload.setMeta(prodMeta);

        when(entityService.getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true)))
                .thenReturn(CompletableFuture.completedFuture(List.of(productPayload)));

        // Stub updateItem for product quantity update
        when(entityService.updateItem(any(UUID.class), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Stub addItem for Shipment creation
        when(entityService.addItem(eq(Shipment.ENTITY_NAME), eq(Shipment.ENTITY_VERSION), any()))
                .thenReturn(CompletableFuture.completedFuture(UUID.randomUUID()));

        // Create processor instance (real serializerFactory and objectMapper, mocked entityService)
        CreateOrderFromPaidProcessor processor = new CreateOrderFromPaidProcessor(serializerFactory, entityService, objectMapper);

        // Build Payment payload that triggers the processor (status PAID) and references the cartTechnicalId
        Payment payment = new Payment();
        payment.setPaymentId(UUID.randomUUID().toString());
        payment.setCartId(cartTechnicalId);
        payment.setAmount(100.0);
        payment.setProvider("DUMMY");
        payment.setStatus("PAID");
        payment.setCreatedAt("2025-01-01T00:00:00Z");
        payment.setUpdatedAt("2025-01-01T00:00:00Z");

        JsonNode paymentNode = objectMapper.valueToTree(payment);

        EntityProcessorCalculationRequest request = new EntityProcessorCalculationRequest();
        request.setId("r1");
        request.setRequestId("r1");
        request.setEntityId("e1");
        request.setProcessorName("CreateOrderFromPaidProcessor");
        DataPayload requestPayload = new DataPayload();
        requestPayload.setData(paymentNode);
        request.setPayload(requestPayload);

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

        // Validate returned payload contains the (unchanged) Payment with status PAID
        assertNotNull(response.getPayload());
        JsonNode returnedData = response.getPayload().getData();
        assertNotNull(returnedData);
        assertEquals(payment.getPaymentId(), returnedData.get("paymentId").asText());
        assertEquals("PAID", returnedData.get("status").asText());

        // Verify key EntityService interactions occurred
        verify(entityService, atLeastOnce()).getItem(eq(UUID.fromString(cartTechnicalId)));
        verify(entityService, atLeastOnce()).addItem(eq(Order.ENTITY_NAME), eq(Order.ENTITY_VERSION), any());
        verify(entityService, atLeastOnce()).getItemsByCondition(eq(Product.ENTITY_NAME), eq(Product.ENTITY_VERSION), any(), eq(true));
        verify(entityService, atLeastOnce()).updateItem(any(UUID.class), any());
        verify(entityService, atLeastOnce()).addItem(eq(Shipment.ENTITY_NAME), eq(Shipment.ENTITY_VERSION), any());
    }
}