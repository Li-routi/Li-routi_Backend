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

    /** 생략하지 않은 항목만 변경하는 사용자 알림 설정 요청이다. */
    @Schema(name = "UpdateNotificationSettingsRequest", description = "사용자 알림 설정 부분 변경 요청")
    public record UpdateSettings(
            @Schema(description = "루틴 마감·리마인드 알림 수신 여부", example = "true")
            Boolean routineDeadlineEnabled,
            @Schema(description = "그룹원의 새 인증 알림 수신 여부", example = "true")
            Boolean newVerificationEnabled,
            @Schema(description = "내 그룹 인증의 좋아요·아쉬워요 알림 수신 여부", example = "true")
            Boolean verificationReactionEnabled,
            @Schema(description = "콕콕 알림 수신 여부", example = "true")
            Boolean pokeEnabled,
            @Schema(description = "새 그룹 채팅 알림 수신 여부", example = "true")
            Boolean newChatEnabled,
            @Schema(description = "챌린지 인증 좋아요 알림 수신 여부", example = "true")
            Boolean likeEnabled
    ) {}
}
