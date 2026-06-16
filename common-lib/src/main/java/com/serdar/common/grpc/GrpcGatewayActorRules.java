package com.serdar.common.grpc;

import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;

import java.util.Map;
import java.util.Optional;

/**
 * Maps gRPC full method names to the protobuf field that carries the acting user.
 * When a method is listed here, calls must include {@link InternalGrpcAuth#GATEWAY_USER_ID_HEADER}
 * and the field value must match — blocking token-only impersonation from inside the network.
 */
public final class GrpcGatewayActorRules {

    public record ActorRule(String fieldName, boolean anyOfPair) {}

    private static final Map<String, ActorRule> RULES = Map.ofEntries(
            // chat-service
            Map.entry("com.serdar.proto.chat.ChatService/SendMessage", new ActorRule("sender_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetPage", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetLatest", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/MarkRead", new ActorRule("reader_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetReadState", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetUnreadCounts", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/MyConversations", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetOrCreateDirect", new ActorRule("user_a_id", true)),
            Map.entry("com.serdar.proto.chat.ChatService/CreateMessagingGroup", new ActorRule("creator_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/AddMessagingGroupMember", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/RemoveMessagingGroupMember", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetMessagingGroupDetail", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/UpdateMessagingGroup", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/ExitMessagingGroup", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/DeleteMessagingGroup", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/UpdateMessagingGroupParticipant", new ActorRule("requester_id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/GetPresenceSnapshot", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.chat.ChatService/SubscribeEvents", new ActorRule("id", false)),

            // user-service
            Map.entry("com.serdar.proto.user.UserService/UpdateProfilePhoto", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/UploadImage", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/UpdateNameSurname", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/ChangeNickname", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/ChangeEmail", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/GetFriendStatus", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.user.UserService/ListFriends", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.user.UserService/ListFriendIds", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.user.UserService/RemoveFriend", new ActorRule("user_a", true)),
            Map.entry("com.serdar.proto.user.UserService/SendFriendRequest", new ActorRule("from_user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/ListIncoming", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.user.UserService/ListOutgoing", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.user.UserService/RespondToRequest", new ActorRule("responder_id", false)),
            Map.entry("com.serdar.proto.user.UserService/CancelRequest", new ActorRule("canceller_id", false)),
            Map.entry("com.serdar.proto.user.UserService/Block", new ActorRule("blocker_id", false)),
            Map.entry("com.serdar.proto.user.UserService/Unblock", new ActorRule("blocker_id", false)),
            Map.entry("com.serdar.proto.user.UserService/SyncProfileEmail", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/UpdateProfileSettings", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.user.UserService/BlockStatus", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.user.UserService/IsBlockedEitherWay", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.user.UserService/MyBlocks", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.user.UserService/SearchUsers", new ActorRule("caller_id", false)),
            Map.entry("com.serdar.proto.user.UserService/GetProfileSettings", new ActorRule("id", false)),

            // auth-service
            Map.entry("com.serdar.proto.auth.AuthService/LogoutAll", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/ChangePassword", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/RequestEmailChange", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/ConfirmEmailChange", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/RequestPasswordChange", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/ConfirmPasswordChange", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/UpdateEmail", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/UpdateNickname", new ActorRule("user_id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/FreezeAccount", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/UnfreezeAccount", new ActorRule("id", false)),
            Map.entry("com.serdar.proto.auth.AuthService/AbortRegistration", new ActorRule("id", false)),

            // community-service
            Map.entry("com.serdar.proto.community.CommunityService/CreateCommunity", new ActorRule("creator_id", false)),
            Map.entry("com.serdar.proto.community.CommunityService/Invite", new ActorRule("inviter_id", false)),
            Map.entry("com.serdar.proto.community.CommunityService/RespondToInvite", new ActorRule("responder_id", false)),
            Map.entry("com.serdar.proto.community.CommunityService/MyCommunities", new ActorRule("id", false))
    );

    private GrpcGatewayActorRules() {}

    public static Optional<ActorRule> ruleFor(String fullMethodName) {
        return Optional.ofNullable(RULES.get(fullMethodName));
    }

    public static boolean actorMatches(Message request, ActorRule rule, long gatewayUserId) {
        Descriptors.Descriptor descriptor = request.getDescriptorForType();
        if (rule.anyOfPair()) {
            long a = longField(request, descriptor, rule.fieldName());
            long b = longField(request, descriptor, "user_b");
            return gatewayUserId == a || gatewayUserId == b;
        }
        return gatewayUserId == longField(request, descriptor, rule.fieldName());
    }

    private static long longField(Message request, Descriptors.Descriptor descriptor, String fieldName) {
        Descriptors.FieldDescriptor field = descriptor.findFieldByName(fieldName);
        if (field == null || !request.hasField(field)) return Long.MIN_VALUE;
        Object value = request.getField(field);
        if (value instanceof Number number) return number.longValue();
        return Long.MIN_VALUE;
    }
}
