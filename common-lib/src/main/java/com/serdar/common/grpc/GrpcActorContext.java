package com.serdar.common.grpc;

import io.grpc.Context;

import java.util.concurrent.Callable;

/**
 * Propagates the acting user id on internal gRPC client calls from backend
 * services (chat → user, etc.). Downstream actor checks treat this the same as
 * the gateway's {@link InternalGrpcAuth#GATEWAY_USER_ID_HEADER}.
 */
public final class GrpcActorContext {

    public static final Context.Key<Long> ACTING_USER_ID = Context.key("acting-user-id");

    private GrpcActorContext() {}

    public static Long current() {
        return ACTING_USER_ID.get();
    }

    public static void runAs(long userId, Runnable action) {
        callAs(userId, () -> {
            action.run();
            return null;
        });
    }

    public static <T> T callAs(long userId, Callable<T> action) {
        Context ctx = Context.current().withValue(ACTING_USER_ID, userId);
        Context previous = ctx.attach();
        try {
            return action.call();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            ctx.detach(previous);
        }
    }
}
