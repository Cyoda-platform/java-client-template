# HN Item Entity Requirements

## Entity Overview
The **HN Item** entity represents individual Hacker News items from the Firebase HN API. This entity supports all types of HN content including stories, comments, jobs, Ask HNs, and polls.

## Entity Name
- **Entity Name**: `HnItem`
- **Package**: `com.java_template.application.entity.hnitem.version_1`
- **Version**: 1

## Attributes

### Required Fields
- **id** (Long): The item's unique identifier from Hacker News. This is the primary business identifier.
- **type** (String): The type of item. One of "job", "story", "comment", "poll", or "pollopt".

### Optional Fields Based on Firebase HN API
- **deleted** (Boolean): true if the item is deleted.
- **by** (String): The username of the item's author.
- **time** (Long): Creation date of the item, in Unix Time.
- **text** (String): The comment, story or poll text. HTML format.
- **dead** (Boolean): true if the item is dead.
- **parent** (Long): The comment's parent: either another comment or the relevant story.
- **poll** (Long): The pollopt's associated poll.
- **kids** (List<Long>): The ids of the item's comments, in ranked display order.
- **url** (String): The URL of the story.
- **score** (Integer): The story's score, or the votes for a pollopt.
- **title** (String): The title of the story, poll or job. HTML format.
- **parts** (List<Long>): A list of related pollopts, in display order.
- **descendants** (Integer): In the case of stories or polls, the total comment count.

### System Fields
- **createdAt** (LocalDateTime): When the entity was created in our system.
- **updatedAt** (LocalDateTime): When the entity was last updated in our system.
- **sourceUrl** (String): The Firebase API URL where this item was retrieved from.

## Validation Rules
1. **id** must not be null (required business identifier)
2. **type** must be one of: "job", "story", "comment", "poll", "pollopt"
3. If **type** is "comment", **parent** should be present
4. If **type** is "pollopt", **poll** should be present
5. If **type** is "poll", **parts** should be present and not empty

## Relationships
- **Parent-Child Hierarchy**: Comments reference their parent items via the **parent** field
- **Poll Relationships**: Poll options reference their poll via the **poll** field, polls reference their options via **parts**
- **Comment Trees**: Items can have child comments referenced via the **kids** field

## Business Rules
1. The **id** field corresponds directly to the Hacker News item ID from the Firebase API
2. The entity supports the full Firebase HN API JSON structure
3. HTML content in **text** and **title** fields should be preserved as-is
4. Unix timestamps in **time** field should be handled appropriately
5. The **kids** array represents the comment hierarchy and should maintain order

## Use Cases
1. **Firebase API Integration**: Store items retrieved from Firebase HN API
2. **Single Item Creation**: Accept individual HN items via POST API
3. **Bulk Operations**: Support array of items and file uploads
4. **Hierarchical Queries**: Enable searching with parent-child relationships
5. **Content Management**: Support all HN item types (stories, comments, jobs, polls)

## Notes
- Entity state is managed internally via `entity.meta.state` and should not appear in the entity schema
- The Firebase API structure is preserved to maintain compatibility
- All optional fields support the full range of HN item variations
- The entity supports both read operations (from Firebase API) and write operations (via REST API)
