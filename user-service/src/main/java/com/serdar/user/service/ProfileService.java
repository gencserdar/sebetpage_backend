package com.serdar.user.service;

import com.serdar.common.ServiceException;
import com.serdar.user.client.AuthClient;
import com.serdar.user.search.UserSearchIndexService;
import com.serdar.user.entity.UserProfile;
import com.serdar.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final String DEFAULT_AVATAR = "";

    private final UserProfileRepository repo;
    private final AuthClient authClient;
    private final LocalImageStorageService imageStorage;
    private final UserSearchIndexService searchIndex;

    @Transactional
    public UserProfile createProfile(long userId, String email, String nickname, String name, String surname) {
        // Defensive: if someone hits CreateProfile twice (e.g. a retried registration
        // call) we return the existing profile instead of blowing up on PK collision.
        return repo.findById(userId).orElseGet(() -> {
                UserProfile saved = repo.save(UserProfile.builder()
                        .id(userId)
                        .email(email)
                        .nickname(nickname)
                        .name(name)
                        .surname(surname)
                        .profileImageUrl(DEFAULT_AVATAR)
                        .build());
                searchIndex.index(saved);
                return saved;
        });
    }

    public UserProfile getById(long id) {
        return repo.findById(id).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    public UserProfile getByEmail(String email) {
        return repo.findByEmail(email).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    public UserProfile getByNickname(String nickname) {
        return repo.findByNickname(nickname).orElseThrow(() -> ServiceException.notFound("User not found"));
    }

    @Transactional
    public UserProfile updatePhoto(long userId, byte[] bytes, String contentType, String filename) {
        // Don't trust the client's contentType / filename — validate the
        // bytes against known image magic numbers, derive a fresh
        // server-controlled filename. Stops two attack classes:
        //   - mislabeled files (script/exe with image/png Content-Type)
        //   - path-traversal / unicode-in-filename ending up in storage path
        ImageValidator.Validated v = ImageValidator.validate(bytes, userId);
        UserProfile p = getById(userId);
        String url = imageStorage.upload(v.bytes(), v.canonicalContentType(), v.safeFilename());
        p.setProfileImageUrl(url);
        return repo.save(p);
    }

    public String uploadImage(long uploaderId, byte[] bytes, String contentType, String filename) {
        ImageValidator.Validated v = ImageValidator.validate(bytes, uploaderId);
        return imageStorage.upload(v.bytes(), v.canonicalContentType(), v.safeFilename());
    }

    @Transactional
    public UserProfile updateNameSurname(long userId, String name, String surname) {
        UserProfile p = getById(userId);
        if (name != null)    p.setName(name);
        if (surname != null) p.setSurname(surname);
        UserProfile saved = repo.save(p);
        searchIndex.index(saved);
        return saved;
    }

    @Transactional
    public UserProfile changeNickname(long userId, String newNickname) {
        if (newNickname == null || newNickname.isBlank())
            throw ServiceException.invalid("Nickname cannot be blank");
        // Auth-service is the source of truth for uniqueness; it will throw CONFLICT if taken.
        if (authClient.isNicknameTaken(newNickname))
            throw ServiceException.conflict("Nickname already used");
        authClient.updateNickname(userId, newNickname);
        UserProfile p = getById(userId);
        p.setNickname(newNickname);
        UserProfile saved = repo.save(p);
        searchIndex.index(saved);
        return saved;
    }

    @Transactional
    public UserProfile changeEmail(long userId, String newEmail) {
        if (newEmail == null || newEmail.isBlank())
            throw ServiceException.invalid("Email cannot be blank");
        if (authClient.isEmailTaken(newEmail))
            throw ServiceException.conflict("Email already used");
        authClient.updateEmail(userId, newEmail);
        UserProfile p = getById(userId);
        p.setEmail(newEmail);
        return repo.save(p);
    }

    /**
     * Internal — invoked by the gateway after auth-service has verified and
     * applied a code-confirmed email change. Skips the uniqueness check
     * (auth already enforced it) and just mirrors the new value into the
     * denormalized profile row.
     */
    @Transactional
    public UserProfile syncProfileEmail(long userId, String newEmail) {
        UserProfile p = getById(userId);
        p.setEmail(newEmail);
        return repo.save(p);
    }

    public UserProfile getProfileSettings(long userId) {
        return getById(userId);
    }

    @Transactional
    public UserProfile updateProfileSettings(
            long userId,
            String bio,
            String socialLinksJson,
            String profileCardJson) {
        UserProfile p = getById(userId);
        p.setBio(ProfileSettingsJson.sanitizeBio(bio));
        p.setSocialLinksJson(ProfileSettingsJson.normalizeSocialLinksJson(socialLinksJson));
        p.setProfileCardJson(ProfileSettingsJson.normalizeProfileCardJson(profileCardJson));
        return repo.save(p);
    }
}
