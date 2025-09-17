# Criteria

## HNItem Criteria

### 1. HNItemNeedsEnrichmentCriterion

**Entity**: HNItem  
**Description**: Determines if an HN item needs enrichment based on its type and available data.

**Logic**:
```
check(hnItem):
    // Stories with URLs need domain extraction
    if hnItem.type == "story" and hnItem.url is not null and hnItem.url is not empty:
        return true
    
    // Items with text need text analysis
    if hnItem.text is not null and hnItem.text is not empty:
        return true
    
    // Items with children need child count calculation
    if hnItem.kids is not null and hnItem.kids.size() > 0:
        return true
    
    // Otherwise, no enrichment needed
    return false
```

### 2. HNItemNoEnrichmentNeededCriterion

**Entity**: HNItem  
**Description**: Determines if an HN item can skip enrichment and go directly to indexing.

**Logic**:
```
check(hnItem):
    // Inverse of HNItemNeedsEnrichmentCriterion
    return not HNItemNeedsEnrichmentCriterion.check(hnItem)
```

## SearchQuery Criteria

### 3. SearchQuerySuccessCriterion

**Entity**: SearchQuery  
**Description**: Determines if a search query execution was successful.

**Logic**:
```
check(searchQuery):
    // Check if execution completed without errors
    if searchQuery.executionEndTime is null:
        return false
    
    // Check if results were found (even empty results are successful)
    if searchQuery.intermediateResults is null:
        return false
    
    // Check if no error occurred during execution
    if hasActiveException():
        return false
    
    return true
```

### 4. SearchQueryFailureCriterion

**Entity**: SearchQuery  
**Description**: Determines if a search query execution failed.

**Logic**:
```
check(searchQuery):
    // Inverse of SearchQuerySuccessCriterion
    return not SearchQuerySuccessCriterion.check(searchQuery)
```

## BulkUpload Criteria

### 5. BulkUploadAllSuccessCriterion

**Entity**: BulkUpload  
**Description**: Determines if all items in a bulk upload were processed successfully.

**Logic**:
```
check(bulkUpload):
    // Check if processing is complete
    if bulkUpload.processingEndTime is null:
        return false
    
    // Check if all items were processed successfully
    if bulkUpload.totalItems is null or bulkUpload.totalItems == 0:
        return false
    
    if bulkUpload.processedItems != bulkUpload.totalItems:
        return false
    
    if bulkUpload.failedItems > 0:
        return false
    
    return true
```

### 6. BulkUploadPartialSuccessCriterion

**Entity**: BulkUpload  
**Description**: Determines if a bulk upload had partial success (some items succeeded, some failed).

**Logic**:
```
check(bulkUpload):
    // Check if processing is complete
    if bulkUpload.processingEndTime is null:
        return false
    
    // Check if we have both successful and failed items
    if bulkUpload.processedItems is null or bulkUpload.failedItems is null:
        return false
    
    if bulkUpload.processedItems > 0 and bulkUpload.failedItems > 0:
        return true
    
    return false
```

### 7. BulkUploadTotalFailureCriterion

**Entity**: BulkUpload  
**Description**: Determines if a bulk upload failed completely (no items processed successfully).

**Logic**:
```
check(bulkUpload):
    // Check if processing is complete
    if bulkUpload.processingEndTime is null:
        return false
    
    // Check if no items were processed successfully
    if bulkUpload.processedItems is null or bulkUpload.processedItems == 0:
        if bulkUpload.totalItems is not null and bulkUpload.totalItems > 0:
            return true
    
    return false
```

## Additional Validation Criteria

### 8. HNItemValidTypeCriterion

**Entity**: HNItem  
**Description**: Validates that the HN item has a valid type.

**Logic**:
```
check(hnItem):
    validTypes = ["job", "story", "comment", "poll", "pollopt"]
    return hnItem.type is not null and hnItem.type in validTypes
```

### 9. HNItemValidIdCriterion

**Entity**: HNItem  
**Description**: Validates that the HN item has a valid ID.

**Logic**:
```
check(hnItem):
    return hnItem.id is not null and hnItem.id > 0
```

### 10. SearchQueryValidParametersCriterion

**Entity**: SearchQuery  
**Description**: Validates that search query parameters are within acceptable ranges.

**Logic**:
```
check(searchQuery):
    // Check limit is reasonable
    if searchQuery.limit is not null and searchQuery.limit > 1000:
        return false
    
    // Check time range is reasonable (not more than 10 years)
    if searchQuery.fromTime is not null and searchQuery.toTime is not null:
        timeDiff = searchQuery.toTime - searchQuery.fromTime
        tenYearsInSeconds = 10 * 365 * 24 * 60 * 60
        if timeDiff > tenYearsInSeconds:
            return false
    
    // Check max depth for hierarchy is reasonable
    if searchQuery.maxDepth is not null and searchQuery.maxDepth > 10:
        return false
    
    return true
```

### 11. BulkUploadValidFileSizeCriterion

**Entity**: BulkUpload  
**Description**: Validates that the uploaded file size is within acceptable limits.

**Logic**:
```
check(bulkUpload):
    MAX_FILE_SIZE = 100 * 1024 * 1024  // 100 MB
    
    if bulkUpload.fileSize is null:
        return false
    
    return bulkUpload.fileSize > 0 and bulkUpload.fileSize <= MAX_FILE_SIZE
```

### 12. HNItemCommentValidParentCriterion

**Entity**: HNItem  
**Description**: Validates that comment items have a valid parent reference.

**Logic**:
```
check(hnItem):
    if hnItem.type != "comment":
        return true  // Not applicable to non-comments
    
    // Comments must have a parent
    if hnItem.parent is null or hnItem.parent <= 0:
        return false
    
    // Parent cannot be the same as the item itself
    if hnItem.parent == hnItem.id:
        return false
    
    return true
```

### 13. HNItemPollValidPartsCriterion

**Entity**: HNItem  
**Description**: Validates that poll items have valid poll option references.

**Logic**:
```
check(hnItem):
    if hnItem.type != "poll":
        return true  // Not applicable to non-polls
    
    // Polls must have parts (poll options)
    if hnItem.parts is null or hnItem.parts.isEmpty():
        return false
    
    // All parts must be valid IDs
    for partId in hnItem.parts:
        if partId is null or partId <= 0:
            return false
    
    return true
```

### 14. HNItemPollOptValidPollCriterion

**Entity**: HNItem  
**Description**: Validates that poll option items have a valid poll reference.

**Logic**:
```
check(hnItem):
    if hnItem.type != "pollopt":
        return true  // Not applicable to non-poll options
    
    // Poll options must reference a poll
    if hnItem.poll is null or hnItem.poll <= 0:
        return false
    
    // Poll reference cannot be the same as the item itself
    if hnItem.poll == hnItem.id:
        return false
    
    return true
```

### 15. SearchQueryValidSortCriterion

**Entity**: SearchQuery  
**Description**: Validates that search query sort parameters are valid.

**Logic**:
```
check(searchQuery):
    validSortBy = ["score", "time", "relevance"]
    validSortOrder = ["asc", "desc"]
    
    // If sortBy is specified, it must be valid
    if searchQuery.sortBy is not null:
        if searchQuery.sortBy not in validSortBy:
            return false
    
    // If sortOrder is specified, it must be valid
    if searchQuery.sortOrder is not null:
        if searchQuery.sortOrder not in validSortOrder:
            return false
    
    return true
```
