package com.serdar.common.grpc;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.ClientInterceptor;
import io.grpc.ForwardingClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;

public class InternalGrpcClientInterceptor implements ClientInterceptor {

    private final String token;

    public InternalGrpcClientInterceptor(String token) {
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
                Long actingUserId = GatewayUserContext.currentViewerId();
                if (actingUserId != null) {
                    headers.put(InternalGrpcAuth.GATEWAY_USER_ID_HEADER, String.valueOf(actingUserId));
                }
                super.start(responseListener, headers);
            }
        };
    }
}
