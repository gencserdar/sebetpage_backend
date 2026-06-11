package com.serdar.gateway.grpc;

import com.serdar.common.grpc.GrpcActorContext;
import com.serdar.common.grpc.InternalGrpcAuth;
import com.serdar.gateway.security.AuthenticatedUser;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Attaches the internal service token and the gateway-authenticated user id so
 * downstream services can reject token-only impersonation.
 */
public class GatewayGrpcClientInterceptor implements ClientInterceptor {

    private final String token;

    public GatewayGrpcClientInterceptor(String token) {
        this.token = InternalGrpcAuth.requireToken(token);
    }

    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, callOptions)) {
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(InternalGrpcAuth.TOKEN_HEADER, token);
                Long gatewayUserId = currentGatewayUserId();
                if (gatewayUserId == null) {
                    gatewayUserId = GrpcActorContext.current();
                }
                if (gatewayUserId != null) {
                    headers.put(InternalGrpcAuth.GATEWAY_USER_ID_HEADER, String.valueOf(gatewayUserId));
                }
                super.start(responseListener, headers);
            }
        };
    }

    private static Long currentGatewayUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof AuthenticatedUser user)) {
            return null;
        }
        return user.id();
    }
}
