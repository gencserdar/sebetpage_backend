package com.serdar.gateway.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.serdar.gateway.dto.Dtos;
import com.serdar.proto.user.ProfileSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ProfileSettingsMapper {

    private static final TypeReference<List<Dtos.SocialLinkDTO>> SOCIAL_LINKS_TYPE =
            new TypeReference<>() {};
    private static final TypeReference<Dtos.ProfileCardDTO> PROFILE_CARD_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public Dtos.ProfileSettingsDTO fromProto(ProfileSettings settings) {
        return Dtos.ProfileSettingsDTO.builder()
                .userId(settings.getUserId())
                .bio(settings.getBio())
                .socialLinks(parseSocialLinks(settings.getSocialLinksJson()))
                .profileCard(parseProfileCard(settings.getProfileCardJson()))
                .build();
    }

    public String toSocialLinksJson(List<Dtos.SocialLinkDTO> links) throws JsonProcessingException {
        return objectMapper.writeValueAsString(links == null ? Collections.emptyList() : links);
    }

    public String toProfileCardJson(Dtos.ProfileCardDTO card) throws JsonProcessingException {
        if (card == null || card.getWidgets() == null) {
            return "{\"widgets\":[]}";
        }
        return objectMapper.writeValueAsString(card);
    }

    private List<Dtos.SocialLinkDTO> parseSocialLinks(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, SOCIAL_LINKS_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private Dtos.ProfileCardDTO parseProfileCard(String json) {
        if (json == null || json.isBlank()) {
            return new Dtos.ProfileCardDTO(Collections.emptyList());
        }
        try {
            Dtos.ProfileCardDTO card = objectMapper.readValue(json, PROFILE_CARD_TYPE);
            if (card.getWidgets() == null) {
                card.setWidgets(Collections.emptyList());
            }
            return card;
        } catch (JsonProcessingException e) {
            return new Dtos.ProfileCardDTO(Collections.emptyList());
        }
    }
}
