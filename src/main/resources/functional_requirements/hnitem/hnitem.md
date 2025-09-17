# HNItem Entity Specification

## Overview
The HNItem entity represents a single Hacker News item in the JSON format of the Firebase HN API. This entity can represent stories, comments, jobs, Ask HNs, and polls.

## Entity Name
- **Entity Name**: HNItem
- **Package**: `com.java_template.application.entity.hnitem.version_1`
- **Class Name**: HNItem

## Attributes

### Required Fields
- **id** (Long): The item's unique id from Hacker News
- **type** (String): The type of item. One of "job", "story", "comment", "poll", or "pollopt"

### Optional Fields
- **deleted** (Boolean): true if the item is deleted
- **by** (String): The username of the item's author
- **time** (Long): Creation date of the item, in Unix Time
- **text** (String): The comment, story or poll text. HTML
- **dead** (Boolean): true if the item is dead
- **parent** (Long): The comment's parent: either another comment or the relevant story
- **poll** (Long): The pollopt's associated poll
- **kids** (List<Long>): The ids of the item's comments, in ranked display order
- **url** (String): The URL of the story
- **score** (Integer): The story's score, or the votes for a pollopt
- **title** (String): The title of the story, poll or job. HTML
- **parts** (List<Long>): A list of related pollopts, in display order
- **descendants** (Integer): In the case of stories or polls, the total comment count

### Technical Fields
- **createdAt** (LocalDateTime): When the entity was created in our system
- **updatedAt** (LocalDateTime): When the entity was last updated in our system
- **sourceType** (String): How this item was added ("API_PULL", "SINGLE_POST", "ARRAY_POST", "BULK_UPLOAD")

## Relationships
- **Parent-Child**: HNItem can reference other HNItems through the `parent` field
- **Comments**: HNItem can have child comments referenced through the `kids` field
- **Poll Options**: Poll items can have related poll options through the `parts` field

## Validation Rules
1. **id** must not be null
2. **type** must be one of: "job", "story", "comment", "poll", "pollopt"
3. If **type** is "comment", **parent** should not be null
4. If **type** is "poll", **parts** should not be empty
5. If **type** is "pollopt", **poll** should not be null

## Business Logic
- The entity represents the exact structure from Firebase HN API
- No transformation of the original data structure
- Supports hierarchical queries through parent-child relationships
- Enables search functionality across all fields

## State Management
- Entity state is managed internally via `entity.meta.state`
- No explicit status field in the entity schema
- State transitions are handled through workflow definitions

## Notes
- This entity closely mirrors the Firebase Hacker News API structure
- All fields from the original API are preserved
- Additional technical fields are added for system management
- The entity supports all types of HN items (stories, comments, jobs, polls, poll options)
