# HNItemSearch Entity

## Description
Manages search queries for Hacker News items with support for hierarchical parent joins and complex filtering.

## Attributes
- **searchId**: Unique identifier for the search query (required)
- **query**: Search query string (required)
- **searchType**: Type of search - "text", "author", "type", "hierarchical" (required)
- **filters**: JSON object containing search filters (type, author, time range, etc.)
- **includeParents**: Boolean flag to include parent hierarchy in results
- **maxResults**: Maximum number of results to return
- **resultCount**: Actual number of results found
- **searchTimestamp**: When the search was executed
- **executionTimeMs**: Time taken to execute the search

## Relationships
- References HNItem entities in search results
- No direct foreign key relationships

## Notes
Entity state tracks search execution: pending, executing, completed, failed.
State is managed via `entity.meta.state`.
