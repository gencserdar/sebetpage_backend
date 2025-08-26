package com.serdar.personal.service;

import com.serdar.personal.model.dto.SearchResponseDTO;
import com.serdar.personal.model.dto.SearchResultDTO;
import com.serdar.personal.model.User;
import com.serdar.personal.model.Group;
import com.serdar.personal.model.Friendship;
import com.serdar.personal.repository.GroupRepository;
import com.serdar.personal.repository.UserRepository;
import com.serdar.personal.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final FriendshipRepository friendshipRepository;
    private final UserContextService userContextService;

    // ✅ EKLENDİ: beni engelleyenleri bulmak için
    private final BlockService blockService;

    public SearchResponseDTO search(String keyword) {
        User currentUser = userContextService.getCurrentUser();

        // Mevcut arkadaşlarımın id seti (mutual friend sayımı için)
        Set<Long> friendIds = friendshipRepository.findByUser1OrUser2(currentUser, currentUser)
                .stream()
                .map(f -> f.getUser1().equals(currentUser) ? f.getUser2().getId() : f.getUser1().getId())
                .collect(Collectors.toSet());

        //  Beni engelleyenlerin id’leri (tek seferde çek, bellekte filtrele)
        Set<Long> whoBlockMe = new HashSet<>(blockService.whoBlocksMeIds(currentUser.getId()));

        // Kullanıcı arama
        List<User> users = userRepository
                .findByNameContainingIgnoreCaseOrSurnameContainingIgnoreCaseOrNicknameContainingIgnoreCase(
                        keyword, keyword, keyword);

        // Kullanıcı sonuçları: kendimi çıkar + beni engelleyenleri çıkar
        List<SearchResultDTO> userResults = users.stream()
                .filter(u -> !u.getId().equals(currentUser.getId()))
                .filter(u -> !whoBlockMe.contains(u.getId())) // ✅ kritik satır
                .map(u -> {
                    int mutualCount = countMutualFriends(currentUser.getId(), u.getId(), friendIds);
                    return new SearchResultDTO(
                            u.getId(),
                            u.getName(),
                            u.getSurname(),
                            u.getNickname(),
                            "USER",
                            mutualCount,
                            u.getProfileImageUrl()
                    );
                })
                .collect(Collectors.toList());

        // Grup arama (değişmedi)
        List<Group> groups = groupRepository.findByNameContainingIgnoreCase(keyword);
        List<SearchResultDTO> groupResults = groups.stream()
                .map(g -> {
                    int mutualCount = (int) g.getMembers().stream()
                            .map(m -> m.getUser().getId())
                            .filter(friendIds::contains)
                            .count();
                    return new SearchResultDTO(
                            g.getId(),
                            g.getName(),
                            null,
                            null,
                            "GROUP",
                            mutualCount,
                            null
                    );
                })
                .collect(Collectors.toList());

        return new SearchResponseDTO(userResults, groupResults);
    }

    private int countMutualFriends(Long id1, Long id2, Set<Long> currentFriends) {
        Set<Long> otherFriends = friendshipRepository.findByUser1_IdOrUser2_Id(id2, id2)
                .stream()
                .map(f -> f.getUser1().getId().equals(id2) ? f.getUser2().getId() : f.getUser1().getId())
                .collect(Collectors.toSet());

        otherFriends.retainAll(currentFriends);
        return otherFriends.size();
    }
}
