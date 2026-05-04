package com.serdar.common;

/**
 * Domain exception that services translate into gRPC status codes.
 * Using a small set of codes keeps the mapping in each service simple.
 */
public class ServiceException extends RuntimeException {
    public enum Code { NOT_FOUND, CONFLICT, INVALID_ARGUMENT, UNAUTHENTICATED, PERMISSION_DENIED, FAILED_PRECONDITION, INTERNAL }

    private final Code code;

    public ServiceException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code code() { return code; }

    public static ServiceException notFound(String msg)   { return new ServiceException(Code.NOT_FOUND, msg); }
    public static ServiceException conflict(String msg)   { return new ServiceException(Code.CONFLICT, msg); }
    public static ServiceException invalid(String msg)    { return new ServiceException(Code.INVALID_ARGUMENT, msg); }
    public static ServiceException unauth(String msg)     { return new ServiceException(Code.UNAUTHENTICATED, msg); }
    public static ServiceException forbidden(String msg)  { return new ServiceException(Code.PERMISSION_DENIED, msg); }
    public static ServiceException precondition(String m) { return new ServiceException(Code.FAILED_PRECONDITION, m); }
}
