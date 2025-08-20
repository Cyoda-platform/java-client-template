package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.address.version_1.Address;
import com.java_template.application.entity.user.version_1.User;
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
import java.util.UUID;

import static com.java_template.common.config.Config.*;

@Component
public class IdentifyProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public IdentifyProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Identify for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(ObjectNode.class)
            .validate(this::isValidPayload, "Invalid identify payload")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidPayload(ObjectNode payload) {
        if (payload == null) return false;
        if (!payload.hasNonNull("email")) return false;
        if (!payload.hasNonNull("name")) return false;
        if (!payload.hasNonNull("address")) return false;
        JsonNode addr = payload.get("address");
        if (!addr.hasNonNull("line1") || !addr.hasNonNull("city") || !addr.hasNonNull("postcode") || !addr.hasNonNull("country")) return false;
        return true;
    }

    private ObjectNode processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<ObjectNode> context) {
        ObjectNode payload = context.entity();
        // Upsert user by email
        String email = payload.get("email").asText();
        String name = payload.get("name").asText();
        String phone = payload.hasNonNull("phone") ? payload.get("phone").asText() : null;

        try {
            SearchConditionRequest cond = SearchConditionRequest.group("AND", Condition.of("$.email", "IEQUALS", email));
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> usersFuture = entityService.getItemsByCondition(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), cond, true);
            com.fasterxml.jackson.databind.node.ArrayNode users = usersFuture.get();
            User user;
            if (users != null && users.size() > 0) {
                // update existing
                ObjectNode existing = (ObjectNode) users.get(0);
                String technicalId = existing.get("technicalId").asText();
                CompletableFuture<ObjectNode> fetched = entityService.getItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), UUID.fromString(technicalId));
                ObjectNode userNode = fetched.get();
                user = context.serializer().convert(userNode, User.class);
                user.setName(name);
                user.setPhone(phone);
                user.setStatus("IDENTIFIED");
                entityService.updateItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), UUID.fromString(technicalId), user);
            } else {
                // create new
                user = new User();
                user.setUserId(null);
                user.setName(name);
                user.setEmail(email);
                user.setPhone(phone);
                user.setStatus("IDENTIFIED");
                CompletableFuture<UUID> idFuture = entityService.addItem(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), user);
                UUID uid = idFuture.get();
                logger.info("Created new User with technicalId {}", uid);
            }

            // Create Address linked to user (we'll link by email-based lookup of technicalId)
            Address address = new Address();
            JsonNode addr = payload.get("address");
            address.setLine1(addr.get("line1").asText());
            address.setCity(addr.get("city").asText());
            address.setPostcode(addr.get("postcode").asText());
            address.setCountry(addr.get("country").asText());
            // attempt to retrieve user technical id for linkage
            CompletableFuture<com.fasterxml.jackson.databind.node.ArrayNode> u2 = entityService.getItemsByCondition(User.ENTITY_NAME, String.valueOf(User.ENTITY_VERSION), SearchConditionRequest.group("AND", Condition.of("$.email", "IEQUALS", email)), true);
            com.fasterxml.jackson.databind.node.ArrayNode found = u2.get();
            if (found != null && found.size() > 0) {
                ObjectNode first = (ObjectNode) found.get(0);
                String technicalId = first.get("technicalId").asText();
                address.setUserId(technicalId);
            }
            CompletableFuture<UUID> aid = entityService.addItem(Address.ENTITY_NAME, String.valueOf(Address.ENTITY_VERSION), address);
            UUID addressId = aid.get();
            logger.info("Created address with technicalId {}", addressId);

        } catch (Exception e) {
            logger.error("Error during IdentifyProcessor execution", e);
        }

        return payload;
    }
}
