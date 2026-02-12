package com.cyoda.customer.service.impl;

import com.cyoda.customer.Customer;
import com.cyoda.customer.CustomerRepository;
import com.cyoda.customer.dto.CustomerRequest;
import com.cyoda.customer.dto.CustomerResponse;
import com.cyoda.customer.exception.NotFoundException;
import com.cyoda.customer.mapper.CustomerMapper;
import com.cyoda.customer.service.CustomerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository repository;

    @Autowired
    public CustomerServiceImpl(CustomerRepository repository) {
        this.repository = repository;
    }

    @Override
    public CustomerResponse create(CustomerRequest request) {
        repository.findByEmail(request.getEmail()).ifPresent(c -> {
            throw new IllegalArgumentException("Email already exists");
        });
        Customer entity = CustomerMapper.toEntity(request);
        Customer saved = repository.save(entity);
        return CustomerMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID id) {
        Customer c = repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        return CustomerMapper.toResponse(c);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(int page, int size, String sort) {
        Sort s = Sort.by(Sort.Direction.DESC, "createdAt");
        if (sort != null && !sort.isBlank()) {
            // basic parsing: e.g. createdAt,asc
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                s = Sort.by(Sort.Direction.fromString(parts[1]), parts[0]);
            } else {
                s = Sort.by(sort);
            }
        }
        Pageable pageable = PageRequest.of(page, size, s);
        Page<Customer> p = repository.findAll(pageable);
        return p.map(CustomerMapper::toResponse);
    }

    @Override
    public CustomerResponse update(UUID id, CustomerRequest request) {
        Customer existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        if (!existing.getEmail().equals(request.getEmail())) {
            repository.findByEmail(request.getEmail()).ifPresent(c -> {
                throw new IllegalArgumentException("Email already exists");
            });
        }
        CustomerMapper.updateEntityFromRequest(request, existing);
        Customer saved = repository.save(existing);
        return CustomerMapper.toResponse(saved);
    }

    @Override
    public void delete(UUID id) {
        Customer existing = repository.findById(id).orElseThrow(() -> new NotFoundException("Customer not found"));
        repository.delete(existing);
    }
}
