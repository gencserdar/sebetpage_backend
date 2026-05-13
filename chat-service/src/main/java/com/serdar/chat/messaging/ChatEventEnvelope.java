package com.serdar.chat.messaging;

public record ChatEventEnvelope(long userId, byte[] eventBytes, String originId) {}
