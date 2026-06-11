package com.serdar.auth.grpc;

import com.serdar.auth.entity.Credential;
import com.serdar.auth.entity.Role;
import com.serdar.auth.service.AuthDomainService;
import com.serdar.common.GrpcErrors;
import com.serdar.common.ServiceException;
import com.serdar.proto.auth.*;
import com.serdar.proto.common.BoolResponse;
import com.serdar.proto.common.Empty;
import com.serdar.proto.common.IdRequest;
import com.serdar.proto.common.StringRequest;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * gRPC adapter — translates proto messages to/from domain calls. Every handler
 * wraps the call in try/catch so domain {@link ServiceException}s become proper
 * gRPC status codes instead of bubbling as UNKNOWN.
 */
@GrpcService
@RequiredArgsConstructor
public class AuthGrpcService extends AuthServiceGrpc.AuthServiceImplBase {

    private final AuthDomainService svc;

    @Override
    public void register(RegisterRequest req, StreamObserver<RegisterResponse> out) {
        guard(out, () -> {
            AuthDomainService.Registered r = svc.register(req.getEmail(), req.getPassword(), req.getNickname());
            out.onNext(RegisterResponse.newBuilder()
                    .setUserId(r.credential().getId())
                    .setEmail(r.credential().getEmail())
                    .setNickname(r.credential().getNickname())
                    .setActivationCode(r.activationCode())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void abortRegistration(IdRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.abortRegistration(req.getId());
            out.onNext(Empty.getDefaultInstance());
            out.onCompleted();
        });
    }

    @Override
    public void login(LoginRequest req, StreamObserver<AuthResponse> out) {
        guard(out, () -> {
            AuthDomainService.LoginResult r = svc.login(req.getEmail(), req.getPassword(), req.getRememberMe());
            out.onNext(AuthResponse.newBuilder()
                    .setAccessToken(r.accessToken())
                    .setRefreshToken(r.refreshToken())
                    .setUserId(r.credential().getId())
                    .setEmail(r.credential().getEmail())
                    .setNickname(r.credential().getNickname())
                    .setRole(toProto(r.credential().getRole()))
                    .setRefreshCookieAgeSeconds(r.cookieAge())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void refresh(RefreshRequest req, StreamObserver<AuthResponse> out) {
        guard(out, () -> {
            AuthDomainService.LoginResult r = svc.refresh(req.getRefreshToken());
            out.onNext(AuthResponse.newBuilder()
                    .setAccessToken(r.accessToken())
                    .setRefreshToken(r.refreshToken())
                    .setUserId(r.credential().getId())
                    .setEmail(r.credential().getEmail())
                    .setNickname(r.credential().getNickname())
                    .setRole(toProto(r.credential().getRole()))
                    .setRefreshCookieAgeSeconds(r.cookieAge())
                    .build());
            out.onCompleted();
        });
    }

    @Override
    public void logout(LogoutRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.logout(req.getSessionId(), req.getRefreshToken());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void logoutAll(LogoutAllRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.logoutAll(req.getUserId());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void activate(ActivateRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            svc.activate(req.getActivationCode());
            out.onNext(BoolResponse.newBuilder().setValue(true).build());
            out.onCompleted();
        });
    }

    @Override
    public void resendActivation(ForgotPasswordRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.resendActivation(req.getEmail());
            out.onNext(Empty.getDefaultInstance());
            out.onCompleted();
        });
    }

    @Override
    public void forgotPassword(ForgotPasswordRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.forgotPassword(req.getEmail());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void resetPassword(ResetPasswordRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            boolean ok = svc.resetPassword(req.getResetCode(), req.getNewPassword());
            out.onNext(BoolResponse.newBuilder().setValue(ok).build()); out.onCompleted();
        });
    }

    @Override
    public void changePassword(ChangePasswordRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            boolean ok = svc.changePassword(req.getUserId(), req.getCurrentPassword(), req.getNewPassword());
            out.onNext(BoolResponse.newBuilder().setValue(ok).build()); out.onCompleted();
        });
    }

    @Override
    public void validateToken(ValidateTokenRequest req, StreamObserver<ValidateTokenResponse> out) {
        AuthDomainService.ValidationResult v = svc.validate(req.getAccessToken());
        ValidateTokenResponse.Builder b = ValidateTokenResponse.newBuilder().setValid(v.valid());
        if (v.valid()) {
            b.setUserId(v.userId())
                    .setEmail(v.emailAddr())
                    .setNickname(v.nickname())
                    .setRole(toProto(v.role()))
                    .setSessionId(v.sessionId());
        }
        out.onNext(b.build()); out.onCompleted();
    }

    @Override
    public void getCredentialsById(IdRequest req, StreamObserver<Credentials> out) {
        guard(out, () -> {
            out.onNext(toProto(svc.byId(req.getId()))); out.onCompleted();
        });
    }

    @Override
    public void getCredentialsByEmail(StringRequest req, StreamObserver<Credentials> out) {
        guard(out, () -> { out.onNext(toProto(svc.byEmail(req.getValue()))); out.onCompleted(); });
    }

    @Override
    public void getCredentialsByNickname(StringRequest req, StreamObserver<Credentials> out) {
        guard(out, () -> { out.onNext(toProto(svc.byNickname(req.getValue()))); out.onCompleted(); });
    }

    @Override
    public void isEmailTaken(StringRequest req, StreamObserver<BoolResponse> out) {
        out.onNext(BoolResponse.newBuilder().setValue(svc.emailTaken(req.getValue())).build());
        out.onCompleted();
    }

    @Override
    public void isNicknameTaken(StringRequest req, StreamObserver<BoolResponse> out) {
        out.onNext(BoolResponse.newBuilder().setValue(svc.nicknameTaken(req.getValue())).build());
        out.onCompleted();
    }

    @Override
    public void updateEmail(UpdateEmailRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            svc.updateEmail(req.getUserId(), req.getNewEmail());
            out.onNext(BoolResponse.newBuilder().setValue(true).build()); out.onCompleted();
        });
    }

    @Override
    public void updateNickname(UpdateNicknameRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            svc.updateNickname(req.getUserId(), req.getNewNickname());
            out.onNext(BoolResponse.newBuilder().setValue(true).build()); out.onCompleted();
        });
    }

    @Override
    public void requestEmailChange(RequestEmailChangeRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.requestEmailChange(req.getUserId(), req.getNewEmail());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void confirmEmailChange(ConfirmCodeRequest req, StreamObserver<ConfirmEmailChangeResponse> out) {
        guard(out, () -> {
            String newEmail = svc.confirmEmailChange(req.getUserId(), req.getCode());
            out.onNext(ConfirmEmailChangeResponse.newBuilder()
                    .setOk(true).setNewEmail(newEmail == null ? "" : newEmail).build());
            out.onCompleted();
        });
    }

    @Override
    public void requestPasswordChange(RequestPasswordChangeRequest req, StreamObserver<Empty> out) {
        guard(out, () -> {
            svc.requestPasswordChange(req.getUserId(), req.getCurrentPassword(), req.getNewPassword());
            out.onNext(Empty.getDefaultInstance()); out.onCompleted();
        });
    }

    @Override
    public void confirmPasswordChange(ConfirmCodeRequest req, StreamObserver<BoolResponse> out) {
        guard(out, () -> {
            boolean ok = svc.confirmPasswordChange(req.getUserId(), req.getCode());
            out.onNext(BoolResponse.newBuilder().setValue(ok).build()); out.onCompleted();
        });
    }

    // --- helpers ------------------------------------------------------------

    private static Credentials toProto(Credential c) {
        return Credentials.newBuilder()
                .setId(c.getId())
                .setEmail(c.getEmail())
                .setNickname(c.getNickname())
                .setRole(toProto(c.getRole()))
                .setActivated(Boolean.TRUE.equals(c.getActivated()))
                .build();
    }

    private static com.serdar.proto.auth.Role toProto(Role r) {
        return switch (r) {
            case USER  -> com.serdar.proto.auth.Role.USER;
            case ADMIN -> com.serdar.proto.auth.Role.ADMIN;
        };
    }

    private static void guard(StreamObserver<?> out, Runnable r) {
        try { r.run(); }
        catch (ServiceException e) { out.onError(GrpcErrors.toGrpc(e)); }
        catch (Exception e) { out.onError(GrpcErrors.toGrpc(new ServiceException(ServiceException.Code.INTERNAL, e.getMessage()))); }
    }
}
