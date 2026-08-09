package com.lirouti.domain.notification.controller;

import com.lirouti.domain.notification.controller.docs.NotificationControllerDocs;
import com.lirouti.domain.notification.dto.request.NotificationReqDTO;
import com.lirouti.domain.notification.dto.response.NotificationResDTO;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.service.command.NotificationCommandService;
import com.lirouti.domain.notification.service.query.NotificationQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.apiPayload.code.GeneralSuccessCode;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Android 기기 토큰과 최근 7일 알림센터를 제공하는 인증 사용자 API다. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController implements NotificationControllerDocs {
    private final NotificationCommandService commandService;
    private final NotificationQueryService queryService;

    /** Firebase Android SDK가 발급한 현재 토큰을 회원에게 등록한다. */
    @Override
    @PostMapping("/devices")
    public ApiResponse<NotificationResDTO.DeviceRegistration> registerDevice(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody NotificationReqDTO.RegisterDevice request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                commandService.registerDevice(user.getMemberId(), request.token()));
    }

    /** 로그아웃한 Android 기기의 토큰을 비활성화한다. */
    @Override
    @DeleteMapping("/devices")
    public ApiResponse<NotificationResDTO.DeviceRegistration> unregisterDevice(
            @AuthenticationPrincipal CustomUserDetails user,
            @Valid @RequestBody NotificationReqDTO.UnregisterDevice request) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                commandService.unregisterDevice(user.getMemberId(), request.token()));
    }

    /** 전체 또는 한 분류의 최근 알림을 ID 커서로 나눠 조회한다. */
    @Override
    @GetMapping
    public ApiResponse<NotificationResDTO.Page> getNotifications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) NotificationCategory category,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                queryService.getNotifications(user.getMemberId(), category, cursor, size));
    }

    /** 현재 회원 소유 알림 한 건을 읽음 처리한다. */
    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markRead(@AuthenticationPrincipal CustomUserDetails user,
                                      @PathVariable Long notificationId) {
        commandService.markRead(user.getMemberId(), notificationId);
        return ApiResponse.onSuccess(GeneralSuccessCode.OK, null);
    }

    /** 현재 회원의 읽지 않은 알림을 모두 읽음 처리한다. */
    @Override
    @PatchMapping("/read-all")
    public ApiResponse<NotificationResDTO.ReadAll> markAllRead(
            @AuthenticationPrincipal CustomUserDetails user) {
        return ApiResponse.onSuccess(GeneralSuccessCode.OK,
                commandService.markAllRead(user.getMemberId()));
    }
}
