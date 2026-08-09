package com.lirouti.domain.group.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lirouti.domain.group.enums.GroupJoinUnavailableReason;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class GroupResDTO {
    private GroupResDTO() {
    }

    /** 그룹에서 사용할 수 있는 카테고리와 추가 가능 개수다. */
    @Builder
    @Schema(name = "GroupRoutineCategoryList", description = "그룹 루틴 카테고리 목록")
    public record CategoryList(
            List<Category> categories,
            int addableCount
    ) {
    }

    /** 기본 또는 그룹 사용자 카테고리 한 건이다. */
    @Builder
    @Schema(name = "GroupRoutineCategory", description = "그룹 루틴 카테고리")
    public record Category(
            Long categoryId,
            String name,
            RoutineCategoryColor color,
            boolean fixed
    ) {
    }

    /** 그룹방 진입 화면의 기본 정보와 ACTIVE 구성원 활동 현황이다. */
    @Builder
    @Schema(name = "GroupDetail", description = "그룹방 상세 정보")
    public record Detail(
            Long groupId,
            String groupName,
            String inviteCode,
            List<MemberActivity> members
    ) {
    }

    /** ACTIVE 그룹 구성원 한 명의 프로필·활동 현황이다. */
    @Builder
    @Schema(name = "GroupMemberActivity", description = "그룹 구성원 활동 현황")
    public record MemberActivity(
            Long memberId,
            String name,
            String profileImageKey,
            String statusMessage,
            int currentStreak,
            long totalLikeCount,
            long totalPokeCount,
            DailyProgress dailyProgress
    ) {
    }

    /** 그룹 구성원 한 명을 찌른 뒤의 누적값이다. */
    @Builder
    @Schema(name = "GroupMemberPokeResult", description = "그룹 구성원 찌르기 결과")
    public record PokeResult(
            Long memberId,
            long totalPokeCount
    ) {
    }

    /** 로그인 회원이 수정한 해당 그룹 내 상태 메시지다. */
    @Builder
    @Schema(name = "GroupMemberStatusMessageUpdate", description = "그룹별 상태 메시지 수정 결과")
    public record StatusMessageUpdate(
            Long groupId,
            String statusMessage
    ) {
    }

    /** 금일 그룹 루틴 할당의 완료 수와 전체 수다. */
    @Builder
    public record DailyProgress(
            long completedCount,
            long totalCount
    ) {
    }

    /** 초대코드를 제외한 모임방 통합 생성 결과다. */
    @Builder
    @Schema(name = "GroupCreateResult", description = "모임방과 초기 그룹 루틴 통합 생성 결과")
    public record CreateResult(
            Long groupId,
            String name,
            List<CreatedCategory> customCategories,
            List<CreatedRoutine> routines,
            int assignmentCount
    ) {
    }

    /** 요청의 clientKey와 저장된 그룹 사용자 카테고리 ID를 연결해 반환한다. */
    @Builder
    public record CreatedCategory(
            String clientKey,
            Long categoryId,
            String name,
            RoutineCategoryColor color
    ) {
    }

    /** 통합 생성으로 저장된 초기 그룹 루틴과 방장 할당 결과다. */
    @Builder
    public record CreatedRoutine(
            Long routineId,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            List<RoutineSchedule> schedules,
            int assignmentCount
    ) {
    }

    /**
     * 그룹 루틴 생성 결과와 생성 당일 할당 대상 수를 전달한다.
     *
     * @param routineId 생성된 그룹 루틴 ID
     * @param groupId 루틴이 속한 그룹 ID
     * @param categoryId 루틴 카테고리 ID
     * @param categoryName 루틴 카테고리 이름
     * @param title 루틴 제목
     * @param description 루틴 설명
     * @param schedules 요일 순서로 정렬된 반복 일정
     * @param assignmentCount 생성 당일 할당 대상 수
     */
    @Builder
    @Schema(name = "GroupRoutineCreateResult", description = "그룹 루틴 생성 결과")
    public record GroupRoutineCreateResult(
            Long routineId,
            Long groupId,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            List<RoutineSchedule> schedules,
            int assignmentCount
    ) {
    }

    /**
     * 그룹 루틴 수정 결과와 수정 후 오늘 조회 가능한 할당 수를 전달한다.
     *
     * @param routineId 수정된 그룹 루틴 ID
     * @param groupId 루틴이 속한 그룹 ID
     * @param categoryId 루틴 카테고리 ID
     * @param categoryName 루틴 카테고리 이름
     * @param title 루틴 제목
     * @param description 루틴 설명
     * @param schedules 요일 순서로 정렬된 반복 일정
     * @param assignmentCount 수정 후 ACTIVE 구성원이 오늘 조회할 수 있는 해당 루틴 할당 수
     */
    @Builder
    public record RoutineUpdateResult(
            Long routineId,
            Long groupId,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            List<RoutineSchedule> schedules,
            int assignmentCount
    ) {
    }

    /**
     * 그룹 루틴의 요일별 수행 시간 범위를 전달한다.
     *
     * @param repeatDay 반복 요일
     * @param startTime 수행 시작 시각
     * @param endTime 수행 마감 시각
     */
    @Schema(name = "GroupRoutineScheduleResult", description = "그룹 루틴 수행 일정")
    @Builder
    public record RoutineSchedule(
            DayOfWeek repeatDay,
            @JsonFormat(pattern = "HH:mm") LocalTime startTime,
            @JsonFormat(pattern = "HH:mm") LocalTime endTime
    ) {
    }

    /** 오늘 로그인 회원에게 할당된 그룹 루틴 목록을 전달한다. */
    @Builder
    public record TodayRoutineList(
            List<TodayRoutine> routines
    ) {
    }

    /**
     * 오늘의 그룹 루틴 할당과 소속 그룹 및 카테고리 정보를 전달한다.
     * 수행 시간은 할당 생성 시점에 저장한 스냅샷이다.
     */
    @Builder
    public record TodayRoutine(
            Long assignmentId,
            Long routineId,
            Long groupId,
            String groupName,
            Long categoryId,
            String categoryName,
            String title,
            String description,
            LocalDate assignedDate,
            @JsonFormat(pattern = "HH:mm") LocalTime scheduledStartTime,
            @JsonFormat(pattern = "HH:mm") LocalTime scheduledEndTime,
            GroupRoutineAssignmentStatus status
    ) {
    }

    // 그룹 설정에서 확인할 영구 초대코드
    @Builder
    public record InviteCode(
            String inviteCode
    ) {
    }

    /** 초대코드 입력 후 참여 팝업에 표시할 그룹 정보와 현재 회원의 참여 가능 상태다. */
    @Builder
    @Schema(name = "GroupJoinPreview", description = "초대코드 기반 그룹 참여 Preview")
    public record JoinPreview(
            Long groupId,
            String name,
            int activeMemberCount,
            int maxMemberCount,
            int totalRoutineCount,
            List<JoinPreviewMember> members,
            boolean joinable,
            GroupJoinUnavailableReason unavailableReason
    ) {
    }

    /**
     * 참여 Preview에 표시하는 ACTIVE 구성원 요약이다.
     * 캐릭터 도메인이 도입되기 전에는 characterImageUrl이 null이며 내부 식별자는 노출하지 않는다.
     */
    @Builder
    @Schema(name = "GroupJoinPreviewMember", description = "그룹 참여 Preview 구성원 요약")
    public record JoinPreviewMember(
            String characterImageUrl
    ) {
    }

    /** 초대코드로 그룹 가입을 완료한 결과다. */
    @Builder
    @Schema(name = "GroupJoinResult", description = "초대코드 기반 그룹 가입 결과")
    public record JoinResult(
            Long groupId,
            String name,
            GroupMemberStatus memberStatus
    ) {
    }

    /** 그룹의 신규 참여 허용 여부를 전달한다. */
    @Builder
    @Schema(name = "GroupLockState", description = "그룹 방 잠금 상태")
    public record LockState(
            Long groupId,
            boolean isLocked
    ) {
    }
}
