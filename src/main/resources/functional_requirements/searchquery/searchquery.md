# SearchQuery Entity Requirements

## Overview
The SearchQuery entity represents search operations for HN items. It captures search parameters, filters, and hierarchy traversal requirements, and tracks the execution and results of search operations.

## Entity Name
- **Entity Name**: SearchQuery
- **Version**: 1
- **Package**: `com.java_template.application.entity.searchquery.version_1`

## Attributes

### Required Fields
- **queryId** (String): Unique identifier for the search query - primary business identifier
- **createdAt** (LocalDateTime): When the search query was created

### Search Parameters
- **keywords** (String): Full-text search keywords for title, text, and author fields
- **author** (String): Filter by specific HN username (by field)
- **itemTypes** (List<String>): Filter by item types ("story", "comment", "job", "poll", "pollopt")
- **minScore** (Integer): Minimum score threshold for items
- **maxScore** (Integer): Maximum score threshold for items

### Time Range Filters
- **fromTime** (Long): Start time filter (Unix timestamp)
- **toTime** (Long): End time filter (Unix timestamp)
- **fromDate** (LocalDateTime): Start date filter (converted to Unix time)
- **toDate** (LocalDateTime): End date filter (converted to Unix time)

### Hierarchy Filters
- **parentId** (Long): Find all children of a specific parent item
- **rootItemsOnly** (Boolean): Only return top-level items (stories, jobs, polls)
- **includeDescendants** (Boolean): Include all descendants in hierarchy traversal
- **maxDepth** (Integer): Maximum depth for hierarchy traversal (null = unlimited)

### Result Configuration
- **sortBy** (String): Sort field ("score", "time", "title", "descendants")
- **sortOrder** (String): Sort direction ("ASC", "DESC")
- **limit** (Integer): Maximum number of results to return
- **offset** (Integer): Number of results to skip (for pagination)

### Execution Tracking
- **executedAt** (LocalDateTime): When the search was executed
- **executionTimeMs** (Long): How long the search took to execute
- **resultCount** (Integer): Number of results returned
- **totalMatches** (Long): Total number of matching items (before limit/offset)

### Result Storage
- **resultIds** (List<Long>): HN IDs of items that matched the search
- **resultSummary** (String): Summary of search results
- **cachedUntil** (LocalDateTime): When cached results expire

### Metadata
- **requestedBy** (String): User or system that requested the search
- **searchContext** (String): Context or purpose of the search
- **tags** (List<String>): Tags for categorizing searches

## Nested Classes

### SearchFilter
Represents complex filter conditions:
- **field** (String): Field name to filter on
- **operator** (String): Filter operator ("EQUALS", "CONTAINS", "GREATER_THAN", etc.)
- **value** (String): Filter value
- **caseSensitive** (Boolean): Whether string comparisons are case-sensitive

### HierarchyConfig
Configuration for hierarchy traversal:
- **traversalType** (String): Type of traversal ("CHILDREN", "DESCENDANTS", "ANCESTORS", "SIBLINGS")
- **includeRoot** (Boolean): Whether to include the root item in results
- **depthLimit** (Integer): Maximum depth to traverse
- **sortChildrenBy** (String): How to sort children at each level

## Relationships

### HNItem Relationship
- **Many-to-Many**: One SearchQuery can match multiple HNItems
- **Reference**: Results stored as list of HN IDs in `resultIds`
- **Dynamic**: Relationship is query-based, not stored permanently

## Validation Rules

### Required Field Validation
- `queryId` must not be null and must be unique
- `createdAt` must not be null
- At least one search parameter must be specified

### Search Parameter Validation
- If `keywords` is provided, must not be empty
- `itemTypes` must contain only valid types if specified
- `minScore` must be less than or equal to `maxScore` if both specified

### Time Range Validation
- `fromTime` must be less than `toTime` if both specified
- `fromDate` must be before `toDate` if both specified
- Time fields should be converted consistently between Unix time and LocalDateTime

### Hierarchy Validation
- `parentId` must reference a valid HNItem if specified
- `maxDepth` must be positive if specified
- Cannot specify both `parentId` and `rootItemsOnly` as true

### Result Configuration Validation
- `sortBy` must be a valid field name if specified
- `sortOrder` must be "ASC" or "DESC" if specified
- `limit` must be positive if specified
- `offset` must be non-negative if specified

## Business Logic Notes

### State Management
- Entity state managed via `entity.meta.state` workflow states
- Typical states: "created", "executing", "completed", "failed", "cached"

### Search Execution
- Support both immediate execution and queued/async execution
- Cache results for frequently executed queries
- Track performance metrics for query optimization

### Hierarchy Traversal
- Efficiently traverse parent-child relationships
- Support both upward (ancestors) and downward (descendants) traversal
- Handle circular references gracefully

### Performance Optimization
- Index commonly searched fields
- Implement query result caching
- Support pagination for large result sets
- Consider search result limits to prevent resource exhaustion

### Search Features
- Full-text search across title, text, and author fields
- Boolean search operators (AND, OR, NOT)
- Wildcard and phrase matching
- Fuzzy matching for typos

### Result Management
- Store search results temporarily for pagination
- Provide result export capabilities
- Track search analytics and popular queries
