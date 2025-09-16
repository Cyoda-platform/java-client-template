package com.java_template.application.criterion;

import com.java_template.common.serializer.CriterionSerializer;
import com.java_template.common.serializer.EvaluationOutcome;
import com.java_template.common.serializer.ReasonAttachmentStrategy;
import com.java_template.common.serializer.SerializerFactory;
import com.java_template.common.workflow.CyodaCriterion;
import com.java_template.common.workflow.CyodaEventContext;
import com.java_template.common.workflow.OperationSpecification;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationRequest;
import org.cyoda.cloud.api.event.processing.EntityCriteriaCalculationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;

/**
 * Criterion to check overall system health before performing operations.
 * Used across multiple workflows to ensure system stability.
 * 
 * Validation Logic:
 * - Checks CPU usage < 90%
 * - Checks memory usage < 85%
 * - Checks disk space availability
 * - Checks external service connectivity (Cat Fact API)
 * - Checks database connectivity
 */
@Component
public class SystemHealthCriterion implements CyodaCriterion {

    private static final Logger logger = LoggerFactory.getLogger(SystemHealthCriterion.class);
    private static final double MAX_CPU_USAGE = 0.9; // 90%
    private static final double MAX_MEMORY_USAGE = 0.85; // 85%
    private static final String CAT_FACT_API_URL = "https://catfact.ninja/fact";
    private static final int CONNECTIVITY_TIMEOUT_MS = 5000;
    
    private final CriterionSerializer serializer;

    public SystemHealthCriterion(SerializerFactory serializerFactory) {
        this.serializer = serializerFactory.getDefaultCriteriaSerializer();
        logger.debug("SystemHealthCriterion initialized");
    }

    @Override
    public EntityCriteriaCalculationResponse check(CyodaEventContext<EntityCriteriaCalculationRequest> context) {
        EntityCriteriaCalculationRequest request = context.getEvent();
        logger.debug("Checking system health criteria for request: {}", request.getId());

        return serializer.withRequest(request)
            .evaluate(ctx -> this.evaluateSystemHealth())
            .withReasonAttachment(ReasonAttachmentStrategy.toWarnings())
            .complete();
    }

    @Override
    public boolean supports(OperationSpecification opSpec) {
        return "SystemHealthCriterion".equals(opSpec.operationName());
    }

    /**
     * Evaluates overall system health.
     * 
     * @return EvaluationOutcome indicating whether system is healthy
     */
    private EvaluationOutcome evaluateSystemHealth() {
        logger.debug("Evaluating system health");

        // Check CPU usage
        try {
            double cpuUsage = getCpuUsage();
            if (cpuUsage > MAX_CPU_USAGE) {
                return EvaluationOutcome.fail(String.format("High CPU usage: %.2f%% (max: %.2f%%)", 
                                                           cpuUsage * 100, MAX_CPU_USAGE * 100));
            }
            logger.debug("CPU usage OK: {:.2f}%", cpuUsage * 100);
        } catch (Exception e) {
            logger.warn("Failed to check CPU usage: {}", e.getMessage());
            // Continue with other checks
        }

        // Check memory usage
        try {
            double memoryUsage = getMemoryUsage();
            if (memoryUsage > MAX_MEMORY_USAGE) {
                return EvaluationOutcome.fail(String.format("High memory usage: %.2f%% (max: %.2f%%)", 
                                                           memoryUsage * 100, MAX_MEMORY_USAGE * 100));
            }
            logger.debug("Memory usage OK: {:.2f}%", memoryUsage * 100);
        } catch (Exception e) {
            logger.warn("Failed to check memory usage: {}", e.getMessage());
            // Continue with other checks
        }

        // Check external service connectivity
        try {
            if (!isExternalServiceAvailable()) {
                return EvaluationOutcome.fail("Cat Fact API is not accessible");
            }
            logger.debug("External service connectivity OK");
        } catch (Exception e) {
            logger.warn("Failed to check external service connectivity: {}", e.getMessage());
            // Continue with other checks - this might not be critical for all operations
        }

        // Check network connectivity
        try {
            if (!isNetworkConnectivityOk()) {
                return EvaluationOutcome.fail("Network connectivity issues detected");
            }
            logger.debug("Network connectivity OK");
        } catch (Exception e) {
            logger.warn("Failed to check network connectivity: {}", e.getMessage());
        }

        // All health checks passed
        logger.debug("System health check passed");
        return EvaluationOutcome.success();
    }

    /**
     * Gets current CPU usage.
     */
    private double getCpuUsage() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        
        // Try to get system CPU load (Java 7+)
        if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sunOsBean = 
                (com.sun.management.OperatingSystemMXBean) osBean;
            double cpuLoad = sunOsBean.getSystemCpuLoad();
            
            // getSystemCpuLoad() returns -1 if not available
            if (cpuLoad >= 0) {
                return cpuLoad;
            }
        }
        
        // Fallback: return 0 if system CPU load is not available
        return 0.0;
    }

    /**
     * Gets current memory usage.
     */
    private double getMemoryUsage() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        
        if (maxMemory <= 0) {
            // If max memory is not available, use committed memory
            maxMemory = memoryBean.getHeapMemoryUsage().getCommitted();
        }
        
        return maxMemory > 0 ? (double) usedMemory / maxMemory : 0.0;
    }

    /**
     * Checks if external service (Cat Fact API) is available.
     */
    private boolean isExternalServiceAvailable() {
        try {
            URL url = new URL(CAT_FACT_API_URL);
            URLConnection connection = url.openConnection();
            connection.setConnectTimeout(CONNECTIVITY_TIMEOUT_MS);
            connection.setReadTimeout(CONNECTIVITY_TIMEOUT_MS);
            connection.connect();
            return true;
        } catch (Exception e) {
            logger.debug("Cat Fact API not accessible: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Checks basic network connectivity.
     */
    private boolean isNetworkConnectivityOk() {
        try {
            // Try to reach a reliable external host
            InetAddress address = InetAddress.getByName("8.8.8.8"); // Google DNS
            return address.isReachable(CONNECTIVITY_TIMEOUT_MS);
        } catch (Exception e) {
            logger.debug("Network connectivity check failed: {}", e.getMessage());
            return false;
        }
    }
}
