# UploadBatch Entity Requirements

## Overview
The UploadBatch entity tracks bulk upload operations of HN items from JSON files. It provides audit trail, progress tracking, and error handling for batch processing operations.

## Entity Name
- **Entity Name**: UploadBatch
- **Version**: 1
- **Package**: `com.java_template.application.entity.uploadbatch.version_1`

## Attributes

### Required Fields
- **batchId** (String): Unique identifier for the batch upload operation - primary business identifier
- **fileName** (String): Original name of the uploaded JSON file
- **uploadedAt** (LocalDateTime): Timestamp when the file was uploaded

### File Information
- **fileSize** (Long): Size of the uploaded file in bytes
- **fileMd5Hash** (String): MD5 hash of the file content for integrity verification
- **contentType** (String): MIME type of the uploaded file (should be application/json)

### Processing Metrics
- **totalItemsInFile** (Integer): Total number of HN items found in the JSON file
- **itemsProcessed** (Integer): Number of items successfully processed so far
- **itemsSkipped** (Integer): Number of items skipped (duplicates, invalid data)
- **itemsErrored** (Integer): Number of items that failed processing

### Processing Status
- **processingStartedAt** (LocalDateTime): When batch processing began
- **processingCompletedAt** (LocalDateTime): When batch processing finished
- **estimatedCompletionTime** (LocalDateTime): Estimated completion time (calculated during processing)

### Error Handling
- **errorSummary** (String): Summary of errors encountered during processing
- **errorDetails** (List<BatchError>): Detailed error information for failed items

### Metadata
- **uploadedBy** (String): User or system that initiated the upload
- **processingNode** (String): Which processing node handled this batch
- **retryCount** (Integer): Number of times this batch has been retried
- **lastRetryAt** (LocalDateTime): Timestamp of last retry attempt

## Nested Classes

### BatchError
Represents individual errors encountered during batch processing:
- **itemIndex** (Integer): Index of the item in the original file
- **hnId** (Long): HN ID of the item that failed (if parseable)
- **errorType** (String): Type of error ("VALIDATION", "DUPLICATE", "PROCESSING", "UNKNOWN")
- **errorMessage** (String): Detailed error message
- **occurredAt** (LocalDateTime): When the error occurred

## Relationships

### HNItem Relationship
- **One-to-Many**: One UploadBatch can create multiple HNItems
- **Reference**: HNItems reference their UploadBatch via `batchId` field
- **Cascade**: When querying batch results, can retrieve all associated HNItems

## Validation Rules

### Required Field Validation
- `batchId` must not be null and must be unique
- `fileName` must not be null and not empty
- `uploadedAt` must not be null

### File Validation
- `fileSize` must be positive
- `fileMd5Hash` must be valid MD5 format
- `contentType` should be "application/json" or similar JSON MIME type

### Metrics Validation
- All count fields (`totalItemsInFile`, `itemsProcessed`, etc.) must be non-negative
- `itemsProcessed + itemsSkipped + itemsErrored` should not exceed `totalItemsInFile`

### Timestamp Validation
- `processingStartedAt` should be after `uploadedAt`
- `processingCompletedAt` should be after `processingStartedAt` (when set)

## Business Logic Notes

### State Management
- Entity state managed via `entity.meta.state` workflow states
- Typical states: "uploaded", "processing", "completed", "failed", "retrying"

### Progress Tracking
- Calculate progress percentage: `(itemsProcessed + itemsSkipped + itemsErrored) / totalItemsInFile * 100`
- Estimate completion time based on processing rate

### Error Handling Strategy
- Continue processing other items when individual items fail
- Collect all errors for post-processing review
- Support retry mechanisms for failed batches

### Duplicate Handling
- Track skipped items that are duplicates of existing HNItems
- Provide option to update existing items vs skip duplicates

### Performance Considerations
- Large files should be processed in chunks
- Progress should be updated periodically during processing
- Consider async processing for large batches
