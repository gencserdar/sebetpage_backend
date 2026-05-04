package com.serdar.common.grpc;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class InternalGrpcServerInterceptor implements ServerInterceptor {

    private final String expectedToken;

    public InternalGrpcServerInterceptor(String expectedToken) {
        this.expectedToken = InternalGrpcAuth.requireToken(expectedToken);
    }

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        String suppliedToken = headers.get(InternalGrpcAuth.TOKEN_HEADER);
        if (!InternalGrpcAuth.matches(expectedToken, suppliedToken)) {
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid internal gRPC token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
