package com.serdar.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSchemaRepair {

    private final JdbcTemplate jdbc;

    @EventListener(ApplicationReadyEvent.class)
    public void repairConversationTypeColumn() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'conversations'
                  AND COLUMN_NAME = 'type'
                """);

        if (rows.isEmpty()) return;

        Map<String, Object> row = rows.get(0);
        String dataType = String.valueOf(row.get("DATA_TYPE"));
        Object lengthValue = row.get("CHARACTER_MAXIMUM_LENGTH");
        long length = lengthValue instanceof Number n ? n.longValue() : 0;

        if (!"varchar".equalsIgnoreCase(dataType) || length < 32) {
            jdbc.execute("ALTER TABLE conversations MODIFY COLUMN `type` VARCHAR(32) NOT NULL");
            log.info("Repaired conversations.type column to VARCHAR(32)");
        }
    }
}
