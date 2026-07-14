package com.lirouti.domain.auth.controller;

import com.lirouti.domain.auth.controller.docs.AuthControllerDocs;
import com.lirouti.domain.auth.dto.request.AuthReqDTO;
import com.lirouti.domain.auth.dto.response.AuthResDTO;
import com.lirouti.domain.auth.exception.code.success.AuthSuccessCode;
import com.lirouti.domain.auth.service.AuthService;
import com.lirouti.global.apiPayload.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AuthController implements AuthControllerDocs {
    private final AuthService authService;

    @Override
    @PostMapping("/google/nonce")
    public ApiResponse<AuthResDTO.GoogleNonce> issueGoogleNonce() {
        AuthResDTO.GoogleNonce response = authService.issueGoogleNonce();
        return ApiResponse.onSuccess(AuthSuccessCode.GOOGLE_NONCE_ISSUE_SUCCESS, response);
    }

    @Override
    @PostMapping("/social-login")
    public ApiResponse<AuthResDTO.Token> socialLogin(
            @Valid @RequestBody AuthReqDTO.SocialLogin request
    ) {
        AuthResDTO.Token response = authService.socialLogin(request);
        return ApiResponse.onSuccess(AuthSuccessCode.LOGIN_SUCCESS, response);
    }

    @Override
    @PostMapping("/reissue")
    public ApiResponse<AuthResDTO.Token> reissue(
            @Valid @RequestBody AuthReqDTO.Reissue request
    ) {
        AuthResDTO.Token response = authService.reissue(request);
        return ApiResponse.onSuccess(AuthSuccessCode.TOKEN_REFRESH_SUCCESS, response);
    }
}
