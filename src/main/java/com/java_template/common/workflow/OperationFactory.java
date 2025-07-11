package com.java_template.common.workflow;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OperationFactory {

    private static final Logger log = LoggerFactory.getLogger(OperationFactory.class);

    private final List<CyodaProcessor> processors;
    private final List<CyodaCriterion> criteria;

    private final Map<OperationSpecification,CyodaProcessor> processorCache = new ConcurrentHashMap<>();
    private final Map<OperationSpecification,CyodaCriterion> criteriaCache = new ConcurrentHashMap<>();

    public OperationFactory(
            List<CyodaProcessor> processorBeans,
            List<CyodaCriterion> criteriaBeans
    ) {
        log.debug("Initializing OperationFactory with {} processor beans", processorBeans.size());
        this.processors = processorBeans;
        log.debug("Initializing OperationFactory with {} criteria beans", criteriaBeans.size());
        this.criteria = criteriaBeans;
    }

    public @NotNull CyodaProcessor getProcessorForModel(OperationSpecification.Processor opsSpec) {

        return processorCache.computeIfAbsent(opsSpec, k -> {
            log.debug("Searching for processor for OperationSpecification {}", opsSpec);
            for (CyodaProcessor processor : processors) {
                if (processor.supports(opsSpec)) {
                    String processorName = opsSpec.processorName();
                    log.debug("Found processor '{}' (class: {}) for OperationSpecification {}",
                            processorName, processor.getClass().getSimpleName(), opsSpec);
                    return processor;
                }
            }
            throw new IllegalStateException("No processor found for OperationSpecification " + opsSpec);
        });
    }

    public @NotNull CyodaCriterion getCriteriaForModel(OperationSpecification.Criterion opsSpec) {
        return criteriaCache.computeIfAbsent(opsSpec, k -> {
            log.debug("Searching for criteria for OperationSpecification {}", opsSpec);
            for (CyodaCriterion criterion : criteria) {
                if (criterion.supports(opsSpec)) {
                    String criteriaName = opsSpec.criterionName();
                    log.debug("Found criteria '{}' (class: {}) for OperationSpecification {}",
                            criteriaName, criterion.getClass().getSimpleName(), opsSpec);
                    return criterion;
                }
            }
            throw new IllegalStateException("No criteria found for OperationSpecification " + opsSpec);
        });
    }
}
