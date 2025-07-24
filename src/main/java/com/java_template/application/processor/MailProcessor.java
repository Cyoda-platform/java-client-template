package com.java_template.application.processor;

import com.java_template.application.entity.Mail;
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

import java.util.HashMap;
import java.util.Map;

@Component
public class MailProcessor implements CyodaProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ProcessorSerializer serializer;

    public MailProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        logger.info("MailProcessor initialized with SerializerFactory");
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Mail for request: {}", request.getId());

        return serializer.withRequest(request)
                .toEntity(Mail.class)
                .validate(Mail::isValid)
                .map(this::processMail)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
               "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
               Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processMail(Mail mail) {
        logger.info("Processing Mail with technicalId: {}", mail.getTechnicalId());
        Map<String, String> criteriaResults = new HashMap<>();
        int happyCount = 0;
        int gloomyCount = 0;
        for (int i = 1; i <= 22; i++) {
            String result = (i % 2 == 0) ? "isHappy" : "isGloomy";
            criteriaResults.put("criteria" + i, result);
            if ("isHappy".equals(result)) happyCount++;
            else gloomyCount++;
        }
        mail.setCriteriaResults(criteriaResults);

        if (happyCount > gloomyCount) {
            mail.setIsHappy(true);
            mail.setIsGloomy(false);
            mail.setStatus("PROCESSING");
        } else {
            mail.setIsHappy(false);
            mail.setIsGloomy(true);
            mail.setStatus("PROCESSING");
        }

        boolean sendSuccess = true;
        if (mail.getIsHappy() != null && mail.getIsHappy()) {
            logger.info("Sending happy mail to recipients: {}", mail.getMailList());
            mail.setStatus("SENT_HAPPY");
        } else {
            logger.info("Sending gloomy mail to recipients: {}", mail.getMailList());
            mail.setStatus("SENT_GLOOMY");
        }
        if (!sendSuccess) {
            mail.setStatus("FAILED");
            logger.error("Failed to send mail with technicalId: {}", mail.getTechnicalId());
        }
        return mail;
    }
}
