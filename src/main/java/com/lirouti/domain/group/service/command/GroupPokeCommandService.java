package com.lirouti.domain.group.service.command;

import com.lirouti.domain.achievement.event.AchievementProgressEvent;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupPoke;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupMemberLockCandidate;
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
     * 업적 진행도 이벤트의 conditionKey. 쿡쿡을 보낸 사람 기준으로 쌓인다.
     */
    private static final String POKE_COUNT_CONDITION_KEY = "POKE_COUNT";
    private static final String POKE_SOURCE_TYPE = "POKE";

    /**
     * 요청자와 대상 참여 관계만 GroupMember ID 순서로 잠근 뒤 대상의 누적 찌르기 수를 증가시킨다.
     * 그룹 행은 잠그지 않아 서로 다른 구성원 쌍의 요청은 병렬로 처리할 수 있다.
     */
    @Transactional
    public GroupResDTO.PokeResult poke(Long groupId, Long requesterMemberId, Long targetMemberId) {
        groupValidationService.validateActiveGroupMember(groupId, requesterMemberId);
        if (requesterMemberId.equals(targetMemberId)) {
            throw new GroupException(GroupErrorCode.CANNOT_POKE_SELF);
        }

        List<GroupMemberLockCandidate> candidates = groupMemberRepository
                .findActiveMembershipLockCandidatesByGroupIdAndMemberIds(
                        groupId,
                        List.of(requesterMemberId, targetMemberId)
                );
        Map<Long, GroupMember> membershipsByMemberId = candidates.stream()
                .map(candidate -> groupMemberRepository.findByIdForUpdate(candidate.groupMemberId())
                        .orElse(null))
                .filter(java.util.Objects::nonNull)
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

        // 업적 진행도(POKE_COUNT)의 멱등키(sourceId)로 쓰기 위해 찌르기 1건마다 기록을 남긴다.
        // 하루 1회 같은 제한은 없다 - 같은 쌍이 하루에 여러 번 찔러도 매번 새 행이 쌓인다.
        GroupPoke groupPoke = groupPokeRepository.save(
                GroupPoke.builder()
                        .group(requesterMembership.getGroup())
                        .sender(requesterMembership.getMember())
                        .recipient(targetMembership.getMember())
                        .pokedDate(LocalDate.now())
                        .build()
        );

        targetMembership.increaseTotalPokeCount();
        eventPublisher.publishEvent(new NotificationRequestedEvent(
                targetMemberId,
                NotificationCategory.GROUP_ROUTINE,
                NotificationType.GROUP_MEMBER_POKED,
                "그룹원이 회원님을 찔렀어요!",
                membershipsByMemberId.get(requesterMemberId).getMember().getNickname()
                        + "님이 루틴을 기다리고 있어요 🔥",
                groupId,
                requesterMemberId,
                "GROUP_MEMBER",
                deduplicationKey(groupId, requesterMemberId, targetMemberId, targetMembership)
        ));

        // 업적 진행도는 찌른 사람(requester) 기준으로 쌓인다 - ACH-ST-002/012/013, ACH-SP-001 참고.
        // sourceId 로 방금 저장한 group_poke.id 를 써서, 이 이벤트가 재발행되더라도
        // AchievementProgressEventLog 의 unique 제약이 중복 반영을 막는다.
        eventPublisher.publishEvent(new AchievementProgressEvent(
                requesterMemberId,
                POKE_COUNT_CONDITION_KEY,
                1,
                POKE_SOURCE_TYPE,
                groupPoke.getId()
        ));
        log.info("그룹 구성원을 찔렀습니다. groupId={}, requesterMemberId={}, targetMemberId={}, totalPokeCount={}",
                groupId, requesterMemberId, targetMemberId, targetMembership.getTotalPokeCount());
        return new GroupResDTO.PokeResult(targetMemberId, targetMembership.getTotalPokeCount());
    }

    /**
     * 같은 누적 poke 알림의 기존 group-poke 키 convention을 유지하되, 재가입 회차도 구분한다.
     */
    private String deduplicationKey(
            Long groupId,
            Long requesterMemberId,
            Long targetMemberId,
            GroupMember targetMembership
    ) {
        return "group-poke:" + groupId + ":" + requesterMemberId + ":" + targetMemberId
                + ":" + targetMembership.getJoinedAt() + ":" + targetMembership.getTotalPokeCount();
    }
}
