// src/main/java/com/serdar/personal/model/dto/FriendStatusResponse.java
package com.serdar.personal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendStatusResponse {
    private String status; // friends, sent, received, etc.
    private Long requestId; // sadece 'received' ise set edilir
}

