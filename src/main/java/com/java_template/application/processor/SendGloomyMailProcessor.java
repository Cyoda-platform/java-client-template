package com.java_template.application.processor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.application.entity.recipient.version_1.Recipient;
import com.java_template.application.entity.deliveryRecord.version_1.DeliveryRecord;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component
public class SendGloomyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendGloomyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final int defaultDailyLimit = 100;

    public SendGloomyMailProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for gloomy send request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Mail.class)
            .validate(this::isValidEntity, "Invalid Mail entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.getMailList() != null;
    }

    private Mail processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();
        try {
            // resolve recipients
            List<Recipient> resolved = resolveRecipients(mail.getMailList());
            List<Recipient> filtered = filterRecipients(resolved, mail);

            if (filtered.isEmpty()) {
                mail.setStatus("FAILED");
                return mail;
            }

            // create delivery records
            List<DeliveryRecord> records = filtered.stream().map(r -> {
                DeliveryRecord dr = new DeliveryRecord();
                dr.setMailTechnicalId(mail.getTechnicalId());
                dr.setRecipientId(r.getId());
                dr.setRecipientEmail(r.getEmail());
                dr.setStatus("PENDING");
                dr.setAttempts(0);
                dr.setCreatedAt(java.time.OffsetDateTime.now().toString());
                dr.setUpdatedAt(dr.getCreatedAt());
                return dr;
            }).collect(Collectors.toList());

            // persist each delivery record via entityService
            for (DeliveryRecord dr : records) {
                try {
                    CompletableFuture<java.util.UUID> fut = entityService.addItem(
                        DeliveryRecord.ENTITY_NAME,
                        String.valueOf(DeliveryRecord.ENTITY_VERSION),
                        dr
                    );
                    fut.get();
                } catch (InterruptedException | ExecutionException e) {
                    logger.error("Error persisting delivery record", e);
                }
            }

            // update mail status
            mail.setStatus("QUEUED");
            logger.info("Gloomy mail queued with {} recipients", records.size());
        } catch (Exception ex) {
            logger.error("Error in send gloomy mail processor", ex);
            mail.setStatus("FAILED");
        }
        return mail;
    }

    private List<Recipient> resolveRecipients(Object mailList) {
        List<Recipient> result = new ArrayList<>();
        if (mailList instanceof String) {
            // assume mailing list id
            try {
                CompletableFuture<ObjectNode> listFuture = entityService.getItem(
                    "mailingList",
                    "1",
                    java.util.UUID.fromString((String) mailList)
                );
                ObjectNode listNode = listFuture.get();
                if (listNode != null && listNode.has("recipients")) {
                    ArrayNode arr = (ArrayNode) listNode.get("recipients");
                    for (int i = 0; i < arr.size(); i++) {
                        if (arr.get(i).isTextual()) {
                            String v = arr.get(i).asText();
                            // try to resolve recipient id
                            try {
                                CompletableFuture<ObjectNode> rFuture = entityService.getItem(
                                    "recipient",
                                    "1",
                                    java.util.UUID.fromString(v)
                                );
                                ObjectNode rNode = rFuture.get();
                                if (rNode != null) {
                                    Recipient r = new Recipient();
                                    r.setId(rNode.has("id") ? rNode.get("id").asText() : null);
                                    r.setTechnicalId(rNode.has("technicalId") ? rNode.get("technicalId").asText() : null);
                                    r.setEmail(rNode.has("email") ? rNode.get("email").asText() : null);
                                    result.add(r);
                                }
                            } catch (Exception ex) {
                                // treat as inline email
                                Recipient r = new Recipient();
                                r.setEmail(v);
                                result.add(r);
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                logger.error("Error resolving mailing list", ex);
            }
        } else if (mailList instanceof java.util.List) {
            List<?> arr = (List<?>) mailList;
            for (Object o : arr) {
                if (o instanceof String) {
                    String v = (String) o;
                    // attempt to fetch recipient by id
                    try {
                        CompletableFuture<ObjectNode> rFuture = entityService.getItem(
                            "recipient",
                            "1",
                            java.util.UUID.fromString(v)
                        );
                        ObjectNode rNode = rFuture.get();
                        if (rNode != null) {
                            Recipient r = new Recipient();
                            r.setId(rNode.has("id") ? rNode.get("id").asText() : null);
                            r.setTechnicalId(rNode.has("technicalId") ? rNode.get("technicalId").asText() : null);
                            r.setEmail(rNode.has("email") ? rNode.get("email").asText() : null);
                            result.add(r);
                        }
                    } catch (Exception ex) {
                        // inline email
                        Recipient r = new Recipient();
                        r.setEmail(v);
                        result.add(r);
                    }
                }
            }
        }
        return result;
    }

    private List<Recipient> filterRecipients(List<Recipient> resolved, Mail mail) {
        Set<String> seen = new HashSet<>();
        List<Recipient> out = new ArrayList<>();
        for (Recipient r : resolved) {
            if (r.getEmail() == null) continue;
            String email = r.getEmail().toLowerCase();
            if (seen.contains(email)) continue;
            seen.add(email);
            // basic opt-out and status checks
            if (r.getPreferences() != null && Boolean.TRUE.equals(r.getPreferences().getOptOut())) continue;
            String status = r.getStatus();
            if (status != null && ("OPTED_OUT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status))) continue;
            // daily limit check
            Integer daily = r.getPreferences() != null ? r.getPreferences().getDailyLimit() : null;
            if (daily != null && daily <= 0) continue;
            out.add(r);
        }
        return out;
    }
}
