package com.serdar.personal.exception;

public class NicknameAlreadyUsedException extends RuntimeException {
    public NicknameAlreadyUsedException() {
        super("Username already in use");
    }

    public NicknameAlreadyUsedException(String message) {
        super(message);
    }
}
