# HNItem Entity

## Description
Represents a Hacker News item following the Firebase HN API JSON format. Supports all item types: story, comment, job, poll, and pollopt.

## Attributes
- **id**: Unique integer identifier (required)
- **type**: Item type - "story", "comment", "job", "poll", or "pollopt" (required)
- **by**: Username of the author
- **time**: Creation timestamp (Unix time)
- **title**: Title for stories, polls, or jobs (HTML)
- **text**: Content text (HTML)
- **url**: URL for stories
- **score**: Score/votes for stories and pollopts
- **parent**: Parent item ID for comments
- **kids**: Array of child comment IDs
- **descendants**: Total comment count for stories/polls
- **parts**: Related pollopt IDs for polls
- **poll**: Associated poll ID for pollopts
- **deleted**: Boolean flag for deleted items
- **dead**: Boolean flag for dead items

## Relationships
- Self-referential: parent-child relationships for comments
- Poll-pollopt relationships
- Hierarchical comment trees

## Notes
Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema.
