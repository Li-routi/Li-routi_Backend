package com.lirouti.domain.notification.dto.response;

import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

/** Android 알림센터와 토큰 등록 API 응답 DTO 모음이다. */
public final class NotificationResDTO {
    private NotificationResDTO() {}

    /** 토큰 등록 결과다. 원문 토큰은 응답에 다시 노출하지 않는다. */
    @Schema(name = "DeviceRegistrationResult", description = "FCM 기기 토큰 등록·해제 결과")
    public record DeviceRegistration(
            @Schema(description = "이 기기 토큰이 현재 활성 상태인지", example = "true")
            boolean active
    ) {}

    /** 알림센터의 한 항목이다. */
    @Schema(name = "NotificationItem", description = "알림센터 목록의 한 항목")
    public record Item(
            @Schema(description = "알림 ID. 다음 페이지 조회 시 cursor로 사용", example = "42")
            Long id,
            @Schema(description = "알림 목록 탭 분류")
            NotificationCategory category,
            @Schema(description = "알림 사건 유형. 화면 이동·아이콘 분기에 사용",
                    example = "GROUP_MEMBER_POKED")
            NotificationType type,
            @Schema(description = "알림 제목", example = "그룹원이 회원님을 찔렀어요!")
            String title,
            @Schema(description = "알림 본문", example = "더미유저1님이 루틴을 기다리고 있어요 🔥")
            String body,
            @Schema(description = "관련 그룹 ID. 그룹과 무관한 알림(개인 루틴 등)이면 null", example = "1")
            Long groupId,
            @Schema(description = "referenceType이 가리키는 대상의 ID(인증 게시물, 할당, 회원 등)",
                    example = "10")
            Long referenceId,
            @Schema(description = "referenceId가 가리키는 대상의 종류. 클라이언트가 탭 시 이동할 화면을 결정",
                    example = "GROUP_ROUTINE_VERIFICATION")
            String referenceType,
            @Schema(description = "읽음 여부", example = "false")
            boolean read,
            @Schema(description = "알림 생성 시각")
            LocalDateTime createdAt
    ) {}

    /** 무한 스크롤 한 페이지다. nextCursor가 null이면 마지막 페이지다. */
    @Schema(name = "NotificationPage", description = "알림 목록 커서 페이지")
    public record Page(
            @Schema(description = "이번 페이지 알림 목록. 최신순(id 역순)")
            List<Item> notifications,
            @Schema(description = "다음 페이지 조회에 넘길 cursor. 마지막 페이지면 null", example = "23")
            Long nextCursor,
            @Schema(description = "다음 페이지가 더 있는지", example = "true")
            boolean hasNext
    ) {}

    /** 전체 읽음 처리된 행 수다. */
    @Schema(name = "NotificationReadAllResult", description = "전체 읽음 처리 결과")
    public record ReadAll(
            @Schema(description = "이번 요청으로 실제 읽음 처리된 알림 수", example = "3")
            int updatedCount
    ) {}
}
