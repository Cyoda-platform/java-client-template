package com.java_template.application.entity.user.version_1;

import lombok.Data;

@Data
public class UserExtras {
    private String password; // plaintext for validation in demo only
    private String externalId;

    public UserExtras() {}
}
