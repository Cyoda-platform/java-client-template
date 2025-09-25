# Document Controllers

## DocumentController

### Endpoints

#### POST /api/documents
Upload a new document.

**Request Example:**
```json
{
  "fileName": "protocol.pdf",
  "fileType": "application/pdf",
  "fileSize": 2048576,
  "submissionId": "456e7890-e89b-12d3-a456-426614174001",
  "uploadedBy": "researcher@example.com",
  "filePath": "/uploads/documents/protocol_v1.pdf"
}
```

**Response Example:**
```json
{
  "entity": {
    "fileName": "protocol.pdf",
    "fileType": "application/pdf",
    "fileSize": 2048576,
    "submissionId": "456e7890-e89b-12d3-a456-426614174001",
    "version": 1,
    "uploadedBy": "researcher@example.com",
    "uploadDate": "2024-01-15T11:00:00Z",
    "checksum": "sha256:abc123def456...",
    "filePath": "/uploads/documents/protocol_v1.pdf"
  },
  "meta": {
    "uuid": "789e0123-e89b-12d3-a456-426614174002",
    "state": "uploaded",
    "version": 1
  }
}
```

#### PUT /api/documents/{uuid}/validate
Validate document (transition: validate_document).

**Request Example:**
```json
{
  "transitionName": "validate_document"
}
```

#### GET /api/documents/{uuid}
Get document by UUID.

#### GET /api/documents/submission/{submissionId}
List documents for a submission.

#### DELETE /api/documents/{uuid}
Delete document (transition: delete_document).
