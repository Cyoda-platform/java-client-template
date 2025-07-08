package com.java_template.common.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for managing CyodaProcessor beans.
 * Automatically discovers and registers all CyodaProcessor beans on construction.
 * Processors are decoupled from specific entity types and handle ObjectNode payloads.
 * Supports filtering processors by ModelKey (entity name + version) for sophisticated selection.
 */
@Component
public class ProcessorFactory {

    private static final Logger logger = LoggerFactory.getLogger(ProcessorFactory.class);

    private final Map<String, List<CyodaProcessor>> processorsByName = new ConcurrentHashMap<>();

    /**
     * Constructor that automatically discovers and registers all CyodaProcessor beans.
     *
     * @param processorBeans List of all CyodaProcessor beans from Spring context
     */
    public ProcessorFactory(List<CyodaProcessor> processorBeans) {
        logger.info("Initializing ProcessorFactory with {} processor beans", processorBeans.size());

        for (CyodaProcessor processor : processorBeans) {
            String processorName = processor.getName();
            processorsByName.computeIfAbsent(processorName, k -> new ArrayList<>()).add(processor);
        }

        logger.info("ProcessorFactory initialized with {} unique processor names", processorsByName.size());
    }


    /**
     * Gets a processor by name, only if it supports the given ModelKey.
     * Returns the first supporting processor if multiple exist.
     *
     * @param processorName the name of the processor
     * @param modelKey      the model key that must be supported
     * @return supporting processor or null if none found
     */
    public CyodaProcessor getProcessorForModel(String processorName, ModelKey modelKey) {
        List<CyodaProcessor> candidateProcessors = processorsByName.get(processorName);

        if (candidateProcessors == null || candidateProcessors.isEmpty()) {
            logger.debug("No processors found for name '{}'", processorName);
            return null;
        }

        for (CyodaProcessor processor : candidateProcessors) {
            if (processor.supports(modelKey)) {
                logger.debug("Selected processor '{}' (class: {}) for ModelKey {}",
                        processorName, processor.getClass().getSimpleName(), modelKey);
                return processor;
            }
        }

        logger.debug("No processor named '{}' supports ModelKey {}. Found {} processors with this name.",
                processorName, modelKey, candidateProcessors.size());
        return null;
    }

}
