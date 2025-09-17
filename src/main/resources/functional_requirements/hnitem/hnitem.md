# HNItem Entity Requirements

## Overview
The HNItem entity represents a Hacker News item from the Firebase HN API. It stores all the data fields available in the Firebase API format and supports hierarchical relationships between items (parent-child comments).

## Entity Name
- **Entity Name**: HNItem
- **Version**: 1
- **Package**: `com.java_template.application.entity.hnitem.version_1`

## Attributes

### Required Fields
- **hnId** (Long): The item's unique Firebase HN ID - primary business identifier
- **type** (String): The type of item - one of "job", "story", "comment", "poll", or "pollopt"

### Core Firebase HN API Fields
- **by** (String): The username of the item's author
- **time** (Long): Creation date of the item, in Unix Time
- **text** (String): The comment, story or poll text (HTML format)
- **url** (String): The URL of the story
- **score** (Integer): The story's score, or the votes for a pollopt
- **title** (String): The title of the story, poll or job (HTML format)
- **descendants** (Integer): Total comment count for stories or polls

### Hierarchical Relationship Fields
- **parent** (Long): The comment's parent ID (either another comment or the relevant story)
- **kids** (List<Long>): The IDs of the item's comments, in ranked display order
- **poll** (Long): The pollopt's associated poll ID
- **parts** (List<Long>): A list of related pollopts, in display order

### Status Fields
- **deleted** (Boolean): true if the item is deleted
- **dead** (Boolean): true if the item is dead

### Metadata Fields
- **sourceType** (String): How this item was ingested ("FIREBASE_API", "SINGLE_POST", "ARRAY_POST", "BULK_UPLOAD")
- **batchId** (String): Reference to UploadBatch if ingested via bulk upload
- **ingestedAt** (LocalDateTime): When this item was saved to our system
- **lastUpdated** (LocalDateTime): When this item was last updated

## Relationships

### Parent-Child Hierarchy
- **Parent Relationship**: An HNItem can have a parent (referenced by `parent` field)
- **Children Relationship**: An HNItem can have multiple children (referenced by `kids` field)
- **Root Items**: Stories, jobs, polls, and Ask HN items typically have no parent
- **Comment Items**: Comments have a parent which can be either a story or another comment

### Poll Relationships
- **Poll-PollOpt**: Poll items contain multiple pollopt items (referenced by `parts` field)
- **PollOpt-Poll**: Pollopt items reference their parent poll (referenced by `poll` field)

### Batch Relationship
- **UploadBatch**: HNItems created via bulk upload reference the UploadBatch entity via `batchId`

## Validation Rules

### Required Field Validation
- `hnId` must not be null and must be positive
- `type` must not be null and must be one of the valid types

### Type-Specific Validation
- **Story/Job/Poll**: Should have `title`, may have `url`, `score`, `descendants`
- **Comment**: Should have `text` and `parent`
- **PollOpt**: Should have `text`, `poll`, and `score`

### Hierarchy Validation
- Comments must have a valid `parent` ID
- Poll options must have a valid `poll` ID
- `kids` array should contain valid HNItem IDs

## Business Logic Notes

### State Management
- Entity state is managed internally via `entity.meta.state` (not part of entity schema)
- No explicit status field in the entity - use workflow states for processing status

### Data Integrity
- Firebase HN IDs are unique across all item types
- Parent-child relationships should be maintained consistently
- Deleted/dead items should be preserved for data integrity

### Search Capabilities
- Support full-text search on `title`, `text`, and `by` fields
- Support hierarchical queries (find all comments for a story)
- Support filtering by `type`, `score`, `time` ranges
- Support parent hierarchy traversal for comment threads
