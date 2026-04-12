package com.java_template.common.auth;

import io.cloudevents.v1.proto.CloudEvent;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Extracts CloudEvents Auth Context Extension attributes from incoming gRPC CloudEvents.
 * Returns an empty Optional when no auth context attributes are present.
 */
@Component
public class CloudEventAuthContextExtractor {

    private static final String ATTR_AUTHTYPE   = "authtype";
    private static final String ATTR_AUTHID     = "authid";
    private static final String ATTR_AUTHCLAIMS = "authclaims";

    public Optional<CloudEventAuthContext> extract(CloudEvent cloudEvent) {
        var attrs = cloudEvent.getAttributesMap();
        if (!attrs.containsKey(ATTR_AUTHTYPE)) {
            return Optional.empty();
        }
        String authType   = attrs.get(ATTR_AUTHTYPE).getCeString();
        String authId     = attrs.containsKey(ATTR_AUTHID) ? attrs.get(ATTR_AUTHID).getCeString() : null;
        String authClaims = attrs.containsKey(ATTR_AUTHCLAIMS) ? attrs.get(ATTR_AUTHCLAIMS).getCeString() : null;

        return Optional.of(new CloudEventAuthContext(authType, authId, authClaims));
    }
}
