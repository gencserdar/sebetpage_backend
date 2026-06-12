package com.serdar.common.grpc;

import com.google.protobuf.Message;
import io.grpc.Context;
import io.grpc.ForwardingServerCallListener;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * Rejects internal gRPC calls where a declared actor id does not match the gateway's
 * authenticated user id header.
 */
public class InternalGrpcActorServerInterceptor implements ServerInterceptor {

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        ServerCall.Listener<ReqT> delegate = next.startCall(call, headers);
        String fullMethod = call.getMethodDescriptor().getFullMethodName();
        return new ForwardingServerCallListener.SimpleForwardingServerCallListener<>(delegate) {
            @Override
            public void onMessage(ReqT message) {
                Long gatewayUserId = InternalGrpcAuth.parseGatewayUserId(headers);
                if (gatewayUserId == null) {
                    dispatch(message);
                    return;
                }
                Context ctx = Context.current().withValue(GatewayUserContext.VIEWER_ID, gatewayUserId);
                Context previous = ctx.attach();
                try {
                    dispatch(message);
                } finally {
                    ctx.detach(previous);
                }
            }

            private void dispatch(ReqT message) {
                if (message instanceof Message proto) {
                    GrpcGatewayActorRules.ruleFor(fullMethod).ifPresent(rule -> {
                        Long gatewayUserId = InternalGrpcAuth.parseGatewayUserId(headers);
                        if (gatewayUserId == null) {
                            throw denied("Missing gateway user context");
                        }
                        if (!GrpcGatewayActorRules.actorMatches(proto, rule, gatewayUserId)) {
                            throw denied("Gateway user does not match request actor");
                        }
                    });
                }
                super.onMessage(message);
            }
        };
    }

    private static StatusRuntimeException denied(String description) {
        return Status.PERMISSION_DENIED.withDescription(description).asRuntimeException();
    }
}

