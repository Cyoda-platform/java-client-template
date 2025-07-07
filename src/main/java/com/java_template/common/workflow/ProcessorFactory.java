package com.java_template.common.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for managing CyodaProcessor beans.
 * Automatically discovers and registers all CyodaProcessor beans on construction.
 * Handles generic processors and their entity type resolution.
 */
@Component
public class ProcessorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorFactory.class);

    private final Map<String, CyodaProcessor<? extends CyodaEntity>> processors = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends CyodaEntity>> processorEntityTypes = new ConcurrentHashMap<>();

    /**
     * Constructor that automatically discovers and registers all CyodaProcessor beans.
     * @param processorBeans Map of all CyodaProcessor beans from Spring context
     */
    @SuppressWarnings("unchecked")
    public ProcessorFactory(Map<String, CyodaProcessor> processorBeans) {
        logger.info("Initializing ProcessorFactory with {} processor beans", processorBeans.size());

        for (Map.Entry<String, CyodaProcessor> entry : processorBeans.entrySet()) {
            String beanName = entry.getKey();
            CyodaProcessor<? extends CyodaEntity> processor = entry.getValue();

            processors.put(beanName, processor);

            // Store the entity type for this processor
            Class<? extends CyodaEntity> entityType = processor.getEntityType();
            processorEntityTypes.put(beanName, entityType);

            logger.debug("Registered processor: {} for entity type: {}", beanName, entityType.getSimpleName());
        }

        logger.info("ProcessorFactory initialized with {} processors: {}",
                   processors.size(), processors.keySet());
    }

    /**
     * Gets a processor by name.
     * @param processorName the name of the processor (bean name)
     * @return the processor, or null if not found
     */
    public CyodaProcessor<? extends CyodaEntity> getProcessor(String processorName) {
        return processors.get(processorName);
    }

    /**
     * Gets the entity type for a processor.
     * @param processorName the name of the processor (bean name)
     * @return the entity type class, or null if processor not found
     */
    public Class<? extends CyodaEntity> getEntityType(String processorName) {
        return processorEntityTypes.get(processorName);
    }

    /**
     * Checks if a processor exists.
     * @param processorName the name of the processor
     * @return true if the processor exists, false otherwise
     */
    public boolean hasProcessor(String processorName) {
        return processors.containsKey(processorName);
    }

    /**
     * Gets all registered processor names.
     * @return array of processor names
     */
    public String[] getRegisteredProcessors() {
        return processors.keySet().toArray(new String[0]);
    }

    /**
     * Gets the number of registered processors.
     * @return number of processors
     */
    public int getProcessorCount() {
        return processors.size();
    }
}
