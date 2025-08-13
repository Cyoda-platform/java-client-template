package com.java_template.common.workflow;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OperationFactory {

    private static final Logger log = LoggerFactory.getLogger(OperationFactory.class);

    private final List<CyodaProcessor> processors;
    private final List<CyodaCriterion> criteria;

    public OperationFactory(
            List<CyodaProcessor> processorBeans,
            List<CyodaCriterion> criteriaBeans
    ) {
        log.debug(
                "Initializing OperationFactory with {} processor beans",
                processorBeans.size()
        );
        this.processors = processorBeans;
        log.debug(
                "Initializing OperationFactory with {} criteria beans",
                criteriaBeans.size()
        );
        this.criteria = criteriaBeans;
    }

    public @NotNull CyodaProcessor getProcessorForModel(final OperationSpecification.Processor opsSpec) {
        log.debug(
                "Searching for processor for OperationSpecification {}",
                opsSpec
        );

        final var matchedProcessors = processors.stream()
                .filter(p -> p.supports(opsSpec))
                .toList();

        if (matchedProcessors.isEmpty()) {
            throw new IllegalStateException("No processor found for OperationSpecificationfor OperationSpecification " + opsSpec);
        }

        if (matchedProcessors.size() > 1) {
            log.warn(
                    "For OperationSpecification {} found {} processors which is seems as an app configuration issue",
                    opsSpec,
                    matchedProcessors.stream().map(CyodaProcessor::getClass)
            );
        }

        final var matchedProcessor = matchedProcessors.getFirst();
        log.info(
                "For OperationSpecification {} selected {} processor",
                opsSpec,
                matchedProcessor.getClass()
        );
        return matchedProcessor;
    }

    public @NotNull CyodaCriterion getCriteriaForModel(final OperationSpecification.Criterion opsSpec) {
        log.debug(
                "Searching for criteria for OperationSpecification {}",
                opsSpec
        );

        final var matchedCriteria = criteria.stream()
                .filter(p -> p.supports(opsSpec))
                .toList();

        if (matchedCriteria.isEmpty()) {
            throw new IllegalStateException("No criterion found for OperationSpecificationfor OperationSpecification " + opsSpec);
        }

        if (matchedCriteria.size() > 1) {
            log.warn(
                    "For OperationSpecification {} found {} criteria which is seems as an app configuration issue",
                    opsSpec,
                    matchedCriteria.stream().map(CyodaCriterion::getClass)
            );
        }

        final var matchedCriterion = matchedCriteria.getFirst();
        log.info(
                "For OperationSpecification {} selected {} criterion",
                opsSpec,
                matchedCriterion.getClass()
        );
        return matchedCriterion;
    }
}
