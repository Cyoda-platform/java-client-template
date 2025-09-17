# HackerNewsItem Processors Requirements

## Overview
Processors handle the business logic for HackerNewsItem entities throughout their workflow lifecycle. Each processor is responsible for specific operations like validation, enrichment, and storage.

## Processor List

### 1. ValidateHackerNewsItemProcessor

**Entity**: HackerNewsItem
**Workflow Transition**: validate_item (pending_validation → validated)
**Purpose**: Validate item structure, required fields, and data integrity
**Execution Mode**: SYNC

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with basic item data
- Item must have id and type fields populated

**Process Method Pseudocode**:
```
BEGIN process(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    // Validate required fields
    IF item.getId() == null OR item.getId() <= 0 THEN
        THROW ValidationException("Item ID is required and must be positive")
    END IF
    
    IF item.getType() == null OR !isValidType(item.getType()) THEN
        THROW ValidationException("Item type is required and must be valid")
    END IF
    
    // Validate type-specific requirements
    IF item.getType() == "pollopt" AND item.getPoll() == null THEN
        THROW ValidationException("Poll option must reference a poll")
    END IF
    
    IF item.getType() == "poll" AND (item.getParts() == null OR item.getParts().isEmpty()) THEN
        THROW ValidationException("Poll must have poll options")
    END IF
    
    // Validate hierarchical relationships
    IF item.getParent() != null AND item.getParent().equals(item.getId()) THEN
        THROW ValidationException("Item cannot be its own parent")
    END IF
    
    // Log validation success
    LOG.info("HackerNewsItem {} validated successfully", item.getId())
    
    RETURN entityWithMetadata
END
```

**Expected Output**: 
- Same EntityWithMetadata<HackerNewsItem> if validation passes
- Exception thrown if validation fails

### 2. EnrichHackerNewsItemProcessor

**Entity**: HackerNewsItem
**Workflow Transition**: enrich_item (validated → enriching)
**Purpose**: Fetch additional data from Firebase HN API to complete item information
**Execution Mode**: ASYNC_NEW_TX

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with validated basic data
- Item must have valid id for API lookup

**Process Method Pseudocode**:
```
BEGIN process(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    // Fetch from Firebase HN API
    apiUrl = "https://hacker-news.firebaseio.com/v0/item/" + item.getId() + ".json"
    
    TRY
        apiResponse = httpClient.get(apiUrl)
        
        IF apiResponse.getStatusCode() != 200 THEN
            THROW EnrichmentException("Failed to fetch item from HN API: " + apiResponse.getStatusCode())
        END IF
        
        apiData = parseJson(apiResponse.getBody())
        
        // Enrich item with API data
        IF apiData.getBy() != null THEN item.setBy(apiData.getBy())
        IF apiData.getTime() != null THEN item.setTime(apiData.getTime())
        IF apiData.getText() != null THEN item.setText(apiData.getText())
        IF apiData.getTitle() != null THEN item.setTitle(apiData.getTitle())
        IF apiData.getUrl() != null THEN item.setUrl(apiData.getUrl())
        IF apiData.getScore() != null THEN item.setScore(apiData.getScore())
        IF apiData.getKids() != null THEN item.setKids(apiData.getKids())
        IF apiData.getParent() != null THEN item.setParent(apiData.getParent())
        IF apiData.getDescendants() != null THEN item.setDescendants(apiData.getDescendants())
        IF apiData.getDeleted() != null THEN item.setDeleted(apiData.getDeleted())
        IF apiData.getDead() != null THEN item.setDead(apiData.getDead())
        IF apiData.getPoll() != null THEN item.setPoll(apiData.getPoll())
        IF apiData.getParts() != null THEN item.setParts(apiData.getParts())
        
        LOG.info("HackerNewsItem {} enriched successfully", item.getId())
        
    CATCH Exception e
        LOG.error("Failed to enrich HackerNewsItem {}: {}", item.getId(), e.getMessage())
        THROW EnrichmentException("Enrichment failed", e)
    END TRY
    
    RETURN entityWithMetadata
END
```

**Expected Output**: 
- EntityWithMetadata<HackerNewsItem> with enriched data from Firebase HN API
- Exception thrown if enrichment fails

### 3. StoreHackerNewsItemProcessor

**Entity**: HackerNewsItem
**Workflow Transitions**: 
- skip_enrichment (validated → stored)
- store_enriched_item (enriched → stored)
**Purpose**: Persist HackerNewsItem to the storage system
**Execution Mode**: SYNC

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with validated (and optionally enriched) data
- Item ready for final storage

