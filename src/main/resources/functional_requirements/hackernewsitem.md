# HackerNewsItem Entity Requirements

## Overview
The HackerNewsItem entity represents a single item from the Hacker News platform, following the exact JSON format of the Firebase HN API. This entity supports all types of HN items: stories, comments, jobs, Ask HNs, polls, and poll options.

## Entity Name
- **Entity Name**: `HackerNewsItem`
- **Package**: `com.java_template.application.entity.hackernewsitem.version_1`
- **Model Key**: `HackerNewsItem`
- **Version**: 1

## Required Fields

### Primary Identifier
- **id** (Long, required)
  - The item's unique identifier from Hacker News
  - Must be positive integer
  - Used as primary business key

### Core Classification
- **type** (String, required)
  - The type of item
  - Valid values: "job", "story", "comment", "poll", "pollopt"
  - Used for filtering and processing logic

## Optional Fields

### Author Information
- **by** (String, optional)
  - The username of the item's author
  - May be null for deleted items

### Temporal Information
- **time** (Long, optional)
  - Creation date of the item in Unix timestamp
  - Represents seconds since epoch

### Content Fields
- **text** (String, optional)
  - The comment, story or poll text
  - Contains HTML markup
  - May be null for URL-only stories

- **title** (String, optional)
  - The title of the story, poll or job
  - Contains HTML markup
  - Required for stories, jobs, and polls

- **url** (String, optional)
  - The URL of the story
  - Only applicable to story type items

### Scoring and Engagement
- **score** (Integer, optional)
  - The story's score or votes for a pollopt
  - May be null for comments

- **descendants** (Integer, optional)
  - Total comment count for stories or polls
  - Calculated field representing tree size

### Hierarchical Relationships
- **kids** (List<Long>, optional)
  - Array of child comment IDs
  - Ordered by display ranking
  - Empty list if no children

- **parent** (Long, optional)
  - Parent comment or story ID
  - Null for top-level stories

### Poll-Specific Fields
- **poll** (Long, optional)
  - Associated poll ID for pollopt items
  - Only used by pollopt type

- **parts** (List<Long>, optional)
  - Array of related pollopt IDs for polls
  - Ordered by display sequence
  - Only used by poll type

### Status Fields
- **deleted** (Boolean, optional)
  - True if the item is deleted
  - Defaults to false

- **dead** (Boolean, optional)
  - True if the item is dead (flagged)
  - Defaults to false

## Entity State Management
- Entity state is managed internally via `entity.meta.state`
- No explicit status field in the entity schema
- State transitions control the lifecycle of HN items

## Validation Rules
1. **id** must be present and positive
2. **type** must be one of the valid enum values
3. **parent** cannot reference itself (no circular references)
4. **poll** field only valid when type is "pollopt"
5. **parts** field only valid when type is "poll"
6. **url** field primarily used with "story" type
7. **title** should be present for "story", "job", and "poll" types

## Relationships with Other Entities
- **Self-referential**: Items can reference other HackerNewsItem entities via parent/kids relationships
- **Hierarchical**: Forms tree structures for comment threads
- **Poll relationships**: Polls link to their poll options via parts field

## Search and Query Requirements
- Support full-text search on title and text fields
- Support filtering by type, author (by), score ranges
- Support hierarchical queries (find all children of a parent)
- Support temporal queries (time-based filtering)
- Support parent hierarchy traversal for comment threads

## Data Source Integration
- Primary source: Firebase HN API (https://hacker-news.firebaseio.com/v0/)
- Support bulk import from JSON files
- Support individual item creation via API
- Support array-based batch creation
