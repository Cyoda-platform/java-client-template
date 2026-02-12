package com.cyoda.customer.mapper;

import com.cyoda.customer.Customer;
import com.cyoda.customer.dto.CustomerRequest;
import com.cyoda.customer.dto.CustomerResponse;

public class CustomerMapper {

    public static Customer toEntity(CustomerRequest req) {
        Customer c = new Customer();
        c.setFirstName(req.getFirstName());
        c.setLastName(req.getLastName());
        c.setEmail(req.getEmail());
        return c;
    }

    public static CustomerResponse toResponse(Customer c) {
        if (c == null) return null;
        return new CustomerResponse(
                c.getId(),
                c.getFirstName(),
                c.getLastName(),
                c.getEmail(),
                c.getCreatedAt(),
                c.getUpdatedAt()
        );
    }

    public static void updateEntityFromRequest(CustomerRequest req, Customer c) {
        if (req.getFirstName() != null) c.setFirstName(req.getFirstName());
        if (req.getLastName() != null) c.setLastName(req.getLastName());
        if (req.getEmail() != null) c.setEmail(req.getEmail());
    }
}
