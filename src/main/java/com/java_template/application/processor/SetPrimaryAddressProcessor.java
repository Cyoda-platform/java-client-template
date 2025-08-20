package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@Component
public class SetPrimaryAddressProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SetPrimaryAddressProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public SetPrimaryAddressProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing SetPrimaryAddress for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid address for primary set")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Address address) {
        return address != null && address.isValid();
    }

    private Address processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Address> context) {
        Address address = context.entity();
        // Toggle other addresses of same user and type to not primary
        try {
            CompletableFuture<ArrayNode> addressesFuture = entityService.getItemsByCondition(Address.ENTITY_NAME, String.valueOf(Address.ENTITY_VERSION), SearchConditionRequest.group("AND", Condition.of("$.userId", "EQUALS", address.getUserId()), Condition.of("$.type", "EQUALS", address.getType())), true);
            ArrayNode addresses = addressesFuture.get();
            if (addresses != null) {
                for (int i = 0; i < addresses.size(); i++) {
                    ObjectNode node = (ObjectNode) addresses.get(i);
                    Address a = objectMapper.convertValue(node, Address.class);
                    if (!a.getAddressId().equals(address.getAddressId()) && Boolean.TRUE.equals(a.getPrimary())) {
                        a.setPrimary(false);
                        if (node.has("technicalId")) {
                            try {
                                entityService.updateItem(Address.ENTITY_NAME, String.valueOf(Address.ENTITY_VERSION), java.util.UUID.fromString(node.get("technicalId").asText()), a).get();
                            } catch (Exception ex) {
                                logger.warn("Failed to unset primary on address {} for user {}", a.getAddressId(), a.getUserId(), ex);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Error while toggling primary address for user {}", address.getUserId(), e);
        }

        address.setPrimary(true);
        logger.info("Address {} set as primary for user {}", address.getAddressId(), address.getUserId());
        return address;
    }
}
