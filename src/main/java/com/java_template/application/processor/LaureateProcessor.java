package com.java_template.application.processor;

import com.java_template.application.entity.Laureate;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Date;

@Component
public class LaureateProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(LaureateProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;

    public LaureateProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Laureate for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Laureate.class)
            .validate(this::isValidEntity, "Invalid laureate entity")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Laureate laureate) {
        return laureate != null && laureate.getLaureateId() != null;
    }

    private Laureate processEntityLogic(Laureate laureate) {
        // Enrich laureate data: calculate age if born and died dates present
        if (laureate.getBorn() != null) {
            Integer age = null;
            if (laureate.getDied() != null) {
                age = calculateAge(laureate.getBorn(), laureate.getDied());
            } else {
                age = calculateAge(laureate.getBorn(), new Date());
            }
            laureate.setAge(age);
        }
        // Normalize country code to uppercase if present
        if (laureate.getBorncountrycode() != null) {
            laureate.setBorncountrycode(laureate.getBorncountrycode().toUpperCase());
        }
        return laureate;
    }

    private Integer calculateAge(Date birthDate, Date endDate) {
        LocalDate birthLocalDate = birthDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate endLocalDate = endDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        if (birthLocalDate.isAfter(endLocalDate)) {
            return null;
        }
        return Period.between(birthLocalDate, endLocalDate).getYears();
    }
}
