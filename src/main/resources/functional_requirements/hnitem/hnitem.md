# HN Item Entity

## Overview
Represents a Hacker News item from the Firebase HN API, including stories, comments, jobs, Ask HNs, and polls.

## Attributes
- **id**: Unique integer identifier
- **type**: Item type ("story", "comment", "job", "poll", "pollopt")
- **by**: Username of the author
- **time**: Creation timestamp (Unix time)
- **title**: Title of the item (for stories, polls, jobs)
- **text**: Content text (HTML format)
- **url**: URL for stories
- **score**: Score/votes for the item
- **parent**: Parent item ID (for comments)
- **kids**: Array of child comment IDs
- **descendants**: Total comment count
- **deleted**: Boolean indicating if item is deleted
- **dead**: Boolean indicating if item is dead
- **poll**: Associated poll ID (for poll options)
- **parts**: Array of poll option IDs (for polls)

## Relationships
- Parent-child relationships through `parent` and `kids` fields
- Poll-option relationships through `poll` and `parts` fields
- Hierarchical comment threading structure

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
