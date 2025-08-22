package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.application.entity.order.version_1.Order;
import com.java_template.application.entity.user.version_1.User;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.java_template.common.config.Config.*;

@Component
public class AddressArchivedProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AddressArchivedProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public AddressArchivedProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Address for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Address.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Address entity) {
        return entity != null && entity.isValid();
    }

    private Address processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Address> context) {
        Address entity = context.entity();

        // Business rule: Archive verified addresses that are not default and not referenced by any User or Order.
        //  - Only consider addresses with status "Verified" (case-insensitive).
        //  - Do not archive if isDefault == Boolean.TRUE
        //  - Do not archive if any User.defaultAddressId equals this address id
        //  - Do not archive if any Order.shippingAddressId or Order.billingAddressId equals this address id
        //  - If none of the above, mark status as "Archived" and unset isDefault

        if (entity == null) {
            logger.debug("Address entity is null, skipping archival.");
            return entity;
        }

        String status = entity.getStatus();
        if (status == null || !status.equalsIgnoreCase("Verified")) {
            logger.debug("Address {} not in Verified state (status={}), skipping archival.", entity.getId(), status);
            return entity;
        }

        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            logger.debug("Address {} is marked as default, skipping archival.", entity.getId());
            return entity;
        }

        // Check Users referencing this address as defaultAddressId
        try {
            SearchConditionRequest userCondition = SearchConditionRequest.group("AND",
                Condition.of("$.defaultAddressId", "EQUALS", entity.getId())
            );
            CompletableFuture<ArrayNode> usersFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                userCondition,
                true
            );
            ArrayNode users = usersFuture.get(5, TimeUnit.SECONDS);
            if (users != null && users.size() > 0) {
                logger.debug("Address {} referenced by {} user(s) as defaultAddressId, skipping archival.", entity.getId(), users.size());
                return entity;
            }
        } catch (Exception ex) {
            logger.warn("Failed to evaluate User references for Address {}. Skipping archival to be safe. Error: {}", entity.getId(), ex.getMessage());
            return entity;
        }

        // Check Orders referencing this address as shipping or billing address
        try {
            SearchConditionRequest orderCondition = SearchConditionRequest.group("OR",
                Condition.of("$.shippingAddressId", "EQUALS", entity.getId()),
                Condition.of("$.billingAddressId", "EQUALS", entity.getId())
            );
            CompletableFuture<ArrayNode> ordersFuture = entityService.getItemsByCondition(
                Order.ENTITY_NAME,
                String.valueOf(Order.ENTITY_VERSION),
                orderCondition,
                true
            );
            ArrayNode orders = ordersFuture.get(5, TimeUnit.SECONDS);
            if (orders != null && orders.size() > 0) {
                logger.debug("Address {} referenced by {} order(s), skipping archival.", entity.getId(), orders.size());
                return entity;
            }
        } catch (Exception ex) {
            logger.warn("Failed to evaluate Order references for Address {}. Skipping archival to be safe. Error: {}", entity.getId(), ex.getMessage());
            return entity;
        }

        // No references found and not default -> archive
        logger.info("Archiving Address {} as it is verified, not default and unreferenced.", entity.getId());
        entity.setStatus("Archived");
        entity.setIsDefault(false);

        return entity;
    }
}