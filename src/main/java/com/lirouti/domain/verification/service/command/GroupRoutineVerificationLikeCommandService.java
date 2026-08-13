package com.lirouti.domain.verification.service.command;

import java.util.List;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.service.command.GroupMemberActivityCommandService;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerificationLike;
import com.lirouti.domain.verification.converter.VerificationConverter;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationDisappointmentRepository;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;

/** 그룹 루틴 인증 게시물 좋아요의 권한·소속 검증과 멱등 쓰기 트랜잭션. */
@Service
@RequiredArgsConstructor
public class GroupRoutineVerificationLikeCommandService {

    /** achievement 도메인이 구독하는 좋아요 이벤트의 conditionKey. ACH-ST-001·010·011, ACH-SP-001 이 반응한다. */
    private static final String CONDITION_KEY_LIKE_COUNT = "LIKE_COUNT";
    private static final String SOURCE_TYPE_GROUP_ROUTINE_VERIFICATION_LIKE = "GROUP_ROUTINE_VERIFICATION_LIKE";

    private final GroupValidationService groupValidationService;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupRoutineVerificationRepository groupRoutineVerificationRepository;
    private final GroupRoutineVerificationLikeRepository groupRoutineVerificationLikeRepository;
    private final GroupRoutineVerificationDisappointmentRepository disappointmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public VerificationResDTO.GroupRoutineLike like(
            Long memberId,
            Long groupId,
            Long verificationId
    ) {
        GroupRoutineVerification verification = validateLikeTarget(memberId, groupId, verificationId);
        // 기존 단위 테스트의 직접 생성 경로에서는 신규 저장소가 null일 수 있다.
        // Spring 운영 빈에서는 항상 주입되어 좋아요/아쉬워요 상호 배타성을 보장한다.
        if (disappointmentRepository != null) {
            disappointmentRepository.deleteReaction(verificationId, memberId);
        }
        int inserted = groupRoutineVerificationLikeRepository.insertIfAbsent(verificationId, memberId);
        if (inserted == 1) {
            publishLikeCountEvent(memberId, verificationId);
            AuthorVerificationContext author = authorContext(verification);
            GroupMember authorMembership = lockAuthorMembership(groupId, author.memberId());
            groupMemberRepository.incrementTotalLikeCountForCurrentActiveMembership(
                    authorMembership.getId(), author.assignmentId());
            if (!author.memberId().equals(memberId) && eventPublisher != null) {
                GroupMember actor = groupValidationService.validateActiveGroupMember(groupId, memberId);
                if (actor != null) eventPublisher.publishEvent(new NotificationRequestedEvent(author.memberId(),
                        NotificationCategory.GROUP_ROUTINE, NotificationType.GROUP_VERIFICATION_LIKED,
                        "그룹 인증에 좋아요가 달렸어요",
                        actor.getMember().getNickname() + "님이 회원님의 인증을 좋아합니다.",
                        groupId, verificationId, "GROUP_ROUTINE_VERIFICATION",
                        "group-like:" + verificationId + ":" + memberId));
            }
        }
        return buildResult(verificationId, true);
    }

    @Transactional
    public VerificationResDTO.GroupRoutineLike unlike(
            Long memberId,
            Long groupId,
        Long verificationId
    ) {
        GroupRoutineVerification verification = validateLikeTarget(memberId, groupId, verificationId);
        GroupRoutineVerificationLike existingLike = groupRoutineVerificationLikeRepository
                .findByVerificationIdAndMemberIdForUpdate(verificationId, memberId)
                .orElse(null);
        if (existingLike != null) {
            AuthorVerificationContext author = authorContext(verification);
            if (groupRoutineVerificationLikeRepository.deleteLike(verificationId, memberId) != 1) {
                return buildResult(verificationId, false);
            }
            GroupMember authorMembership = lockAuthorMembership(groupId, author.memberId());
            groupMemberRepository.decrementTotalLikeCountForCurrentActiveMembershipIfPositive(
                    authorMembership.getId(), author.assignmentId());
        }
        return buildResult(verificationId, false);
    }

    /**
     * 좋아요를 누른 회원(memberId, 글쓴이가 아니라 <b>액션을 한 사람</b>)의 achievement 진행도를 올린다.
     *
     * <p>{@code sourceId} 로 verificationId(좋아요 눌린 게시물)를 쓴다. 이 값은 여러 회원이
     * 공유할 수 있으므로 — 같은 게시물에 여러 명이 좋아요를 누르면 sourceId가 같다 — 멱등성
     * 판정 키에 memberId가 반드시 포함되어야 한다(achievement_progress_event_log 의
     * uk_achievement_progress_event_log 가 member_id를 포함하도록 마이그레이션되어 있다).
     * memberId 없이 판정하면 두 번째 회원의 좋아요가 "이미 처리됨"으로 오판돼 스킵된다.
     */
    private void publishLikeCountEvent(Long memberId, Long verificationId) {
        if (eventPublisher == null) {
            return;
        }
        eventPublisher.publishEvent(new AchievementProgressEvent(
                memberId,
                CONDITION_KEY_LIKE_COUNT,
                1,
                SOURCE_TYPE_GROUP_ROUTINE_VERIFICATION_LIKE,
                verificationId
        ));
    }

    /**
     * 탈퇴·강제 퇴장과 같은 그룹 행 잠금을 먼저 획득한 뒤 ACTIVE 참여 관계를 검증한다.
     * 그룹 명령도 같은 순서(그룹 잠금 → 구성원 검증)를 쓰므로, 검증 뒤 새 Like 행이 생기는
     * 시간 창이 없다. 인증 소속 조회는 그 뒤에 하며 추가 잠금을 잡지 않는다.
     */
    private GroupRoutineVerification validateLikeTarget(
            Long memberId,
            Long groupId,
            Long verificationId
    ) {
        groupValidationService.lockActiveGroupForUpdate(groupId);
        groupValidationService.validateActiveGroupMember(groupId, memberId);
        return groupRoutineVerificationRepository.findByIdAndGroupId(verificationId, groupId)
                .orElseThrow(() -> new VerificationException(
                        VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND));
    }

    private AuthorVerificationContext authorContext(GroupRoutineVerification verification) {
        return new AuthorVerificationContext(
                verification.getAssignment().getMember().getId(),
                verification.getAssignment().getId()
        );
    }

    private GroupMember lockAuthorMembership(Long groupId, Long authorMemberId) {
        return groupMemberActivityCommandService.lockMembership(groupId, authorMemberId);
    }

    private record AuthorVerificationContext(Long memberId, Long assignmentId) {
    }

    private VerificationResDTO.GroupRoutineLike buildResult(Long verificationId, boolean liked) {
        long likeCount = groupRoutineVerificationLikeRepository
                .countByVerificationIds(List.of(verificationId))
                .getOrDefault(verificationId, 0L);
        return VerificationConverter.toGroupRoutineLike(verificationId, likeCount, liked);
    }
}
