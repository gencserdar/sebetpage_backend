package com.serdar.personal.service;

import com.serdar.personal.model.FriendRequest;
import com.serdar.personal.model.User;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class FriendWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public FriendWebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Send friend request received event to the target user
     */
    public void sendFriendRequestReceived(FriendRequest request) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "FRIEND_REQUEST_RECEIVED");
        event.put("requestId", request.getId());

        // Include request details
        Map<String, Object> requestData = new HashMap<>();
        requestData.put("id", request.getId());
        requestData.put("fromUser", createUserData(request.getFromUser()));
        requestData.put("sentAt", request.getSentAt().toString());
        requestData.put("status", request.getStatus().toString());

        event.put("request", requestData);

        try {
            messagingTemplate.convertAndSendToUser(
                    request.getToUser().getEmail(),
                    "/queue/friends",
                    event
            );

            System.out.println("✅ Friend request received event sent to: " + request.getToUser().getEmail());
        } catch (Exception e) {
            System.err.println("❌ Failed to send friend request received event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send friend request accepted event to both users
     */
    public void sendFriendRequestAccepted(FriendRequest request) {
        // Event for the person who sent the original request
        Map<String, Object> senderEvent = new HashMap<>();
        senderEvent.put("type", "FRIEND_REQUEST_ACCEPTED");
        senderEvent.put("requestId", request.getId());
        senderEvent.put("acceptedBy", createUserData(request.getToUser()));

        // Event for the person who accepted the request
        Map<String, Object> accepterEvent = new HashMap<>();
        accepterEvent.put("type", "REQUEST_ACCEPTED");
        accepterEvent.put("requestId", request.getId());
        accepterEvent.put("newFriend", createUserData(request.getFromUser()));

        try {
            // Notify the original sender
            messagingTemplate.convertAndSendToUser(
                    request.getFromUser().getEmail(),
                    "/queue/friends",
                    senderEvent
            );

            // Notify the accepter
            messagingTemplate.convertAndSendToUser(
                    request.getToUser().getEmail(),
                    "/queue/friends",
                    accepterEvent
            );

            System.out.println("✅ Friend request accepted events sent to both users");
        } catch (Exception e) {
            System.err.println("❌ Failed to send friend request accepted events: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send friend request rejected event to the sender
     */
    public void sendFriendRequestRejected(FriendRequest request) {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "FRIEND_REQUEST_REJECTED");
        event.put("requestId", request.getId());
        event.put("rejectedBy", createUserData(request.getToUser()));

        try {
            messagingTemplate.convertAndSendToUser(
                    request.getFromUser().getEmail(),
                    "/queue/friends",
                    event
            );

            System.out.println("✅ Friend request rejected event sent to: " + request.getFromUser().getEmail());
        } catch (Exception e) {
            System.err.println("❌ Failed to send friend request rejected event: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send friend added event to both users
     */
    public void sendFriendAdded(User user1, User user2) {
        Map<String, Object> eventForUser1 = new HashMap<>();
        eventForUser1.put("type", "FRIEND_ADDED");
        eventForUser1.put("newFriend", createUserData(user2));

        Map<String, Object> eventForUser2 = new HashMap<>();
        eventForUser2.put("type", "FRIEND_ADDED");
        eventForUser2.put("newFriend", createUserData(user1));

        try {
            messagingTemplate.convertAndSendToUser(
                    user1.getEmail(),
                    "/queue/friends",
                    eventForUser1
            );

            messagingTemplate.convertAndSendToUser(
                    user2.getEmail(),
                    "/queue/friends",
                    eventForUser2
            );

            System.out.println("✅ Friend added events sent to both users");
        } catch (Exception e) {
            System.err.println("❌ Failed to send friend added events: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Send friend removed event to both users
     */
    public void sendFriendRemoved(User user1, User user2) {
        Map<String, Object> eventForUser1 = new HashMap<>();
        eventForUser1.put("type", "FRIEND_REMOVED");
        eventForUser1.put("removedFriend", createUserData(user2));

        Map<String, Object> eventForUser2 = new HashMap<>();
        eventForUser2.put("type", "FRIEND_REMOVED");
        eventForUser2.put("removedFriend", createUserData(user1));

        try {
            messagingTemplate.convertAndSendToUser(
                    user1.getEmail(),
                    "/queue/friends",
                    eventForUser1
            );

            messagingTemplate.convertAndSendToUser(
                    user2.getEmail(),
                    "/queue/friends",
                    eventForUser2
            );

            System.out.println("✅ Friend removed events sent to both users");
        } catch (Exception e) {
            System.err.println("❌ Failed to send friend removed events: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Create user data for events
     */
    private Map<String, Object> createUserData(User user) {
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("email", user.getEmail());
        userData.put("nickname", user.getNickname());
        userData.put("profileImageUrl", user.getProfileImageUrl());
        return userData;
    }
}