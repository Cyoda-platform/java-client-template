# Processors

## HNItem Processors

### 1. HNItemValidationProcessor

**Entity**: HNItem  
**Input**: HNItem in INITIAL state  
**Output**: HNItem in VALIDATED state  

**Description**: Validates the structure and required fields of an HN item according to Firebase HN API format.

**Pseudocode**:
```
process(hnItem):
    // Validate required fields
    if hnItem.id is null or hnItem.id <= 0:
        throw ValidationException("ID is required and must be positive")
    
    if hnItem.type is null or hnItem.type not in ["job", "story", "comment", "poll", "pollopt"]:
        throw ValidationException("Type must be one of: job, story, comment, poll, pollopt")
    
    // Type-specific validations
    if hnItem.type == "comment":
        if hnItem.parent is null:
            throw ValidationException("Comments must have a parent")
    
    if hnItem.type == "pollopt":
        if hnItem.poll is null:
            throw ValidationException("Poll options must reference a poll")
    
    if hnItem.type == "poll":
        if hnItem.parts is null or hnItem.parts.isEmpty():
            throw ValidationException("Polls must have poll options")
    
    // Validate data integrity
    if hnItem.score is not null and hnItem.score < 0:
        throw ValidationException("Score cannot be negative")
    
    if hnItem.descendants is not null and hnItem.descendants < 0:
        throw ValidationException("Descendants count cannot be negative")
    
    // Set validation timestamp
    hnItem.validatedAt = currentTimestamp()
    
    return hnItem
```

### 2. HNItemEnrichmentProcessor

**Entity**: HNItem  
**Input**: HNItem in VALIDATED state  
**Output**: HNItem in ENRICHED state  

**Description**: Enriches HN items with additional metadata and computed fields.

**Pseudocode**:
```
process(hnItem):
    // Calculate derived fields
    if hnItem.kids is not null:
        hnItem.directChildrenCount = hnItem.kids.size()
    
    // Enrich with URL metadata for stories
    if hnItem.type == "story" and hnItem.url is not null:
        hnItem.domain = extractDomain(hnItem.url)
        hnItem.urlValid = validateUrl(hnItem.url)
    
    // Calculate text statistics
    if hnItem.text is not null:
        hnItem.textLength = stripHtml(hnItem.text).length()
        hnItem.wordCount = countWords(stripHtml(hnItem.text))
    
    // Set enrichment timestamp
    hnItem.enrichedAt = currentTimestamp()
    
    return hnItem
```

### 3. HNItemIndexingProcessor

**Entity**: HNItem  
**Input**: HNItem in VALIDATED or ENRICHED state  
**Output**: HNItem in INDEXED state  

**Description**: Indexes the HN item for search capabilities and creates search metadata.

**Pseudocode**:
```
process(hnItem):
    // Create search index entry
    searchDocument = createSearchDocument()
    searchDocument.id = hnItem.id
    searchDocument.type = hnItem.type
    searchDocument.title = hnItem.title
    searchDocument.text = stripHtml(hnItem.text)
    searchDocument.author = hnItem.by
    searchDocument.score = hnItem.score
    searchDocument.time = hnItem.time
    searchDocument.url = hnItem.url
    
    // Add to search index
    searchService.indexDocument(searchDocument)
    
    // Update parent-child relationships in index
    if hnItem.parent is not null:
        searchService.updateParentChildRelationship(hnItem.parent, hnItem.id)
    
    if hnItem.kids is not null:
        for childId in hnItem.kids:
            searchService.updateParentChildRelationship(hnItem.id, childId)
    
    // Set indexing timestamp
    hnItem.indexedAt = currentTimestamp()
    
    return hnItem
```

### 4. HNItemFinalizationProcessor

**Entity**: HNItem  
**Input**: HNItem in INDEXED state  
**Output**: HNItem in PROCESSED state  

**Description**: Finalizes the HN item processing and makes it available for queries.

**Pseudocode**:
```
process(hnItem):
    // Mark as available for queries
    hnItem.available = true
    
    // Update statistics
    updateGlobalStatistics(hnItem.type)
    
    // Trigger notifications if needed
    if hnItem.type == "story" and hnItem.score > 100:
        notificationService.notifyHighScoreStory(hnItem)
    
    // Set processing completion timestamp
    hnItem.processedAt = currentTimestamp()
    
    return hnItem
```

### 5. HNItemReprocessingProcessor

**Entity**: HNItem  
**Input**: HNItem in PROCESSED state  
**Output**: HNItem in VALIDATED state  

**Description**: Resets an item for reprocessing, clearing derived fields and timestamps.

