package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Component
public class InitiatePaymentProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(InitiatePaymentProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final HttpClient httpClient;
    private final String paymentProviderUrl;

    public InitiatePaymentProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.paymentProviderUrl = System.getenv().getOrDefault("PAYMENT_PROVIDER_URL", "http://localhost:8085/mock-pay");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing InitiatePayment for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Order.class)
            .validate(this::isValidEntity, "Invalid order state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Order order) {
        return order != null && order.isValid();
    }

    private ProcessorSerializer.ProcessorEntityExecutionContext<Order> processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Order> context) {
        Order order = context.entity();
        try {
            // Generate payment reference
            String paymentRef = "payprov-" + java.util.UUID.randomUUID().toString();
            order.setPaymentReference(paymentRef);

            // Persist payment reference on order
            if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                String tid = (String) context.attributes().get("technicalId");
                try {
                    entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), UUID.fromString(tid), order).whenComplete((id, ex) -> {
                        if (ex != null) logger.error("Failed to persist payment reference on order", ex);
                    });
                } catch (Exception ex) {
                    logger.error("Error scheduling order update with payment reference", ex);
                }
            }

            // Call external payment provider synchronously (prototype)
            try {
                ObjectNode payload = com.java_template.common.util.Json.mapper().createObjectNode();
                payload.put("paymentReference", paymentRef);
                payload.put("orderId", order.getId());
                if (order.getTotal() != null) payload.put("amount", order.getTotal().toString());

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(paymentProviderUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(com.java_template.common.util.Json.mapper().writeValueAsString(payload)))
                    .build();

                logger.info("Calling payment provider {} for order {}", paymentProviderUrl, order.getId());
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();
                String body = resp.body();
                logger.info("Payment provider responded with status {} and body {}", code, body);

                boolean success = false;
                if (code >= 200 && code < 300) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode node = com.java_template.common.util.Json.mapper().readTree(body);
                        if (node.has("status")) {
                            String status = node.get("status").asText();
                            success = "SUCCESS".equalsIgnoreCase(status) || "PAID".equalsIgnoreCase(status);
                        } else {
                            // fallback to treat 2xx as success
                            success = true;
                        }
                    } catch (Exception e) {
                        success = true; // treat as success if body can't be parsed but 2xx
                    }
                }

                if (success) {
                    // Update order status to PAID which will trigger PaymentSuccessProcessor
                    order.setStatus("PAID");
                } else {
                    order.setStatus("FAILED");
                }

                // Persist updated order status
                if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                    String tid = (String) context.attributes().get("technicalId");
                    try {
                        entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), UUID.fromString(tid), order).whenComplete((id, ex) -> {
                            if (ex != null) logger.error("Failed to persist order status after payment attempt", ex);
                        });
                    } catch (Exception ex) {
                        logger.error("Error scheduling order status update", ex);
                    }
                }

            } catch (Exception ex) {
                logger.error("Error calling payment provider", ex);
                // mark order as FAILED
                order.setStatus("FAILED");
                if (context.attributes() != null && context.attributes().get("technicalId") != null) {
                    String tid = (String) context.attributes().get("technicalId");
                    try {
                        entityService.updateItem(Order.ENTITY_NAME, String.valueOf(Order.ENTITY_VERSION), UUID.fromString(tid), order).whenComplete((id, ex2) -> {
                            if (ex2 != null) logger.error("Failed to persist order status FAILED after provider error", ex2);
                        });
                    } catch (Exception ex2) {
                        logger.error("Error scheduling order failure update", ex2);
                    }
                }
            }

            logger.info("Initiated payment for order {} with reference {} and final status {}", order.getId(), order.getPaymentReference(), order.getStatus());

        } catch (Exception ex) {
            logger.error("Error initiating payment for order", ex);
        }
        return context;
    }
}
