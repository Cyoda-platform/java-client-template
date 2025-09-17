# BulkUpload Entity Specification

## Overview
The BulkUpload entity manages the bulk uploading of HN items from JSON files. It tracks the upload process, status, and provides metadata about the bulk operation.

## Entity Name
- **Entity Name**: BulkUpload
- **Package**: `com.java_template.application.entity.bulkupload.version_1`
- **Class Name**: BulkUpload

## Attributes

### Required Fields
- **uploadId** (String): Unique identifier for the bulk upload operation
- **fileName** (String): Name of the uploaded JSON file
- **uploadedAt** (LocalDateTime): When the upload was initiated

### Optional Fields
- **fileSize** (Long): Size of the uploaded file in bytes
- **totalItems** (Integer): Total number of items in the file
- **processedItems** (Integer): Number of items successfully processed
- **failedItems** (Integer): Number of items that failed to process
- **errorMessages** (List<String>): List of error messages encountered during processing
- **uploadedBy** (String): User or system that initiated the upload
- **completedAt** (LocalDateTime): When the upload processing was completed

### Technical Fields
- **createdAt** (LocalDateTime): When the entity was created in our system
- **updatedAt** (LocalDateTime): When the entity was last updated in our system

## Validation Rules
1. **uploadId** must not be null
2. **fileName** must not be null or empty
3. **uploadedAt** must not be null
4. **processedItems** must be >= 0
5. **failedItems** must be >= 0
6. **totalItems** must be >= 0
7. **processedItems + failedItems** should not exceed **totalItems**

## Business Logic
- Tracks the lifecycle of bulk upload operations
- Provides progress tracking during processing
- Maintains error logs for failed items
- Supports audit trail for upload operations

## State Management
- Entity state is managed internally via `entity.meta.state`
- States represent the upload processing lifecycle:
  - UPLOADED: File uploaded, waiting to be processed
  - PROCESSING: Currently processing items
  - COMPLETED: All items processed successfully
  - COMPLETED_WITH_ERRORS: Processing completed but some items failed
  - FAILED: Upload processing failed completely

## Relationships
- **Indirect**: Related to HNItem entities that were created from this upload
- No direct foreign key relationships (managed through workflow)

## Processing Flow
1. File upload creates BulkUpload entity in UPLOADED state
2. Processing begins, state moves to PROCESSING
3. Items are extracted and processed individually
4. Progress is tracked via processedItems and failedItems counters
5. Final state depends on success/failure ratio
6. Error messages are collected for failed items

## Notes
- This entity manages the metadata of bulk operations
- Actual file content is processed but not stored in the entity
- Provides comprehensive tracking and audit capabilities
- Supports error handling and partial success scenarios
