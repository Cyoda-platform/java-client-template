package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mailingList.version_1.MailingList;
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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.java_template.common.config.Config.*;

@Component
public class ValidateMailingListProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ValidateMailingListProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;

    public ValidateMailingListProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing MailingList for validation request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(MailingList.class)
            .validate(this::isValidEntity, "Invalid MailingList entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(MailingList entity) {
        return entity != null;
    }

    private MailingList processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<MailingList> context) {
        MailingList list = context.entity();
        try {
            List<String> validRecipients = new ArrayList<>();
            if (list.getRecipients() != null) {
                for (String r : list.getRecipients()) {
                    // try to resolve recipient id
                    try {
                        CompletableFuture<ObjectNode> rFuture = entityService.getItem(
                            "recipient",
                            "1",
                            java.util.UUID.fromString(r)
                        );
                        ObjectNode rNode = rFuture.get();
                        if (rNode != null && rNode.has("email")) {
                            validRecipients.add(r);
                        }
                    } catch (Exception ex) {
                        // if not UUID or not found assume inline email, basic format check
                        if (r != null && r.contains("@")) {
                            validRecipients.add(r);
                        }
                    }
                }
            }
            list.setRecipients(validRecipients);
            if (validRecipients.isEmpty() || !Boolean.TRUE.equals(list.getIsActive())) {
                list.setStatus("INACTIVE");
            } else {
                list.setStatus("ACTIVE");
            }
            list.setUpdatedAt(java.time.OffsetDateTime.now().toString());
        } catch (Exception ex) {
            logger.error("Error validating mailing list", ex);
            list.setStatus("INACTIVE");
        }
        return list;
    }
}
