package com.serdar.personal.controller;

import com.serdar.personal.model.dto.SearchResponseDTO;
import com.serdar.personal.model.dto.SearchResultDTO;
import com.serdar.personal.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResponseDTO search(@RequestParam String keyword) {
        return searchService.search(keyword);
    }


}