**Pseudocode**:
```
process(hnItem):
    // Clear derived fields
    hnItem.directChildrenCount = null
    hnItem.domain = null
    hnItem.urlValid = null
    hnItem.textLength = null
    hnItem.wordCount = null
    hnItem.available = false
    
    // Clear processing timestamps
    hnItem.enrichedAt = null
    hnItem.indexedAt = null
    hnItem.processedAt = null
    
    // Remove from search index
    searchService.removeDocument(hnItem.id)
    
    return hnItem
```

## SearchQuery Processors

### 6. SearchQueryValidationProcessor

**Entity**: SearchQuery  
**Input**: SearchQuery in INITIAL state  
**Output**: SearchQuery in VALIDATED state  

**Description**: Validates search query parameters and ensures they are within acceptable limits.

**Pseudocode**:
```
process(searchQuery):
    // Validate query ID
    if searchQuery.queryId is null or searchQuery.queryId.isEmpty():
        throw ValidationException("Query ID is required")
    
    // Validate item type
    if searchQuery.itemType is not null:
        if searchQuery.itemType not in ["job", "story", "comment", "poll", "pollopt"]:
            throw ValidationException("Invalid item type")
    
    // Validate score range
    if searchQuery.minScore is not null and searchQuery.minScore < 0:
        throw ValidationException("Minimum score cannot be negative")
    
    if searchQuery.maxScore is not null and searchQuery.minScore is not null:
        if searchQuery.maxScore < searchQuery.minScore:
            throw ValidationException("Maximum score must be greater than minimum score")
    
    // Validate time range
    if searchQuery.fromTime is not null and searchQuery.toTime is not null:
        if searchQuery.fromTime >= searchQuery.toTime:
            throw ValidationException("From time must be before to time")
    
    // Validate pagination
    if searchQuery.limit is not null and searchQuery.limit <= 0:
        throw ValidationException("Limit must be positive")
    
    if searchQuery.offset is not null and searchQuery.offset < 0:
        throw ValidationException("Offset cannot be negative")
    
    // Set validation timestamp
    searchQuery.validatedAt = currentTimestamp()
    
    return searchQuery
```

### 7. SearchQueryExecutionProcessor

**Entity**: SearchQuery  
**Input**: SearchQuery in VALIDATED state  
**Output**: SearchQuery in EXECUTING state  

**Description**: Executes the search query against the indexed HN items.

**Pseudocode**:
```
process(searchQuery):
    // Build search criteria
    searchCriteria = buildSearchCriteria(searchQuery)
    
    // Execute search
    searchQuery.executionStartTime = currentTimestamp()
    searchResults = searchService.search(searchCriteria)
    
    // Apply parent hierarchy if requested
    if searchQuery.includeParentHierarchy:
        searchResults = enrichWithParentHierarchy(searchResults, searchQuery.maxDepth)
    
    // Store intermediate results
    searchQuery.intermediateResults = searchResults
    searchQuery.resultCount = searchResults.size()
    
    return searchQuery
```

### 8. SearchQueryCompletionProcessor

**Entity**: SearchQuery  
**Input**: SearchQuery in EXECUTING state  
**Output**: SearchQuery in COMPLETED state  

**Description**: Completes successful search execution and prepares final results.

**Pseudocode**:
```
process(searchQuery):
    // Finalize results
    searchQuery.results = searchQuery.intermediateResults
    searchQuery.executionEndTime = currentTimestamp()
    searchQuery.executionDuration = searchQuery.executionEndTime - searchQuery.executionStartTime
    
    // Clear intermediate data
    searchQuery.intermediateResults = null
    
    // Log successful execution
    logSearchExecution(searchQuery, "SUCCESS")
    
    return searchQuery
```

### 9. SearchQueryFailureProcessor

**Entity**: SearchQuery  
**Input**: SearchQuery in EXECUTING state  
**Output**: SearchQuery in FAILED state  

**Description**: Handles failed search execution and captures error information.

**Pseudocode**:
```
process(searchQuery):
    // Capture failure information
    searchQuery.executionEndTime = currentTimestamp()
    searchQuery.executionDuration = searchQuery.executionEndTime - searchQuery.executionStartTime
    searchQuery.errorMessage = getCurrentException().getMessage()
    
    // Clear intermediate data
    searchQuery.intermediateResults = null
    searchQuery.results = null
    
    // Log failed execution
    logSearchExecution(searchQuery, "FAILURE")
    
    return searchQuery
```

## BulkUpload Processors

### 10. BulkUploadValidationProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in INITIAL state  
**Output**: BulkUpload in VALIDATED state  

**Description**: Validates the uploaded file format and structure.

