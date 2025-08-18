package com.java_template.application.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.java_template.application.entity.mail.version_1.Mail;
import com.java_template.application.entity.recipient.version_1.Recipient;
import com.java_template.application.entity.deliveryRecord.version_1.DeliveryRecord;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.util.Condition;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.service.EntityService;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.java_template.common.config.Config.*;

@Component
public class SendHappyMailProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(SendHappyMailProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final EntityService entityService;
    private final int defaultDailyLimit = 100;
    private final int maxAttempts = 3;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SendHappyMailProcessor(SerializerFactory serializerFactory, EntityService entityService) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for happy send request: {}", request.getId());

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
                dr.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
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
            mail.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC).toString());
            logger.info("Happy mail queued with {} recipients", records.size());
        } catch (Exception ex) {
            logger.error("Error in send happy mail processor", ex);
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
                                    Recipient r = buildRecipientFromNode(rNode);
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
                            Recipient r = buildRecipientFromNode(rNode);
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

    @SuppressWarnings("unchecked")
    private Recipient buildRecipientFromNode(ObjectNode rNode) {
        Recipient r = new Recipient();
        r.setId(rNode.has("id") ? rNode.get("id").asText() : null);
        r.setTechnicalId(rNode.has("technicalId") ? rNode.get("technicalId").asText() : null);
        r.setEmail(rNode.has("email") ? rNode.get("email").asText() : null);
        r.setStatus(rNode.has("status") ? rNode.get("status").asText() : null);
        if (rNode.has("preferences") && !rNode.get("preferences").isNull()) {
            try {
                Map<String, Object> prefs = objectMapper.convertValue(rNode.get("preferences"), Map.class);
                r.setPreferences(prefs);
            } catch (Exception ex) {
                logger.warn("Unable to parse recipient preferences: {}", ex.getMessage());
            }
        } else {
            r.setPreferences(null);
        }
        return r;
    }

    private List<Recipient> filterRecipients(List<Recipient> resolved, Mail mail) {
        Set<String> seen = new HashSet<>();
        List<Recipient> out = new ArrayList<>();
        String mailCategory = null;
        if (mail.getMeta() != null) {
            Object cat = mail.getMeta().get("category");
            if (cat instanceof String) mailCategory = (String) cat;
        }
        for (Recipient r : resolved) {
            if (r.getEmail() == null) continue;
            String email = r.getEmail().toLowerCase();
            if (seen.contains(email)) continue;
            seen.add(email);
            // basic opt-out and status checks
            if (r.getPreferences() != null) {
                try {
                    Object opt = r.getPreferences().get("optOut");
                    if (opt instanceof Boolean && Boolean.TRUE.equals(opt)) continue;
                } catch (Exception ignore) { }
            }
            String status = r.getStatus();
            if (status != null && ("OPTED_OUT".equalsIgnoreCase(status) || "INVALID".equalsIgnoreCase(status) || "SUSPENDED".equalsIgnoreCase(status))) continue;

            // category filtering: if mail has a category and recipient has allowedCategories, ensure match
            if (mailCategory != null && r.getPreferences() != null) {
                try {
                    Object allowed = r.getPreferences().get("allowedCategories");
                    if (allowed instanceof java.util.List) {
                        java.util.List<?> allowedList = (java.util.List<?>) allowed;
                        if (!allowedList.isEmpty() && !allowedList.contains(mailCategory)) continue;
                    }
                } catch (Exception ignore) { }
            }

            // daily limit check - lookup today's deliveries
            Integer dailyLimit = null;
            if (r.getPreferences() != null) {
                try {
                    Object d = r.getPreferences().get("dailyLimit");
                    if (d instanceof Integer) dailyLimit = (Integer) d;
                    else if (d instanceof Number) dailyLimit = ((Number) d).intValue();
                } catch (Exception ignore) { }
            }
            if (dailyLimit == null) dailyLimit = defaultDailyLimit;

            try {
                // build condition to fetch delivery records for recipient
                SearchConditionRequest condition = SearchConditionRequest.group("AND", Condition.of("$.recipientEmail", "IEQUALS", r.getEmail()));
                CompletableFuture<ArrayNode> itemsFuture = entityService.getItemsByCondition(
                    DeliveryRecord.ENTITY_NAME,
                    String.valueOf(DeliveryRecord.ENTITY_VERSION),
                    condition,
                    true
                );
                ArrayNode items = itemsFuture.get();
                int todaysCount = 0;
                if (items != null) {
                    String todayPrefix = OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE);
                    for (int i = 0; i < items.size(); i++) {
                        ObjectNode node = (ObjectNode) items.get(i);
                        if (node.has("createdAt") && node.get("createdAt").asText().startsWith(todayPrefix)) {
                            todaysCount++;
                        }
                    }
                }
                if (todaysCount >= dailyLimit) {
                    logger.info("Recipient {} reached daily limit {} (today {})", r.getEmail(), dailyLimit, todaysCount);
                    continue;
                }
            } catch (Exception ex) {
                logger.warn("Unable to evaluate daily limit for recipient {}: {}", r.getEmail(), ex.getMessage());
                // allow recipient if delivery history not available
            }

            out.add(r);
        }
        return out;
    }
}
