package com.java_template.application.processor;

import com.java_template.application.entity.report.version_1.Report;
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
import com.java_template.common.dto.EntityResponse;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ReportCalculationProcessor implements CyodaProcessor {

    private static final Logger logger = LoggerFactory.getLogger(ReportCalculationProcessor.class);
    private final String className = this.getClass().getSimpleName();
    private final ProcessorSerializer serializer;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private EntityService entityService;

    public ReportCalculationProcessor(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultProcessorSerializer();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public EntityProcessorCalculationResponse process(CyodaEventContext<EntityProcessorCalculationRequest> context) {
        EntityProcessorCalculationRequest request = context.getEvent();
        logger.info("Processing Report calculation for request: {}", request.getId());

        return serializer.withRequest(request)
            .toEntity(Report.class)
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

    private boolean isValidEntity(Report entity) {
        return entity != null;
    }

    private Report processEntityLogic(ProcessorSerializer.ProcessorEntityExecutionContext<Report> context) {
        Report entity = context.entity();
        
        try {
            // Fetch all processed bookings
            List<EntityResponse<Booking>> allBookings = entityService.findAll(Booking.class);
            List<EntityResponse<Booking>> processedBookings = allBookings.stream()
                .filter(bookingResponse -> "processed".equals(bookingResponse.getMetadata().getState()))
                .collect(Collectors.toList());
            
            // Apply filters if they exist (same logic as ReportFilterProcessor)
            List<EntityResponse<Booking>> filteredBookings = processedBookings;
            if (entity.getFilterCriteria() != null && !entity.getFilterCriteria().trim().isEmpty()) {
                final Map<String, Object> filters = objectMapper.readValue(entity.getFilterCriteria(), new TypeReference<Map<String, Object>>() {});
                filteredBookings = processedBookings.stream()
                    .filter(bookingResponse -> applyFilters(bookingResponse.getData(), filters))
                    .collect(Collectors.toList());
            }
            
            // Calculate metrics
            BigDecimal totalRevenue = BigDecimal.ZERO;
            int depositPaidCount = 0;
            int depositUnpaidCount = 0;
            
            for (EntityResponse<Booking> bookingResponse : filteredBookings) {
                Booking booking = bookingResponse.getData();
                totalRevenue = totalRevenue.add(BigDecimal.valueOf(booking.getTotalprice()));
                
                if (booking.getDepositpaid()) {
                    depositPaidCount++;
                } else {
                    depositUnpaidCount++;
                }
            }
            
            // Set calculated metrics
            entity.setTotalBookings(filteredBookings.size());
            entity.setTotalRevenue(totalRevenue);
            entity.setDepositPaidCount(depositPaidCount);
            entity.setDepositUnpaidCount(depositUnpaidCount);
            
            // Calculate average price
            if (entity.getTotalBookings() > 0) {
                entity.setAveragePrice(totalRevenue.divide(BigDecimal.valueOf(entity.getTotalBookings()), 2, RoundingMode.HALF_UP));
            } else {
                entity.setAveragePrice(BigDecimal.ZERO);
            }
            
            // Set generation metadata
            entity.setGeneratedAt(LocalDateTime.now());
            entity.setGeneratedBy("system");
            
            logger.info("Calculated metrics for report {}: Revenue={}, Average={}, Bookings={}", 
                entity.getReportId(), entity.getTotalRevenue(), entity.getAveragePrice(), entity.getTotalBookings());
            
        } catch (Exception e) {
            logger.error("Failed to calculate metrics for report {}: {}", entity.getReportId(), e.getMessage(), e);
            throw new RuntimeException("Report calculation failed: " + e.getMessage(), e);
        }

        return entity;
    }
    
    private boolean applyFilters(Booking booking, Map<String, Object> filters) {
        // Apply date range filter
        if (filters.containsKey("dateFrom")) {
            LocalDate dateFrom = LocalDate.parse((String) filters.get("dateFrom"));
            if (booking.getCheckin().isBefore(dateFrom)) {
                return false;
            }
        }
        
        if (filters.containsKey("dateTo")) {
            LocalDate dateTo = LocalDate.parse((String) filters.get("dateTo"));
            if (booking.getCheckout().isAfter(dateTo)) {
                return false;
            }
        }
        
        // Apply price filters
        if (filters.containsKey("minPrice")) {
            Integer minPrice = (Integer) filters.get("minPrice");
            if (booking.getTotalprice() < minPrice) {
                return false;
            }
        }
        
        if (filters.containsKey("maxPrice")) {
            Integer maxPrice = (Integer) filters.get("maxPrice");
            if (booking.getTotalprice() > maxPrice) {
                return false;
            }
        }
        
        // Apply deposit filter
        if (filters.containsKey("depositpaid")) {
            Boolean depositpaid = (Boolean) filters.get("depositpaid");
            if (!booking.getDepositpaid().equals(depositpaid)) {
                return false;
            }
        }
        
        // Apply name filters
        if (filters.containsKey("firstname")) {
            String firstname = (String) filters.get("firstname");
            if (!booking.getFirstname().toLowerCase().contains(firstname.toLowerCase())) {
                return false;
            }
        }
        
        return true;
    }
}