**Pseudocode**:
```
process(bulkUpload):
    // Validate file exists and is readable
    file = getUploadedFile(bulkUpload.fileName)
    if file is null or not file.exists():
        throw ValidationException("File not found or not readable")
    
    // Validate file size
    if file.size() > MAX_FILE_SIZE:
        throw ValidationException("File size exceeds maximum limit")
    
    // Validate JSON format
    try:
        jsonContent = parseJsonFile(file)
        if not jsonContent.isArray():
            throw ValidationException("File must contain a JSON array of HN items")
        
        bulkUpload.totalItems = jsonContent.size()
        
        // Basic validation of first few items
        for i in range(min(5, jsonContent.size())):
            item = jsonContent.get(i)
            validateBasicHNItemStructure(item)
        
    catch JsonParseException:
        throw ValidationException("Invalid JSON format")
    
    bulkUpload.validatedAt = currentTimestamp()
    return bulkUpload
```

### 11. BulkUploadProcessingProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in VALIDATED state  
**Output**: BulkUpload in PROCESSING state  

**Description**: Processes individual HN items from the bulk upload file.

**Pseudocode**:
```
process(bulkUpload):
    bulkUpload.processingStartTime = currentTimestamp()
    bulkUpload.processedItems = 0
    bulkUpload.failedItems = 0
    bulkUpload.errorMessages = []
    
    file = getUploadedFile(bulkUpload.fileName)
    jsonContent = parseJsonFile(file)
    
    for item in jsonContent:
        try:
            // Create HNItem entity
            hnItem = createHNItemFromJson(item)
            
            // Save and trigger HNItem workflow (transition: null - entity will start in INITIAL state)
            entityService.save(hnItem, null)
            
            bulkUpload.processedItems++
            
        catch Exception e:
            bulkUpload.failedItems++
            bulkUpload.errorMessages.add("Item " + item.id + ": " + e.getMessage())
    
    return bulkUpload
```

### 12. BulkUploadCompletionProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in PROCESSING state  
**Output**: BulkUpload in COMPLETED state  

**Description**: Completes successful bulk upload processing.

**Pseudocode**:
```
process(bulkUpload):
    bulkUpload.processingEndTime = currentTimestamp()
    bulkUpload.processingDuration = bulkUpload.processingEndTime - bulkUpload.processingStartTime
    
    // Log successful completion
    logBulkUpload(bulkUpload, "COMPLETED")
    
    return bulkUpload
```

### 13. BulkUploadPartialCompletionProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in PROCESSING state  
**Output**: BulkUpload in PARTIALLY_COMPLETED state  

**Description**: Handles partial completion when some items succeed and some fail.

**Pseudocode**:
```
process(bulkUpload):
    bulkUpload.processingEndTime = currentTimestamp()
    bulkUpload.processingDuration = bulkUpload.processingEndTime - bulkUpload.processingStartTime
    
    // Calculate success rate
    bulkUpload.successRate = (bulkUpload.processedItems / bulkUpload.totalItems) * 100
    
    // Log partial completion
    logBulkUpload(bulkUpload, "PARTIALLY_COMPLETED")
    
    return bulkUpload
```

### 14. BulkUploadFailureProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in PROCESSING state  
**Output**: BulkUpload in FAILED state  

**Description**: Handles complete failure of bulk upload processing.

**Pseudocode**:
```
process(bulkUpload):
    bulkUpload.processingEndTime = currentTimestamp()
    bulkUpload.processingDuration = bulkUpload.processingEndTime - bulkUpload.processingStartTime
    
    // Log failure
    logBulkUpload(bulkUpload, "FAILED")
    
    return bulkUpload
```

### 15. BulkUploadRetryProcessor

**Entity**: BulkUpload  
**Input**: BulkUpload in PARTIALLY_COMPLETED state  
**Output**: BulkUpload in PROCESSING state  

**Description**: Retries processing of failed items from a partially completed upload.

**Pseudocode**:
```
process(bulkUpload):
    // Reset processing state for retry
    bulkUpload.processingStartTime = currentTimestamp()
    previousFailedItems = bulkUpload.failedItems
    bulkUpload.failedItems = 0
    bulkUpload.errorMessages = []
    
    // Only process previously failed items
    file = getUploadedFile(bulkUpload.fileName)
    jsonContent = parseJsonFile(file)
    
    processedCount = 0
    for item in jsonContent:
        processedCount++
        
        // Skip items that were previously successful
        if processedCount <= (bulkUpload.totalItems - previousFailedItems):
            continue
        
        try:
            hnItem = createHNItemFromJson(item)
            entityService.save(hnItem, null)
            bulkUpload.processedItems++
            
        catch Exception e:
            bulkUpload.failedItems++
            bulkUpload.errorMessages.add("Item " + item.id + ": " + e.getMessage())
    
    return bulkUpload
```
