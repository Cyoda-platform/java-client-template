# HackerNewsItem Criteria Requirements

## Overview
Criteria are pure functions that evaluate conditions for HackerNewsItem entities without side effects. They are used in workflow transitions to determine the next state based on entity data and processing results.

## Criteria List

### 1. ValidationFailedCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: validation_failed (pending_validation → failed)
**Purpose**: Check if validation has failed for a HackerNewsItem
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> that has undergone validation processing

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    // Check for validation failure indicators
    IF item.getId() == null OR item.getId() <= 0 THEN
        RETURN true  // Validation failed - invalid ID
    END IF
    
    IF item.getType() == null THEN
        RETURN true  // Validation failed - missing type
    END IF
    
    validTypes = ["job", "story", "comment", "poll", "pollopt"]
    IF !validTypes.contains(item.getType()) THEN
        RETURN true  // Validation failed - invalid type
    END IF
    
    // Check type-specific validation rules
    IF item.getType() == "pollopt" AND item.getPoll() == null THEN
        RETURN true  // Validation failed - pollopt without poll reference
    END IF
    
    IF item.getType() == "poll" AND (item.getParts() == null OR item.getParts().isEmpty()) THEN
        RETURN true  // Validation failed - poll without parts
    END IF
    
    // Check for circular parent reference
    IF item.getParent() != null AND item.getParent().equals(item.getId()) THEN
        RETURN true  // Validation failed - circular reference
    END IF
    
    RETURN false  // Validation passed
END
```

**Expected Output**: 
- true if validation failed
- false if validation passed

### 2. EnrichmentCompleteCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: enrichment_complete (enriching → enriched)
**Purpose**: Check if enrichment from Firebase HN API was successful
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> that has undergone enrichment processing

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    // Basic enrichment success indicators
    IF item.getTime() == null THEN
        RETURN false  // Enrichment incomplete - missing timestamp
    END IF
    
    // Type-specific enrichment checks
    IF item.getType() == "story" THEN
        IF item.getTitle() == null THEN
            RETURN false  // Story should have title after enrichment
        END IF
    END IF
    
    IF item.getType() == "comment" THEN
        IF item.getText() == null AND item.getDeleted() != true THEN
            RETURN false  // Non-deleted comment should have text
        END IF
        IF item.getParent() == null THEN
            RETURN false  // Comment should have parent after enrichment
        END IF
    END IF
    
    IF item.getType() == "job" THEN
        IF item.getTitle() == null THEN
            RETURN false  // Job should have title after enrichment
        END IF
    END IF
    
    IF item.getType() == "poll" THEN
        IF item.getTitle() == null THEN
            RETURN false  // Poll should have title after enrichment
        END IF
        IF item.getParts() == null OR item.getParts().isEmpty() THEN
            RETURN false  // Poll should have parts after enrichment
        END IF
    END IF
    
    IF item.getType() == "pollopt" THEN
        IF item.getText() == null THEN
            RETURN false  // Poll option should have text after enrichment
        END IF
        IF item.getPoll() == null THEN
            RETURN false  // Poll option should reference poll after enrichment
        END IF
    END IF
    
    RETURN true  // Enrichment appears complete
END
```

**Expected Output**: 
- true if enrichment completed successfully
- false if enrichment is incomplete

### 3. EnrichmentFailedCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: enrichment_failed (enriching → failed)
**Purpose**: Check if enrichment from Firebase HN API has failed
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> that has undergone enrichment processing

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    metadata = entityWithMetadata.getMeta()
    
    // Check for enrichment failure indicators in metadata
    IF metadata.getErrorMessage() != null THEN
        IF metadata.getErrorMessage().contains("EnrichmentException") THEN
            RETURN true  // Explicit enrichment failure
        END IF
        IF metadata.getErrorMessage().contains("API") THEN
            RETURN true  // API-related failure
        END IF
    END IF
    
    // Check for signs of failed enrichment
    IF item.getTime() == null AND item.getDeleted() != true THEN
        // Most items should have timestamp unless deleted
        RETURN true
    END IF
    
    // Check if item appears to be a stub (minimal data suggests fetch failure)
    nonNullFields = 0
    IF item.getBy() != null THEN nonNullFields++
    IF item.getText() != null THEN nonNullFields++
    IF item.getTitle() != null THEN nonNullFields++
    IF item.getUrl() != null THEN nonNullFields++
    IF item.getScore() != null THEN nonNullFields++
    IF item.getKids() != null THEN nonNullFields++
    IF item.getParent() != null THEN nonNullFields++
    IF item.getDescendants() != null THEN nonNullFields++
    
    // If very few fields are populated, enrichment likely failed
    IF nonNullFields < 2 AND item.getDeleted() != true THEN
        RETURN true
    END IF
    
    RETURN false  // No clear signs of enrichment failure
END
```

**Expected Output**: 
- true if enrichment failed
- false if enrichment did not fail

### 4. ItemTypeValidCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: Used in various validation contexts
**Purpose**: Check if the item type is valid for Hacker News items
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with type field to validate

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    IF item.getType() == null THEN
        RETURN false  // Type is required
    END IF
    
    validTypes = ["job", "story", "comment", "poll", "pollopt"]
    RETURN validTypes.contains(item.getType())
END
```

**Expected Output**: 
- true if type is valid
- false if type is invalid or null

### 5. ItemHasChildrenCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: Used for search and hierarchy operations
**Purpose**: Check if the item has child comments
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> to check for children

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    IF item.getKids() == null THEN
        RETURN false  // No children array
    END IF
    
    IF item.getKids().isEmpty() THEN
        RETURN false  // Empty children array
    END IF
    
    RETURN true  // Has children
END
```

**Expected Output**: 
- true if item has children
- false if item has no children

### 6. ItemIsTopLevelCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: Used for search and hierarchy operations
**Purpose**: Check if the item is a top-level item (no parent)
**Type**: Pure function evaluation

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> to check hierarchy level

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    RETURN item.getParent() == null
END
```

**Expected Output**: 
- true if item is top-level (no parent)
- false if item has a parent

### 7. ItemScoreThresholdCriterion

**Entity**: HackerNewsItem
**Workflow Transition**: Used for filtering and search operations
**Purpose**: Check if item score meets a specified threshold
**Type**: Pure function evaluation with configuration

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with score to check
- Configuration parameter for threshold value

**Check Method Logic**:
```
BEGIN check(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    threshold = getConfigurationThreshold()  // From criterion configuration
    
    IF item.getScore() == null THEN
        RETURN false  // No score to evaluate
    END IF
    
    RETURN item.getScore() >= threshold
END
```

**Expected Output**: 
- true if score meets or exceeds threshold
- false if score is below threshold or null
