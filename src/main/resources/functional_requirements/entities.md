# Entities

## 1. HNItem Entity

### Description
Represents a single Hacker News item in the JSON format of the Firebase HN API. This includes stories, comments, jobs, Ask HNs, polls, and poll options.

### Attributes
- **id** (Long, required): The item's unique id
- **deleted** (Boolean): true if the item is deleted
- **type** (String, required): The type of item. One of "job", "story", "comment", "poll", or "pollopt"
- **by** (String): The username of the item's author
- **time** (Long): Creation date of the item, in Unix Time
- **text** (String): The comment, story or poll text. HTML format
- **dead** (Boolean): true if the item is dead
- **parent** (Long): The comment's parent: either another comment or the relevant story
- **poll** (Long): The pollopt's associated poll
- **kids** (List<Long>): The ids of the item's comments, in ranked display order
- **url** (String): The URL of the story
- **score** (Integer): The story's score, or the votes for a pollopt
- **title** (String): The title of the story, poll or job. HTML format
- **parts** (List<Long>): A list of related pollopts, in display order
- **descendants** (Integer): In the case of stories or polls, the total comment count

### Entity State
The entity state represents the processing status of the HN item in the workflow:
- **INITIAL**: Item is newly created/imported
- **VALIDATED**: Item has been validated for required fields
- **ENRICHED**: Item has been enriched with additional data if needed
- **INDEXED**: Item has been indexed for search
- **PROCESSED**: Item is fully processed and available

### Relationships
- **Parent-Child**: HNItems can have parent-child relationships through the `parent` field
- **Poll-PollOption**: Poll items are related to their poll options through the `poll` and `parts` fields
- **Hierarchical**: Comments form a tree structure through parent-child relationships

### Model Key
The model key for HNItem is: `"hnitem"`

### Validation Rules
- `id` must be present and positive
- `type` must be one of: "job", "story", "comment", "poll", "pollopt"
- For comments: `parent` field must be present
- For poll options: `poll` field must be present
- For polls: `parts` field should contain poll option IDs

## 2. SearchQuery Entity

### Description
Represents a search query for finding HN items with specific criteria, including support for parent hierarchy joins.

### Attributes
- **queryId** (String, required): Unique identifier for the search query
- **searchText** (String): Text to search for in title, text, or author fields
- **itemType** (String): Filter by item type ("story", "comment", "job", "poll", "pollopt")
- **author** (String): Filter by author username
- **minScore** (Integer): Minimum score threshold
- **maxScore** (Integer): Maximum score threshold
- **fromTime** (Long): Start time filter (Unix timestamp)
- **toTime** (Long): End time filter (Unix timestamp)
- **includeParentHierarchy** (Boolean): Whether to include parent hierarchy in results
- **maxDepth** (Integer): Maximum depth for parent hierarchy traversal
- **sortBy** (String): Sort criteria ("score", "time", "relevance")
- **sortOrder** (String): Sort order ("asc", "desc")
- **limit** (Integer): Maximum number of results to return
- **offset** (Integer): Offset for pagination

### Entity State
The entity state represents the processing status of the search query:
- **INITIAL**: Query is newly created
- **VALIDATED**: Query parameters have been validated
- **EXECUTING**: Search is currently being executed
- **COMPLETED**: Search has completed successfully
- **FAILED**: Search execution failed

### Relationships
- **Results**: SearchQuery produces a list of HNItem results
- **Hierarchy**: When `includeParentHierarchy` is true, results include parent-child relationships

### Model Key
The model key for SearchQuery is: `"searchquery"`

### Validation Rules
- `queryId` must be present and unique
- `itemType` if present must be one of: "job", "story", "comment", "poll", "pollopt"
- `minScore` must be non-negative if present
- `maxScore` must be greater than `minScore` if both are present
- `fromTime` must be less than `toTime` if both are present
- `maxDepth` must be positive if `includeParentHierarchy` is true
- `sortBy` if present must be one of: "score", "time", "relevance"
- `sortOrder` if present must be one of: "asc", "desc"
- `limit` must be positive if present
- `offset` must be non-negative if present

## 3. BulkUpload Entity

### Description
Represents a bulk upload operation for importing multiple HN items from a JSON file.

### Attributes
- **uploadId** (String, required): Unique identifier for the bulk upload
- **fileName** (String, required): Name of the uploaded file
- **fileSize** (Long): Size of the uploaded file in bytes
- **totalItems** (Integer): Total number of items in the file
- **processedItems** (Integer): Number of items successfully processed
- **failedItems** (Integer): Number of items that failed processing
- **errorMessages** (List<String>): List of error messages for failed items
- **uploadTime** (Long): Timestamp when upload was initiated

### Entity State
The entity state represents the processing status of the bulk upload:
- **INITIAL**: Upload is newly created
- **VALIDATED**: File format and structure have been validated
- **PROCESSING**: Items are being processed individually
- **COMPLETED**: All items have been processed successfully
- **PARTIALLY_COMPLETED**: Some items processed successfully, some failed
- **FAILED**: Upload processing failed completely

### Relationships
- **Items**: BulkUpload processes multiple HNItem entities
- **Results**: Each processed item becomes an HNItem entity

### Model Key
The model key for BulkUpload is: `"bulkupload"`

### Validation Rules
- `uploadId` must be present and unique
- `fileName` must be present and non-empty
- `fileSize` must be positive if present
- `totalItems` must be non-negative if present
- `processedItems` must be non-negative and not exceed `totalItems`
- `failedItems` must be non-negative and not exceed `totalItems`
- `processedItems + failedItems` should not exceed `totalItems`
