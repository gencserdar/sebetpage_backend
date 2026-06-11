package com.serdar.gateway.client;

import com.serdar.proto.auth.*;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.common.StringRequest;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

@Component
public class AuthClient {

    @GrpcClient("auth-service")
    private AuthServiceGrpc.AuthServiceBlockingStub stub;

    public RegisterResponse register(String email, String password, String nickname, String name, String surname) {
        return stub.register(RegisterRequest.newBuilder()
                .setEmail(email).setPassword(password).setNickname(nickname)
                .setName(name).setSurname(surname).build());
    }

    public void abortRegistration(long userId) {
        stub.abortRegistration(IdRequest.newBuilder().setId(userId).build());
    }

    public AuthResponse login(String email, String password, boolean rememberMe) {
        return stub.login(LoginRequest.newBuilder()
                .setEmail(email).setPassword(password).setRememberMe(rememberMe).build());
    }

    public AuthResponse refresh(String refreshToken) {
        return stub.refresh(RefreshRequest.newBuilder().setRefreshToken(refreshToken).build());
    }

    public void logout(long sessionId, String refreshToken) {
        stub.logout(LogoutRequest.newBuilder()
                .setSessionId(sessionId)
                .setRefreshToken(refreshToken != null ? refreshToken : "")
                .build());
    }

    public void logoutAll(long userId) {
        stub.logoutAll(LogoutAllRequest.newBuilder().setUserId(userId).build());
    }

    public boolean activate(String code) {
        return stub.activate(ActivateRequest.newBuilder().setActivationCode(code).build()).getValue();
    }

    public void forgotPassword(String email) {
        stub.forgotPassword(ForgotPasswordRequest.newBuilder().setEmail(email).build());
    }

    public boolean resetPassword(String code, String newPassword) {
        return stub.resetPassword(ResetPasswordRequest.newBuilder()
                .setResetCode(code).setNewPassword(newPassword).build()).getValue();
    }

    public boolean changePassword(long userId, String current, String next) {
        return stub.changePassword(ChangePasswordRequest.newBuilder()
                .setUserId(userId).setCurrentPassword(current).setNewPassword(next).build()).getValue();
    }

    public ValidateTokenResponse validate(String accessToken) {
        return stub.validateToken(ValidateTokenRequest.newBuilder().setAccessToken(accessToken).build());
    }

    public Credentials byId(long id) {
        return stub.getCredentialsById(IdRequest.newBuilder().setId(id).build());
    }

    public Credentials byEmail(String email) {
        return stub.getCredentialsByEmail(StringRequest.newBuilder().setValue(email).build());
    }

    // --- two-step email / password change ----------------------------------

    public void requestEmailChange(long userId, String newEmail) {
        stub.requestEmailChange(RequestEmailChangeRequest.newBuilder()
                .setUserId(userId).setNewEmail(newEmail).build());
    }

    public String confirmEmailChange(long userId, String code) {
        ConfirmEmailChangeResponse r = stub.confirmEmailChange(ConfirmCodeRequest.newBuilder()
                .setUserId(userId).setCode(code).build());
        return r.getNewEmail();
    }

    public void requestPasswordChange(long userId, String currentPassword, String newPassword) {
        stub.requestPasswordChange(RequestPasswordChangeRequest.newBuilder()
                .setUserId(userId).setCurrentPassword(currentPassword).setNewPassword(newPassword).build());
    }

    public boolean confirmPasswordChange(long userId, String code) {
        return stub.confirmPasswordChange(ConfirmCodeRequest.newBuilder()
                .setUserId(userId).setCode(code).build()).getValue();
    }
}
