package com.cyoda.customer.service;

import com.cyoda.customer.dto.CustomerRequest;
import com.cyoda.customer.dto.CustomerResponse;
import org.springframework.data.domain.Page;

import java.util.UUID;

public interface CustomerService {
    CustomerResponse create(CustomerRequest request);
    CustomerResponse getById(UUID id);
    Page<CustomerResponse> list(int page, int size, String sort);
    CustomerResponse update(UUID id, CustomerRequest request);
    void delete(UUID id);
}
