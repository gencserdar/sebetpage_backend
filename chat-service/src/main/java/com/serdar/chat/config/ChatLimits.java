package com.serdar.chat.config;

import com.serdar.common.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ChatLimits {

    private final int maxMessagingGroupMembers;
    private final int maxGroupTitleChars;
    private final int maxGroupDescriptionChars;
    private final int maxMessageChars;

    public ChatLimits(
            @Value("${app.messaging-groups.max-members}") int maxMessagingGroupMembers,
            @Value("${app.messaging-groups.max-title-chars}") int maxGroupTitleChars,
            @Value("${app.messaging-groups.max-description-chars}") int maxGroupDescriptionChars,
            @Value("${app.messages.max-chars}") int maxMessageChars
    ) {
        requirePositive("app.messaging-groups.max-members", maxMessagingGroupMembers);
        requirePositive("app.messaging-groups.max-title-chars", maxGroupTitleChars);
        requirePositive("app.messaging-groups.max-description-chars", maxGroupDescriptionChars);
        requirePositive("app.messages.max-chars", maxMessageChars);
        this.maxMessagingGroupMembers = maxMessagingGroupMembers;
        this.maxGroupTitleChars = maxGroupTitleChars;
        this.maxGroupDescriptionChars = maxGroupDescriptionChars;
        this.maxMessageChars = maxMessageChars;
    }

    public int maxMessagingGroupMembers() {
        return maxMessagingGroupMembers;
    }

    public String normalizeGroupTitle(String value) {
        return normalizeNullableText(value, maxGroupTitleChars, "Group title too long");
    }

    public String normalizeGroupDescription(String value) {
        return normalizeNullableText(value, maxGroupDescriptionChars, "Group description too long");
    }

    public String requireValidMessage(String value) {
        String normalized = value == null ? "" : value;
        if (normalized.length() > maxMessageChars) {
            throw ServiceException.invalid("Message too long");
        }
        return normalized;
    }

    private static String normalizeNullableText(String value, int maxChars, String error) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxChars) throw ServiceException.invalid(error);
        return normalized;
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) throw new IllegalStateException(name + " must be positive");
    }
}
