package com.java_template.common.grpc.client;

// ABOUTME: Unit tests for ClientAuthorizationInterceptor OBO fail-fast and M2M pass-through behavior.

import com.java_template.common.auth.Authentication;
import com.java_template.common.auth.OboTokenException;
import io.grpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.ClientAuthorizationException;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2Error;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClientAuthorizationInterceptorTest {

    @Mock
    private Authentication authentication;

    @Mock
    private Channel channel;

    @Mock
    private ClientCall<Object, Object> clientCall;

    @SuppressWarnings("unchecked")
    private ClientCall.Listener<Object> listener;

    private ClientAuthorizationInterceptor interceptor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        interceptor = new ClientAuthorizationInterceptor(authentication);
        listener = mock(ClientCall.Listener.class);
        when(channel.newCall(any(), any())).thenReturn(clientCall);
    }

    @SuppressWarnings("unchecked")
    @Test
    void start_closesCallWithUnauthenticated_onOboTokenException() {
        when(authentication.getAccessToken()).thenThrow(new OboTokenException("exchange failed"));

        MethodDescriptor<Object, Object> method = buildMethod();
        ClientCall<Object, Object> call = interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        call.start(listener, new Metadata());

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        verify(listener).onClose(statusCaptor.capture(), any(Metadata.class));
        assertThat(statusCaptor.getValue().getCode()).isEqualTo(Status.Code.UNAUTHENTICATED);

        // super.start() should NOT have been called on the underlying call
        verify(clientCall, never()).start(any(), any());
    }

    @SuppressWarnings("unchecked")
    @Test
    void start_proceedsWithoutHeader_onClientAuthorizationException() {
        when(authentication.getAccessToken())
                .thenThrow(new ClientAuthorizationException(new OAuth2Error("access_denied"), "cyoda"));

        MethodDescriptor<Object, Object> method = buildMethod();
        ClientCall<Object, Object> call = interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        call.start(listener, new Metadata());

        // listener.onClose() should NOT have been called (call was not cancelled)
        verify(listener, never()).onClose(any(), any());
        // The call should still proceed via super.start()
        verify(clientCall).start(eq(listener), any(Metadata.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void start_addsAuthorizationHeader_onSuccess() {
        OAuth2AccessToken token = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "valid-token",
                Instant.now(), Instant.now().plusSeconds(3600));
        when(authentication.getAccessToken()).thenReturn(token);

        MethodDescriptor<Object, Object> method = buildMethod();
        ClientCall<Object, Object> call = interceptor.interceptCall(method, CallOptions.DEFAULT, channel);
        ArgumentCaptor<Metadata> headersCaptor = ArgumentCaptor.forClass(Metadata.class);
        call.start(listener, new Metadata());

        verify(clientCall).start(eq(listener), headersCaptor.capture());
        Metadata capturedHeaders = headersCaptor.getValue();
        String authValue = capturedHeaders.get(
                Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER));
        assertThat(authValue).isEqualTo("Bearer valid-token");
    }

    @SuppressWarnings("unchecked")
    private MethodDescriptor<Object, Object> buildMethod() {
        return MethodDescriptor.<Object, Object>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName("test/TestMethod")
                .setRequestMarshaller(mock(MethodDescriptor.Marshaller.class))
                .setResponseMarshaller(mock(MethodDescriptor.Marshaller.class))
                .build();
    }
}
