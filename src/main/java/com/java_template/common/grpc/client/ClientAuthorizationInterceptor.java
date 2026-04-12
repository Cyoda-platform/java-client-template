package com.java_template.common.grpc.client;

import com.java_template.common.auth.Authentication;
import com.java_template.common.auth.OboTokenException;
import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;

/**
 * ABOUTME: gRPC client interceptor that adds OAuth2 authorization headers
 * to outgoing requests for secure communication with Cyoda services.
 */
public class ClientAuthorizationInterceptor implements ClientInterceptor {
    private static final Logger LOG = LoggerFactory.getLogger(ClientAuthorizationInterceptor.class);

    private final Authentication authentication;

    public ClientAuthorizationInterceptor(Authentication authentication) {
        this.authentication = authentication;
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(MethodDescriptor<ReqT, RespT> method, CallOptions callOptions, Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                try {
                    OAuth2AccessToken accessToken = authentication.getAccessToken();
                    Metadata.Key<String> authKey = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);
                    headers.put(authKey, "Bearer " + accessToken.getTokenValue());

                } catch (OboTokenException e) {
                    LOG.error("OBO token exchange failed — cancelling Cyoda call: {}", e.getMessage(), e);
                    responseListener.onClose(
                            Status.UNAUTHENTICATED
                                    .withDescription("OBO token exchange failed: " + e.getMessage())
                                    .withCause(e),
                            new Metadata()
                    );
                    return;

                } catch (ClientAuthorizationException e) {
                    LOG.error("M2M token unavailable — proceeding without auth header: {}",
                            e.getError().getDescription());

                } catch (Exception e) {
                    LOG.error("Unexpected error obtaining access token — proceeding without auth header", e);
                }
                super.start(responseListener, headers);
            }
        };
    }
}
