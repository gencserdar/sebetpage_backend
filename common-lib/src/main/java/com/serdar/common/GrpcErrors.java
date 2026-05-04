package com.serdar.common;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/** Map ServiceException -> gRPC status for server-side exception handlers. */
public final class GrpcErrors {
    private GrpcErrors() {}

    public static StatusRuntimeException toGrpc(ServiceException e) {
        Status s = switch (e.code()) {
            case NOT_FOUND           -> Status.NOT_FOUND;
            case CONFLICT            -> Status.ALREADY_EXISTS;
            case INVALID_ARGUMENT    -> Status.INVALID_ARGUMENT;
            case UNAUTHENTICATED     -> Status.UNAUTHENTICATED;
            case PERMISSION_DENIED   -> Status.PERMISSION_DENIED;
            case FAILED_PRECONDITION -> Status.FAILED_PRECONDITION;
            case INTERNAL            -> Status.INTERNAL;
        };
        return s.withDescription(e.getMessage()).asRuntimeException();
    }

    public static RuntimeException toService(StatusRuntimeException e) {
        ServiceException.Code code = switch (e.getStatus().getCode()) {
            case NOT_FOUND           -> ServiceException.Code.NOT_FOUND;
            case ALREADY_EXISTS      -> ServiceException.Code.CONFLICT;
            case INVALID_ARGUMENT    -> ServiceException.Code.INVALID_ARGUMENT;
            case UNAUTHENTICATED     -> ServiceException.Code.UNAUTHENTICATED;
            case PERMISSION_DENIED   -> ServiceException.Code.PERMISSION_DENIED;
            case FAILED_PRECONDITION -> ServiceException.Code.FAILED_PRECONDITION;
            default                  -> ServiceException.Code.INTERNAL;
        };
        return new ServiceException(code, e.getStatus().getDescription() == null ? "rpc error" : e.getStatus().getDescription());
    }
}
