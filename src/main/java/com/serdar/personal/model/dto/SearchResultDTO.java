package com.serdar.personal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultDTO {
    private Long id;
    private String name;
    private String surname;
    private String nickname;
    private String type;
    private int mutualFriendCount;
    private String profileImageUrl;
}
