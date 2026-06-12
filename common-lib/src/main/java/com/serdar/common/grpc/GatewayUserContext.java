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
        Long viewer = VIEWER_ID.get();
        if (viewer != null) {
            return viewer;
        }
        return GrpcActorContext.current();
    }
}
