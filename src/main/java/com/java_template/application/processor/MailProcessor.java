package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import com.java_template.common.config.Config;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.java_template.common.service.EntityService;
import com.java_template.common.util.SearchConditionRequest;
import com.java_template.common.util.Condition;
import org.springframework.beans.factory.annotation.Autowired;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;
    private final String className = this.getClass().getSimpleName();

    // Added EntityService and ObjectMapper
    private final EntityService entityService;
    private final ObjectMapper objectMapper;

    @Autowired
    public MailProcessor(SerializerFactory serializerFactory, EntityService entityService, ObjectMapper objectMapper) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.entityService = entityService;
        this.objectMapper = objectMapper;
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(this::isValidEntity, "Invalid entity state")
                .map(this::processEntityLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Mail entity) {
        return entity != null && entity.isValid();
    }

    private EntityProcessorCalculationResponse.ProcessorEntityExecutionContext<Mail> processEntityLogic(EntityProcessorCalculationResponse.ProcessorEntityExecutionContext<Mail> context) {
        Mail mail = context.entity();

        if (mail.getIsHappy() != null && mail.getMailList() != null) {
            if (mail.getIsHappy()) {
                logger.info("Sending happy mail to: {}", mail.getMailList());
                // In real implementation, you would use a mail service here
                // mailService.sendHappyMail(mail.getMailList(), mail.getContentHappy());
                logger.info("Happy mail sent");
            } else {
                logger.info("Sending gloomy mail to: {}", mail.getMailList());
                // In real implementation, you would use a mail service here
                // mailService.sendGloomyMail(mail.getMailList(), mail.getContentGloomy());
                logger.info("Gloomy mail sent");
            }
        } else {
            logger.warn("Mail entity is missing required fields (isHappy or mailList)");
        }

        return context;
    }
}
