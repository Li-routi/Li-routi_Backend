package com.lirouti.domain.verification.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Builder;

public class VerificationResDTO {

    @Schema(description = "그룹 루틴 인증 결과")
    public record GroupRoutine(
            Long verificationId,
            Long assignmentId,
            @Schema(description = "인증 사진 key. 조회 URL은 목록 API가 서명해서 내려준다")
            String imageKey,
            String content,
            LocalDateTime verifiedAt
    ) {
    }

    @Schema(description = "개인 루틴 인증 결과")
    public record MemberRoutine(
            Long verificationId,
            Long routineId,
            @Schema(description = "인증 사진 key. 조회 URL은 목록 API가 서명해서 내려준다")
            String imageKey,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt
    ) {
    }

    /**
     * 인증 목록 래퍼. 챌린지 피드와 같은 커서 방식이다 — 첫 요청은 cursor 없이 보내고,
     * 응답의 nextCursor를 다음 요청에 넘긴다. hasNext가 false면 더 요청하지 않는다.
     */
    @Builder
    @Schema(description = "개인 루틴 인증 목록")
    public record MemberRoutineFeed(
            List<MemberRoutineItem> verifications,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 개인 루틴 인증 한 건.
     *
     * <p>{@code imageUrl}은 <b>서명된 한시적 주소</b>다. 비공개 prefix라 서명 없이는 열리지 않고,
     * 유효 시간이 지나면 403이 된다(기본 15분, {@code aws.s3.view-url-expiration}).
     * <b>클라이언트가 이 값을 저장해 두고 재사용하면 안 된다</b> — 필요할 때 목록을 다시 부른다.
     */
    @Builder
    @Schema(description = "개인 루틴 인증 한 건")
    public record MemberRoutineItem(
            Long verificationId,
            @Schema(description = "서명된 사진 주소. 유효 시간이 지나면 만료된다")
            String imageUrl,
            String content,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt
    ) {
    }

    @Builder
    @Schema(description = "그룹 루틴 인증 목록")
    public record GroupRoutineFeed(
            List<GroupRoutineItem> verifications,
            Long nextCursor,
            boolean hasNext
    ) {
    }

    /**
     * 그룹 루틴 인증 한 건. 방 멤버 전원이 보는 피드라 <b>작성자를 함께 싣는다.</b>
     *
     * <p>{@code imageUrl}의 성격은 개인 쪽과 같다 — 서명된 한시적 주소다.
     */
    @Builder
    @Schema(description = "그룹 루틴 인증 한 건")
    public record GroupRoutineItem(
            Long verificationId,
            Long assignmentId,
            Long memberId,
            String nickname,
            @Schema(description = "서명된 사진 주소. 유효 시간이 지나면 만료된다")
            String imageUrl,
            String content,
            LocalDateTime verifiedAt
    ) {
    }
}
