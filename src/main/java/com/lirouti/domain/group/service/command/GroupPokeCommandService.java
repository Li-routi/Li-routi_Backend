package com.lirouti.domain.group.service.command;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupPoke;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberLockCandidate;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupPokeRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.notification.enums.NotificationCategory;
import com.lirouti.domain.notification.enums.NotificationType;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 그룹 구성원 찌르기의 권한 검증과 누적 카운터 증가를 처리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupPokeCommandService {
    private final GroupValidationService groupValidationService;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPokeRepository groupPokeRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 업적 진행도 이벤트의 conditionKey.
     */
    private static final String POKE_RECEIVED_COUNT_CONDITION_KEY = "POKE_RECEIVED_COUNT";
    private static final String POKE_COUNT_CONDITION_KEY = "POKE_COUNT";
    private static final String POKE_SOURCE_TYPE = "POKE";

    /**
     * 그룹 및 구성원 상태를 직렬화하여 안전하게 찌르기 및 업적 이벤트를 처리한다.
     */
    @Transactional
    public GroupResDTO.PokeResult poke(Long groupId, Long requesterMemberId, Long targetMemberId) {
        // 1. 탈퇴/강퇴 동시성 처리를 위해 그룹 행 비관적 잠금을 먼저 획득 (코드래빗 리뷰 반영)
        groupValidationService.lockActiveGroupForUpdate(groupId);

        // 2. 그룹 활성화 상태 및 요청자 검증
        groupValidationService.validateActiveGroupMember(groupId, requesterMemberId);

        if (requesterMemberId.equals(targetMemberId)) {
            throw new GroupException(GroupErrorCode.CANNOT_POKE_SELF);
        }

        // 3. 멤버십 잠금 순서 보장을 위해 ID 순으로 조회 후 비관적 잠금
        List<GroupMemberLockCandidate> candidates = groupMemberRepository
                .findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                        groupId,
                        List.of(requesterMemberId, targetMemberId)
                );

        Map<Long, GroupMember> membershipsByMemberId = candidates.stream()
                .map(candidate -> groupMemberRepository.findByIdForUpdate(candidate.groupMemberId())
                        .orElse(null))
                .filter(Objects::nonNull)
                .filter(membership -> membership.getStatus() == GroupMemberStatus.ACTIVE)
                .collect(Collectors.toMap(
                        membership -> membership.getMember().getId(),
                        Function.identity()
                ));

        if (!membershipsByMemberId.containsKey(requesterMemberId)) {
            throw new GroupException(GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
        }

        GroupMember targetMembership = membershipsByMemberId.get(targetMemberId);
        if (targetMembership == null) {
            throw new GroupException(GroupErrorCode.ACTIVE_GROUP_MEMBER_NOT_FOUND);
        }
        GroupMember requesterMembership = membershipsByMemberId.get(requesterMemberId);

        // 4. 찌르기 기록 저장
        GroupPoke groupPoke = groupPokeRepository.save(
                GroupPoke.builder()
                        .group(requesterMembership.getGroup())
                        .sender(requesterMembership.getMember())
                        .recipient(targetMembership.getMember())
                        .pokedDate(LocalDate.now())
                        .build()
        );

        targetMembership.increaseTotalPokeCount();

        // 5. 알림 이벤트 발행
        eventPublisher.publishEvent(new NotificationRequestedEvent(
                targetMemberId,
                NotificationCategory.GROUP_ROUTINE,
                NotificationType.GROUP_MEMBER_POKED,
                "그룹원이 회원님을 찔렀어요!",
                requesterMembership.getMember().getNickname() + "님이 루틴을 기다리고 있어요 🔥",
                groupId,
                requesterMemberId,
                "GROUP_MEMBER",
                deduplicationKey(groupPoke.getId())
        ));

        // 6. 업적 진행도 이벤트 발행 (찌른 사람 & 찔린 사람)
        eventPublisher.publishEvent(new AchievementProgressEvent(
                requesterMemberId,
                POKE_COUNT_CONDITION_KEY,
                1,
                POKE_SOURCE_TYPE,
                groupPoke.getId()
        ));

        eventPublisher.publishEvent(new AchievementProgressEvent(
                targetMemberId,
                POKE_RECEIVED_COUNT_CONDITION_KEY,
                1,
                POKE_SOURCE_TYPE,
                groupPoke.getId()
        ));

        log.info("그룹 구성원을 찔렀습니다. groupId={}, requesterMemberId={}, targetMemberId={}, totalPokeCount={}",
                groupId, requesterMemberId, targetMemberId, targetMembership.getTotalPokeCount());

        return new GroupResDTO.PokeResult(targetMemberId, targetMembership.getTotalPokeCount());
    }

    /** 각 poke 이력마다 별도 알림을 남기기 위한 키다. */
    private String deduplicationKey(Long groupPokeId) {
        return "group-poke:" + groupPokeId;
    }
}
