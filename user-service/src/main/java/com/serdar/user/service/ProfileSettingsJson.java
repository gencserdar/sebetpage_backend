package com.serdar.user.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.serdar.common.ServiceException;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class ProfileSettingsJson {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_SOCIAL_LINKS = 20;
    private static final int MAX_WIDGETS = 64;
    private static final String DEFAULT_LINKS = "[]";
    private static final String DEFAULT_CARD = "{\"widgets\":[]}";

    private ProfileSettingsJson() {}

    static String sanitizeBio(String bio) {
        if (bio == null) return "";
        String normalized = bio.replace("\r\n", "\n").replace('\r', '\n').trim();
        return normalized.length() <= 180 ? normalized : normalized.substring(0, 180);
    }

    static String normalizeSocialLinksJson(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_LINKS;
        try {
            JsonNode root = JSON.readTree(raw);
            if (!root.isArray()) throw invalid("socialLinks must be a JSON array.");
            if (root.size() > MAX_SOCIAL_LINKS) {
                throw invalid("Too many social links.");
            }

            Map<String, ObjectNode> byPlatform = new LinkedHashMap<>();
            for (JsonNode item : root) {
                if (!item.isObject()) throw invalid("Each social link must be an object.");
                ObjectNode link = (ObjectNode) item;
                String platform = requireLinkText(link, "platform");
                String urlRaw = requireLinkText(link, "url");
                if (!SocialUrlValidator.isSupportedPlatform(platform)) {
                    throw invalid("Unsupported social platform.");
                }
                String url = SocialUrlValidator.validateAndNormalize(platform, urlRaw);
                ObjectNode normalized = JSON.createObjectNode();
                normalized.put("platform", platform);
                normalized.put("url", url);
                if (link.hasNonNull("id") && link.get("id").isTextual()) {
                    normalized.put("id", link.get("id").asText());
                } else {
                    normalized.put("id", platform + "-" + url.hashCode());
                }
                byPlatform.put(platform, normalized);
            }

            ArrayNode out = JSON.createArrayNode();
            byPlatform.values().forEach(out::add);
            return JSON.writeValueAsString(out);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Invalid socialLinks JSON.");
        }
    }

    static String normalizeProfileCardJson(String raw) {
        if (raw == null || raw.isBlank()) return DEFAULT_CARD;
        try {
            JsonNode root = JSON.readTree(raw);
            if (!root.isObject()) throw invalid("profileCard must be a JSON object.");
            JsonNode widgetsNode = root.get("widgets");
            if (widgetsNode == null || !widgetsNode.isArray()) {
                throw invalid("profileCard.widgets must be a JSON array.");
            }
            ArrayNode widgets = (ArrayNode) widgetsNode;
            if (widgets.size() > MAX_WIDGETS) {
                throw invalid("Too many profile card widgets.");
            }

            Set<String> ids = new HashSet<>();
            for (JsonNode widget : widgets) {
                if (!widget.isObject()) throw invalid("Each widget must be an object.");
                ObjectNode object = (ObjectNode) widget;
                ProfileCardValidator.validateWidget(object);
                String id = object.get("id").asText();
                if (!ids.add(id)) {
                    throw invalid("Duplicate widget id.");
                }
            }
            ProfileCardValidator.validateNoOverlap(widgets);
            return JSON.writeValueAsString(root);
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            throw invalid("Invalid profileCard JSON.");
        }
    }

    private static String requireLinkText(ObjectNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()) {
            throw invalid("Each social link needs a " + field + ".");
        }
        String value = node.get(field).asText().trim();
        if (value.isEmpty()) {
            throw invalid("Each social link needs a " + field + ".");
        }
        return value;
    }

    private static ServiceException invalid(String message) {
        return ServiceException.invalid(message);
    }
}
