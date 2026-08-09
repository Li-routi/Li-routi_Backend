package com.lirouti.domain.notification.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Android 앱이 백엔드 알림 API에 전달하는 요청 DTO 모음이다. */
public final class NotificationReqDTO {
    private NotificationReqDTO() {}

    /** Firebase Android SDK가 발급·갱신한 등록 토큰이다. */
    @Schema(name = "RegisterDeviceRequest", description = "FCM 기기 토큰 등록 요청")
    public record RegisterDevice(
            @Schema(description = "Firebase Android SDK가 발급·갱신한 FCM 등록 토큰. 최대 512자",
                    example = "dQw4w9WgXcQ:APA91bF...")
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            @Size(max = 512, message = "FCM 토큰은 512자를 넘을 수 없습니다.")
            String token
    ) {}

    /** 로그아웃 시 현재 Android 토큰을 비활성화하는 요청이다. */
    @Schema(name = "UnregisterDeviceRequest", description = "FCM 기기 토큰 해제 요청")
    public record UnregisterDevice(
            @Schema(description = "비활성화할 현재 기기의 FCM 등록 토큰. 최대 512자",
                    example = "dQw4w9WgXcQ:APA91bF...")
            @NotBlank(message = "FCM 토큰은 필수입니다.")
            @Size(max = 512, message = "FCM 토큰은 512자를 넘을 수 없습니다.")
            String token
    ) {}
}
