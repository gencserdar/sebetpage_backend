package com.serdar.personal.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponseDTO {
    private List<SearchResultDTO> users;
    private List<SearchResultDTO> groups;
}
