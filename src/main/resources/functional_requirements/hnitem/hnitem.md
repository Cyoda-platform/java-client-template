# HN Item Entity Requirements

## Entity Name
**HnItem**

## Description
Represents a Hacker News item (story, comment, job, Ask HN, poll, or poll option) in the Firebase HN API JSON format.

## Attributes
- **id** (Long, required): Unique item identifier from HN
- **type** (String, required): Item type - "story", "comment", "job", "poll", or "pollopt"
- **by** (String): Username of the item's author
- **time** (Long): Creation date in Unix timestamp
- **text** (String): Comment, story or poll text (HTML format)
- **url** (String): URL of the story
- **title** (String): Title of story, poll or job (HTML format)
- **score** (Integer): Story score or poll option votes
- **descendants** (Integer): Total comment count for stories/polls
- **parent** (Long): Parent item ID for comments
- **kids** (List<Long>): Child comment IDs in ranked order
- **parts** (List<Long>): Related poll options for polls
- **poll** (Long): Associated poll ID for poll options
- **deleted** (Boolean): True if item is deleted
- **dead** (Boolean): True if item is dead

## Relationships
- **Parent-Child**: Comments reference parent items via `parent` field
- **Poll-Options**: Polls reference options via `parts`, options reference poll via `poll`
- **Hierarchical**: Items form tree structures through parent-child relationships

## Validation Rules
- ID must be unique and positive
- Type must be valid enum value
- Parent must exist if specified
- Poll must exist for poll options
