# Customer Management API - Code Snippets Reference

## Customer Entity - Key Sections

### Entity Declaration
```java
@Data
public class Customer implements CyodaEntity {
    public static final String ENTITY_NAME = "Customer";
    public static final Integer ENTITY_VERSION = 1;
    
    @NotNull(message = "Customer ID cannot be null")
    private UUID customerId;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @NotNull(message = "Status is required")
    private CustomerStatus status;
    
    @Valid
    @NotNull(message = "Address is required")
    private Address address;
    
    @Valid
    @NotNull(message = "KYC information is required")
    private KYC kyc;
    
    private Boolean softDeleted = false;
}
```

### Nested Address Component
```java
@Data
public static class Address {
    @NotBlank(message = "Street is required")
    @Size(max = 255, message = "Street must not exceed 255 characters")
    private String street;
    
    @NotBlank(message = "City is required")
    @Size(max = 100, message = "City must not exceed 100 characters")
    private String city;
    
    @NotBlank(message = "State is required")
    @Size(max = 100, message = "State must not exceed 100 characters")
    private String state;
    
    @NotBlank(message = "Postal code is required")
    @Size(max = 20, message = "Postal code must not exceed 20 characters")
    private String postalCode;
    
    @NotBlank(message = "Country is required")
    @Size(max = 100, message = "Country must not exceed 100 characters")
    private String country;
}
```

### Nested KYC Component
```java
@Data
public static class KYC {
    @NotNull(message = "KYC status is required")
    private KYCStatus kycStatus;
    
    @Size(max = 100, message = "Document type must not exceed 100 characters")
    private String kycDocumentType;
    
    @Size(max = 255, message = "Document ID must not exceed 255 characters")
    private String kycDocumentId;
}
```

### Enums
```java
public enum CustomerStatus {
    NEW,
    VERIFIED,
    SUSPENDED
}

public enum KYCStatus {
    PENDING,
    VERIFIED,
    REJECTED
}
```

### CyodaEntity Implementation
```java
@Override
public OperationSpecification getModelKey() {
    ModelSpec modelSpec = new ModelSpec();
    modelSpec.setName(ENTITY_NAME);
    modelSpec.setVersion(ENTITY_VERSION);
    return new OperationSpecification.Entity(modelSpec, ENTITY_NAME);
}

@Override
public boolean isValid(EntityMetadata metadata) {
    return customerId != null && 
           firstName != null && !firstName.isBlank() &&
           lastName != null && !lastName.isBlank() &&
           email != null && !email.isBlank() &&
           phone != null && !phone.isBlank() &&
           dateOfBirth != null &&
           status != null &&
           address != null &&
           kyc != null;
}
```

## CustomerDTO - Bidirectional Mapping

### Entity to DTO
```java
public static CustomerDTO fromEntity(Customer entity) {
    if (entity == null) {
        return null;
    }
    return CustomerDTO.builder()
            .customerId(entity.getCustomerId())
            .firstName(entity.getFirstName())
            .lastName(entity.getLastName())
            .email(entity.getEmail())
            .phone(entity.getPhone())
            .dateOfBirth(entity.getDateOfBirth())
            .status(entity.getStatus() != null ? entity.getStatus().name() : null)
            .address(AddressDTO.fromEntity(entity.getAddress()))
            .kyc(KYCDTO.fromEntity(entity.getKyc()))
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .softDeleted(entity.getSoftDeleted())
            .build();
}
```

### DTO to Entity
```java
public Customer toEntity() {
    Customer entity = new Customer();
    entity.setCustomerId(this.customerId);
    entity.setFirstName(this.firstName);
    entity.setLastName(this.lastName);
    entity.setEmail(this.email);
    entity.setPhone(this.phone);
    entity.setDateOfBirth(this.dateOfBirth);
    entity.setStatus(this.status != null ? Customer.CustomerStatus.valueOf(this.status) : null);
    entity.setAddress(this.address != null ? this.address.toEntity() : null);
    entity.setKyc(this.kyc != null ? this.kyc.toEntity() : null);
    entity.setCreatedAt(this.createdAt);
    entity.setUpdatedAt(this.updatedAt);
    entity.setSoftDeleted(this.softDeleted);
    return entity;
}
```

## AuditLog Entity

### Entity Declaration
```java
@Data
public class AuditLog implements CyodaEntity {
    public static final String ENTITY_NAME = "AuditLog";
    public static final Integer ENTITY_VERSION = 1;
    
    @NotNull(message = "Audit log ID cannot be null")
    private UUID auditLogId;
    
    @NotNull(message = "Entity ID cannot be null")
    private UUID entityId;
    
    @NotBlank(message = "Entity type is required")
    @Size(max = 100, message = "Entity type must not exceed 100 characters")
    private String entityType;
    
    @NotNull(message = "Action is required")
    private AuditAction action;
    
    @NotBlank(message = "Performed by is required")
    @Size(max = 255, message = "Performed by must not exceed 255 characters")
    private String performedBy;
    
    @NotNull(message = "Performed at timestamp is required")
    private LocalDateTime performedAt;
    
    @Size(max = 4000, message = "Details must not exceed 4000 characters")
    private String details;
}
```

### Audit Action Enum
```java
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE
}
```

## JSON Metadata Examples

### Customer.json
```json
{
  "customerId": "550e8400-e29b-41d4-a716-446655440000",
  "firstName": "John",
  "lastName": "Doe",
  "email": "john.doe@example.com",
  "phone": "+14155552671",
  "dateOfBirth": "1990-05-15",
  "status": "NEW",
  "address": {
    "street": "123 Main Street",
    "city": "San Francisco",
    "state": "California",
    "postalCode": "94102",
    "country": "United States"
  },
  "kyc": {
    "kycStatus": "PENDING",
    "kycDocumentType": "PASSPORT",
    "kycDocumentId": "US123456789"
  },
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00",
  "softDeleted": false
}
```

### AuditLog.json
```json
{
  "auditLogId": "660e8400-e29b-41d4-a716-446655440001",
  "entityId": "550e8400-e29b-41d4-a716-446655440000",
  "entityType": "Customer",
  "action": "CREATE",
  "performedBy": "admin@example.com",
  "performedAt": "2024-01-15T10:30:00",
  "details": "Customer record created with initial status NEW and KYC status PENDING"
}
```

## Validation Annotations Used

| Annotation | Purpose | Example |
|-----------|---------|---------|
| @NotNull | Field cannot be null | customerId, status |
| @NotBlank | String cannot be empty | firstName, email |
| @Email | Valid email format | email field |
| @Pattern | Regex pattern matching | phone: `^[+]?[0-9]{10,15}$` |
| @Size | String length constraints | firstName: min=1, max=100 |
| @PastOrPresent | Date validation | dateOfBirth |
| @Valid | Nested validation | address, kyc |

## Lombok Annotations Used

| Annotation | Purpose |
|-----------|---------|
| @Data | Generates getters, setters, equals, hashCode, toString |
| @NoArgsConstructor | Generates no-arg constructor |
| @AllArgsConstructor | Generates all-args constructor |
| @Builder | Generates builder pattern |

## Import Statements

### Entity Imports
```java
import com.java_template.common.workflow.CyodaEntity;
import com.java_template.common.workflow.OperationSpecification;
import lombok.Data;
import org.cyoda.cloud.api.event.common.EntityMetadata;
import org.cyoda.cloud.api.event.common.ModelSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
```

### DTO Imports
```java
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
```

