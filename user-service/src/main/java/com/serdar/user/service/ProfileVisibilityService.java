package com.serdar.user.service;

import com.serdar.common.ServiceException;
import com.serdar.user.client.AuthClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Enforces who may see full profile data. Used at the user-service boundary so
 * frozen/blocked users cannot be read by bypassing api-gateway redaction.
 */
@Service
@RequiredArgsConstructor
public class ProfileVisibilityService {

    private final AuthClient authClient;
    private final BlockService blockService;

    /** Throws 404 when the viewer must not know the target exists (block). */
    public void assertProfileAccessible(long targetUserId, Long viewerUserId) {
        if (viewerUserId == null || viewerUserId == targetUserId) {
            return;
        }
        if (blockService.eitherWay(viewerUserId, targetUserId)) {
            throw ServiceException.notFound("User not found");
        }
    }

    /** Full profile fields (name, photo, settings) — false for frozen accounts seen by others. */
    public boolean showFullProfile(long targetUserId, Long viewerUserId) {
        if (viewerUserId == null || viewerUserId == targetUserId) {
            return true;
        }
        return !authClient.isFrozen(targetUserId);
    }
}
