package com.serdar.user.service;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.serdar.common.ServiceException;

import java.util.HashSet;
import java.util.Map;

final class ProfileCardValidator {

    static final int GRID_COLS = 4;
    static final int GRID_ROWS = 6;

    private record WidgetLimits(int minW, int minH, int maxW, int maxH) {}

    private static final Map<String, WidgetLimits> LIMITS = Map.of(
            "bio", new WidgetLimits(2, 1, 4, 3),
            "links", new WidgetLimits(1, 1, 4, 3),
            "status", new WidgetLimits(1, 1, 4, 2),
            "gallery", new WidgetLimits(2, 2, 4, 4),
            "quote", new WidgetLimits(1, 1, 4, 3)
    );

    private ProfileCardValidator() {}

    static void validateWidget(ObjectNode widget) {
        String type = requireText(widget, "type");
        if (!LIMITS.containsKey(type)) {
            throw invalid("Unsupported widget type: " + type);
        }

        requireText(widget, "id");
        int x = requireInt(widget, "x");
        int y = requireInt(widget, "y");
        int w = requireInt(widget, "w");
        int h = requireInt(widget, "h");

        if (x < 0 || y < 0) {
            throw invalid("Widget position must be non-negative.");
        }
        if (w < 1 || h < 1) {
            throw invalid("Widget size must be at least 1x1.");
        }
        if (x + w > GRID_COLS || y + h > GRID_ROWS) {
            throw invalid("Widget must fit inside the profile card grid.");
        }

        WidgetLimits limits = LIMITS.get(type);
        if (w < limits.minW() || h < limits.minH() || w > limits.maxW() || h > limits.maxH()) {
            throw invalid("Widget size is out of allowed bounds for " + type + ".");
        }
    }

    static void validateNoOverlap(ArrayNode widgets) {
        boolean[][] occupied = new boolean[GRID_ROWS][GRID_COLS];
        for (int i = 0; i < widgets.size(); i++) {
            ObjectNode widget = (ObjectNode) widgets.get(i);
            int x = widget.get("x").asInt();
            int y = widget.get("y").asInt();
            int w = widget.get("w").asInt();
            int h = widget.get("h").asInt();
            for (int dy = 0; dy < h; dy++) {
                for (int dx = 0; dx < w; dx++) {
                    int cx = x + dx;
                    int cy = y + dy;
                    if (occupied[cy][cx]) {
                        throw invalid("Profile card widgets overlap.");
                    }
                    occupied[cy][cx] = true;
                }
            }
        }
    }

    private static String requireText(ObjectNode node, String field) {
        if (!node.hasNonNull(field) || !node.get(field).isTextual()) {
            throw invalid("Widget field '" + field + "' must be a string.");
        }
        return node.get(field).asText();
    }

    private static int requireInt(ObjectNode node, String field) {
        if (!node.has(field) || !node.get(field).isInt()) {
            throw invalid("Widget field '" + field + "' must be an integer.");
        }
        return node.get(field).asInt();
    }

    private static ServiceException invalid(String message) {
        return ServiceException.invalid(message);
    }
}
