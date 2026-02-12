package com.cyoda.customer.service;

import com.cyoda.customer.Customer;
import com.cyoda.customer.CustomerRepository;
import com.cyoda.customer.dto.CustomerRequest;
import com.cyoda.customer.exception.NotFoundException;
import com.cyoda.customer.service.impl.CustomerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class CustomerServiceTest {

    @Mock
    private CustomerRepository repository;

    @InjectMocks
    private CustomerServiceImpl service;

    @BeforeEach
    public void setup() { MockitoAnnotations.openMocks(this); }

    @Test
    public void testCreateSuccess() {
        CustomerRequest req = new CustomerRequest("Jane", "Doe", "jane.doe@example.com");
        when(repository.findByEmail(req.getEmail())).thenReturn(Optional.empty());
        when(repository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = (Customer) inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            c.setUpdatedAt(c.getCreatedAt());
            return c;
        });

        var resp = service.create(req);
        assertNotNull(resp.getId());
        assertEquals("jane.doe@example.com", resp.getEmail());
    }

    @Test
    public void testCreateDuplicateEmail() {
        CustomerRequest req = new CustomerRequest("Jane", "Doe", "jane.doe@example.com");
        when(repository.findByEmail(req.getEmail())).thenReturn(Optional.of(new Customer()));
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    public void testGetByIdNotFound() {
        when(repository.findById(any(UUID.class))).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById(UUID.randomUUID()));
    }
}
