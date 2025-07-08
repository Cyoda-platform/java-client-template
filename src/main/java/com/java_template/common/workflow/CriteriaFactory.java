package com.java_template.common.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for managing CriteriaChecker beans.
 * Automatically discovers and registers all CriteriaChecker beans on construction.
 * Supports filtering criteria by ModelKey (entity name + version) for sophisticated selection.
 */
@Component
public class CriteriaFactory {

    private static final Logger logger = LoggerFactory.getLogger(CriteriaFactory.class);

    private final Map<String, List<CriteriaChecker>> criteriaByName = new ConcurrentHashMap<>();

    /**
     * Constructor that automatically discovers and registers all CriteriaChecker beans.
     *
     * @param criteriaBeans Map of all CriteriaChecker beans from Spring context
     */
    public CriteriaFactory(Map<String, CriteriaChecker> criteriaBeans) {
        logger.info("Initializing CriteriaFactory with {} criteria beans", criteriaBeans.size());

        for (Map.Entry<String, CriteriaChecker> entry : criteriaBeans.entrySet()) {

            CriteriaChecker criteriaChecker = entry.getValue();
            String criteriaName = criteriaChecker.getName();
            // Register by criteria name (allowing multiple checkers with same name)
            criteriaByName.computeIfAbsent(criteriaName, k -> new ArrayList<>()).add(criteriaChecker);
        }
    }

    /**
     * Gets a criteria checker by name, but only if it supports the given ModelKey.
     * If multiple criteria checkers have the same name, returns the first one that supports the ModelKey.
     * This ensures that criteria selection respects both ModelKey compatibility and name matching.
     *
     * @param criteriaName the name of the criteria checker (from criteriaChecker.getName())
     * @param modelKey     the ModelKey that must be supported
     * @return the first criteria checker with this name that supports the ModelKey, null if none found
     */
    public CriteriaChecker getCriteriaForModel(String criteriaName, ModelKey modelKey) {
        List<CriteriaChecker> candidateCheckers = criteriaByName.get(criteriaName);

        if (candidateCheckers == null || candidateCheckers.isEmpty()) {
            logger.debug("No criteria checkers found for name '{}'", criteriaName);
            return null;
        }

        // Find the first criteria checker that supports the ModelKey
        for (CriteriaChecker criteriaChecker : candidateCheckers) {
            if (criteriaChecker.supports(modelKey)) {
                logger.debug("Selected criteria checker '{}' (class: {}) for ModelKey {}",
                        criteriaName, criteriaChecker.getClass().getSimpleName(), modelKey);
                return criteriaChecker;
            }
        }

        logger.debug("No criteria checker named '{}' supports ModelKey {}. Found {} checkers with this name.",
                criteriaName, modelKey, candidateCheckers.size());
        return null;
    }
}
