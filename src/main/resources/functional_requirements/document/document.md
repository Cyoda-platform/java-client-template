# Document Entity

## Overview
Represents documents attached to submissions with version control and audit trail capabilities.

## Attributes
- **fileName**: String - Original document file name
- **fileType**: String - Document MIME type
- **fileSize**: Long - File size in bytes
- **submissionId**: String - UUID of associated submission
- **version**: Integer - Document version number
- **uploadedBy**: String - Email of user who uploaded the document
- **uploadDate**: LocalDateTime - When document was uploaded
- **checksum**: String - File integrity checksum
- **filePath**: String - Storage path reference

## Relationships
- One Document belongs to one Submission
- One Document is uploaded by one User
- Document state managed internally via `entity.meta.state`

## Business Rules
- Version numbers increment automatically for the same document
- Checksum ensures file integrity
- Only submitter or assigned reviewer can upload documents
- File size limits apply based on document type