**Process Method Pseudocode**:
```
BEGIN process(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    item = entityWithMetadata.getEntity()
    
    TRY
        // Check if item already exists
        existingItem = entityService.findByBusinessKey("HackerNewsItem", item.getId().toString())
        
        IF existingItem != null THEN
            LOG.info("Updating existing HackerNewsItem {}", item.getId())
            // Update existing item
            updatedEntity = entityService.update(existingItem.getMeta().getUuid(), item)
        ELSE
            LOG.info("Creating new HackerNewsItem {}", item.getId())
            // Create new item
            createdEntity = entityService.create(item)
        END IF
        
        LOG.info("HackerNewsItem {} stored successfully", item.getId())
        
    CATCH Exception e
        LOG.error("Failed to store HackerNewsItem {}: {}", item.getId(), e.getMessage())
        THROW StorageException("Storage failed", e)
    END TRY
    
    RETURN entityWithMetadata
END
```

**Expected Output**: 
- EntityWithMetadata<HackerNewsItem> with updated metadata after storage
- Exception thrown if storage fails

### 4. BulkProcessHackerNewsItemsProcessor

**Entity**: HackerNewsItem
**Workflow Transition**: null transition (used for bulk operations outside normal workflow)
**Purpose**: Process multiple HackerNewsItems from bulk upload operations
**Execution Mode**: ASYNC_NEW_TX

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> containing a list of items in a special bulk format
- Items from JSON file upload or array POST

**Process Method Pseudocode**:
```
BEGIN process(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    bulkData = entityWithMetadata.getEntity()
    itemsList = extractItemsFromBulkData(bulkData)
    
    successCount = 0
    failureCount = 0
    
    FOR EACH item IN itemsList DO
        TRY
            // Create individual entity for workflow processing
            itemEntity = EntityWithMetadata.of(item)
            
            // Trigger validation workflow
            entityService.create(itemEntity.getEntity())
            
            successCount++
            LOG.info("Bulk processed HackerNewsItem {} successfully", item.getId())
            
        CATCH Exception e
            failureCount++
            LOG.error("Failed to bulk process HackerNewsItem {}: {}", item.getId(), e.getMessage())
        END TRY
    END FOR
    
    LOG.info("Bulk processing completed: {} success, {} failures", successCount, failureCount)
    
    RETURN entityWithMetadata
END
```

**Expected Output**: 
- EntityWithMetadata<HackerNewsItem> with bulk processing results
- Individual items enter normal workflow for validation and processing

### 5. FetchFromFirebaseProcessor

**Entity**: HackerNewsItem
**Workflow Transition**: null transition (used for external data fetching)
**Purpose**: Fetch items from Firebase HN API endpoints (top stories, new stories, etc.)
**Execution Mode**: ASYNC_NEW_TX

**Expected Input**:
- EntityWithMetadata<HackerNewsItem> with fetch configuration (endpoint type, count)

**Process Method Pseudocode**:
```
BEGIN process(EntityWithMetadata<HackerNewsItem> entityWithMetadata)
    config = entityWithMetadata.getEntity()
    endpoint = config.getFetchEndpoint() // "topstories", "newstories", "beststories", etc.
    
    apiUrl = "https://hacker-news.firebaseio.com/v0/" + endpoint + ".json"
    
    TRY
        // Fetch item IDs from endpoint
        response = httpClient.get(apiUrl)
        itemIds = parseJsonArray(response.getBody())
        
        // Limit to requested count
        maxItems = config.getMaxItems() != null ? config.getMaxItems() : 100
        itemIds = itemIds.subList(0, Math.min(itemIds.size(), maxItems))
        
        // Create entities for each item ID
        FOR EACH itemId IN itemIds DO
            newItem = new HackerNewsItem()
            newItem.setId(itemId)
            newItem.setType("story") // Default type, will be corrected during enrichment
            
            // Create entity to trigger workflow
            entityService.create(newItem)
        END FOR
        
        LOG.info("Fetched {} items from Firebase HN API endpoint {}", itemIds.size(), endpoint)
        
    CATCH Exception e
        LOG.error("Failed to fetch from Firebase HN API: {}", e.getMessage())
        THROW FetchException("Firebase fetch failed", e)
    END TRY
    
    RETURN entityWithMetadata
END
```

**Expected Output**: 
- Multiple new HackerNewsItem entities created and entered into workflow
- Exception thrown if fetch fails
