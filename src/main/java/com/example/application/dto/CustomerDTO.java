package com.example.application.dto;

import com.example.application.entity.customer.version_1.Customer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Customer Data Transfer Object
 * Used for API request/response payloads with validation.
 * Provides bidirectional mapping with Customer entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerDTO {
    private UUID customerId;

    @NotBlank(message = "First name is required")
    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[+]?[0-9]{10,15}$", message = "Phone must be a valid international format")
    private String phone;

    @NotNull(message = "Date of birth is required")
    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dateOfBirth;

    @NotNull(message = "Status is required")
    private String status;

    @Valid
    @NotNull(message = "Address is required")
    private AddressDTO address;

    @Valid
    @NotNull(message = "KYC information is required")
    private KYCDTO kyc;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean softDeleted;

    /**
     * Convert Entity to DTO
     */
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

    /**
     * Convert DTO to Entity
     */
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

    /**
     * Address DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AddressDTO {
        @NotBlank(message = "Street is required")
        private String street;

        @NotBlank(message = "City is required")
        private String city;

        @NotBlank(message = "State is required")
        private String state;

        @NotBlank(message = "Postal code is required")
        private String postalCode;

        @NotBlank(message = "Country is required")
        private String country;

        public static AddressDTO fromEntity(Customer.Address entity) {
            if (entity == null) return null;
            return AddressDTO.builder()
                    .street(entity.getStreet())
                    .city(entity.getCity())
                    .state(entity.getState())
                    .postalCode(entity.getPostalCode())
                    .country(entity.getCountry())
                    .build();
        }

        public Customer.Address toEntity() {
            Customer.Address entity = new Customer.Address();
            entity.setStreet(this.street);
            entity.setCity(this.city);
            entity.setState(this.state);
            entity.setPostalCode(this.postalCode);
            entity.setCountry(this.country);
            return entity;
        }
    }

    /**
     * KYC DTO
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class KYCDTO {
        @NotNull(message = "KYC status is required")
        private String kycStatus;

        private String kycDocumentType;
        private String kycDocumentId;

        public static KYCDTO fromEntity(Customer.KYC entity) {
            if (entity == null) return null;
            return KYCDTO.builder()
                    .kycStatus(entity.getKycStatus() != null ? entity.getKycStatus().name() : null)
                    .kycDocumentType(entity.getKycDocumentType())
                    .kycDocumentId(entity.getKycDocumentId())
                    .build();
        }

        public Customer.KYC toEntity() {
            Customer.KYC entity = new Customer.KYC();
            entity.setKycStatus(this.kycStatus != null ? Customer.KYCStatus.valueOf(this.kycStatus) : null);
            entity.setKycDocumentType(this.kycDocumentType);
            entity.setKycDocumentId(this.kycDocumentId);
            return entity;
        }
    }
}

