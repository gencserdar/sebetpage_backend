package com.serdar.common.grpc;

import io.grpc.Context;

/**
 * Viewer id propagated from api-gateway ({@link InternalGrpcAuth#GATEWAY_USER_ID_HEADER})
 * or backend actor context ({@link GrpcActorContext}).
 */
public final class GatewayUserContext {

    public static final Context.Key<Long> VIEWER_ID = Context.key("gateway-viewer-id");

    private GatewayUserContext() {}

    public static Long currentViewerId() {
        Long acting = GrpcActorContext.current();
        if (acting != null) {
            return acting;
        }
        return VIEWER_ID.get();
    }
}
