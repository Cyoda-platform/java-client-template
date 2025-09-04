package com.java_template.application.processor;

import com.java_template.application.entity.booking.version_1.Booking;
import com.java_template.common.serializer.ProcessorSerializer;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.serializer.ErrorInfo;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.CyodaProcessor;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityProcessorCalculationResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.cyoda.cloud.api.event.common.DataPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java_template.common.service.EntityService;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Component
public class BookingFetchProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BookingFetchProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final RestTemplate restTemplate;

    public BookingFetchProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.restTemplate = new RestTemplate();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Booking for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Booking.class)
            .withErrorHandler((error, entity) -> {
                logger.error("Failed to extract entity: {}", error.getMessage(), error);
                return new ErrorInfo("TO_ENTITY_ERROR", "Failed to extract entity: " + error.getMessage());
            })
            .validate(this::isValidEntity, "Invalid entity state")
            .map(this::processEntityLogic)
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification modelSpec) {
        return className.equalsIgnoreCase(modelSpec.operationName());
    }

    private boolean isValidEntity(Booking entity) {
        return entity != null && entity.getBookingId() != null;
    }

    private Booking processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Booking> context) {
        Booking entity = context.entity();
        
        try {
            String apiUrl = "https://restful-booker.herokuapp.com/booking/" + entity.getBookingId();
            logger.info("Fetching booking from API: {}", apiUrl);
            
            // Make HTTP GET request to Restful Booker API
            Map<String, Object> responseData = restTemplate.getForObject(apiUrl, Map.class);
            
            if (responseData != null) {
                // Populate booking entity with API response data
                entity.setFirstname((String) responseData.get("firstname"));
                entity.setLastname((String) responseData.get("lastname"));
                entity.setTotalprice((Integer) responseData.get("totalprice"));
                entity.setDepositpaid((Boolean) responseData.get("depositpaid"));
                entity.setAdditionalneeds((String) responseData.get("additionalneeds"));
                
                // Parse booking dates
                Map<String, String> bookingDates = (Map<String, String>) responseData.get("bookingdates");
                if (bookingDates != null) {
                    entity.setCheckin(LocalDate.parse(bookingDates.get("checkin")));
                    entity.setCheckout(LocalDate.parse(bookingDates.get("checkout")));
                }
                
                // Set retrieval timestamp
                entity.setRetrievedAt(LocalDateTime.now());
                
                logger.info("Successfully fetched booking {}", entity.getBookingId());
            } else {
                logger.error("Received null response for booking {}", entity.getBookingId());
                throw new RuntimeException("Failed to fetch booking data from API");
            }
            
        } catch (RestClientException e) {
            logger.error("Exception fetching booking {}: {}", entity.getBookingId(), e.getMessage(), e);
            throw new RuntimeException("Failed to fetch booking from API: " + e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Unexpected exception fetching booking {}: {}", entity.getBookingId(), e.getMessage(), e);
            throw new RuntimeException("Unexpected error during booking fetch: " + e.getMessage(), e);
        }

        return entity;
    }
}
