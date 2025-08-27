package com.java_template.application.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Component
public class UpsertUserWithAddressInlineProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(UpsertUserWithAddressInlineProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    public UpsertUserWithAddressInlineProcessor(SerializerFactory serializerFactory,
                                                EntityService entityService,
                                                ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing User for request: {}", request.getId());

        return serializer.withRequest(request) //always use this method name to request EntityProcessorCalculationResponse
            .toEntity(User.class)
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic) // Implement business logic here
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(User entity) {
        return entity != null && entity.isValid();
    }

    private User processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<User> context) {
        User entity = context.entity();

        // Business rule: Email must be present (EmailPresentCriterion)
        if (entity.getEmail() == null || entity.getEmail().isBlank()) {
            // If email missing, nothing to upsert; just return entity unchanged.
            logger.warn("UpsertUserWithAddressInlineProcessor: email missing for userId={}", entity.getUserId());
            return entity;
        }

        try {
            // Search for existing user by email (case-insensitive)
            SearchConditionRequest condition = SearchConditionRequest.group(
                "AND",
                Condition.of("$.email", "IEQUALS", entity.getEmail())
            );

            CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                User.ENTITY_NAME,
                String.valueOf(User.ENTITY_VERSION),
                condition,
                true
            );

            ArrayNode found = itemsFuture.join();

            if (found != null && !found.isEmpty()) {
                // Pick the first match. Prefer a record with different userId (dedupe).
                JsonNode matched = null;
                for (JsonNode n : found) {
                    if (n != null && n.hasNonNull("userId")) {
                        String foundId = n.path("userId").asText();
                        if (!foundId.equals(entity.getUserId())) {
                            matched = n;
                            break;
                        }
                        if (matched == null) matched = n; // fallback to first
                    }
                }

                if (matched != null) {
                    String existingUserId = matched.path("userId").asText(null);
                    String existingName = matched.path("name").asText(null);
                    String existingPhone = matched.path("phone").asText(null);
                    JsonNode existingAddress = matched.path("address");

                    // If another user exists with the same email, we want to upsert into that record.
                    // We must NOT call entityService.updateItem on the User entity directly.
                    // Instead, set the current entity's technical id (userId) to the existing user's id
                    // so that Cyoda persistence will treat this as an update to the existing record.
                    if (existingUserId != null && !existingUserId.isBlank()) {
                        entity.setUserId(existingUserId);
                    }

                    // Merge name/phone: prefer incoming non-blank values; otherwise keep existing
                    if ((entity.getName() == null || entity.getName().isBlank()) && existingName != null && !existingName.isBlank()) {
                        entity.setName(existingName);
                    }
                    if ((entity.getPhone() == null || entity.getPhone().isBlank()) && existingPhone != null && !existingPhone.isBlank()) {
                        entity.setPhone(existingPhone);
                    }

                    // Merge address: if incoming address is null, reuse existing address.
                    User.Address addr = entity.getAddress();
                    if (addr == null) {
                        addr = new User.Address();
                        entity.setAddress(addr);
                    }
                    // For each address field, prefer incoming non-blank; otherwise copy existing value
                    if ((addr.getLine1() == null || addr.getLine1().isBlank())
                        && existingAddress.hasNonNull("line1")) {
                        addr.setLine1(existingAddress.path("line1").asText(null));
                    }
                    if ((addr.getCity() == null || addr.getCity().isBlank())
                        && existingAddress.hasNonNull("city")) {
                        addr.setCity(existingAddress.path("city").asText(null));
                    }
                    if ((addr.getPostcode() == null || addr.getPostcode().isBlank())
                        && existingAddress.hasNonNull("postcode")) {
                        addr.setPostcode(existingAddress.path("postcode").asText(null));
                    }
                    if ((addr.getCountry() == null || addr.getCountry().isBlank())
                        && existingAddress.hasNonNull("country")) {
                        addr.setCountry(existingAddress.path("country").asText(null));
                    }

                    // Always update address.updatedAt to now to reflect inline update
                    addr.setUpdatedAt(Instant.now().toString());

                    logger.info("UpsertUserWithAddressInlineProcessor: merging into existing userId={}", existingUserId);
                }
            } else {
                // No existing user found by email: ensure address.updatedAt is set
                User.Address addr = entity.getAddress();
                if (addr == null) {
                    addr = new User.Address();
                    entity.setAddress(addr);
                }
                addr.setUpdatedAt(Instant.now().toString());
                // Leave entity.userId as-is: Cyoda will persist it (create if new)
                logger.info("UpsertUserWithAddressInlineProcessor: no existing user for email={}, creating/updating current entity", entity.getEmail());
            }
        } catch (Exception ex) {
            // Do not fail the processor; log and return entity unchanged
            logger.error("UpsertUserWithAddressInlineProcessor: error while searching for existing user by email={} - {}", entity.getEmail(), ex.getMessage(), ex);
        }

        return entity;
    }
}