# HNItemUpload Entity

## Description
Manages bulk upload operations for Hacker News items. Handles single items, arrays of items, and JSON file uploads.

## Attributes
- **uploadId**: Unique identifier for the upload operation (required)
- **uploadType**: Type of upload - "single", "array", or "file" (required)
- **fileName**: Name of uploaded file (for file uploads)
- **totalItems**: Total number of items to process
- **processedItems**: Number of items successfully processed
- **failedItems**: Number of items that failed processing
- **errorMessages**: Array of error messages for failed items
- **uploadTimestamp**: When the upload was initiated
- **completionTimestamp**: When the upload was completed

## Relationships
- References multiple HNItem entities created during upload
- No direct foreign key relationships

## Notes
Entity state tracks upload progress: pending, processing, completed, failed.
State is managed via `entity.meta.state`.
