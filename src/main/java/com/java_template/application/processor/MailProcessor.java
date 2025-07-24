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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
                .map(this::processMailLogic)
                .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return "MailProcessor".equals(modelSpec.operationName()) &&
                "mail".equalsIgnoreCase(modelSpec.modelKey().getName()) &&
                Integer.parseInt(Config.ENTITY_VERSION) == modelSpec.modelKey().getVersion();
    }

    private Mail processMailLogic(Mail mail) {
        logger.info("Starting processing logic for Mail entity");

        // Evaluate mail content for happiness
        boolean isHappy = evaluateMailContentForHappiness(mail.getContent());
        mail.setIsHappy(isHappy);

        try {
            if (isHappy) {
                sendHappyMail(mail);
                mail.setStatus("SENT_HAPPY");
                logger.info("Mail sent as happy mail");
            } else {
                sendGloomyMail(mail);
                mail.setStatus("SENT_GLOOMY");
                logger.info("Mail sent as gloomy mail");
            }
        } catch (Exception e) {
            mail.setStatus("FAILED");
            logger.error("Failed to send mail: {}", e.getMessage());
        }

        logger.info("Mail processing completed with status {} and isHappy {}", mail.getStatus(), mail.getIsHappy());
        return mail;
    }

    private boolean evaluateMailContentForHappiness(String content) {
        if (content == null) {
            return false;
        }
        String lowerContent = content.toLowerCase(Locale.ROOT);
        List<String> positiveKeywords = Arrays.asList("happy", "wonderful", "great", "joy", "love");
        for (String keyword : positiveKeywords) {
            if (lowerContent.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void sendHappyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending HAPPY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }

    private void sendGloomyMail(Mail mail) {
        for (String recipient : mail.getMailList()) {
            logger.info("Sending GLOOMY mail to {}", recipient);
            // Real mail sending logic would go here
        }
    }
}
