package com.java_template.common.workflow;

// ABOUTME: Enum representing the type of access being requested on a Cyoda entity.
// Used by EntityAccessAuthorizer to dispatch authorization decisions.
public enum AccessRequest {
    CREATE, READ, UPDATE, DELETE
}
