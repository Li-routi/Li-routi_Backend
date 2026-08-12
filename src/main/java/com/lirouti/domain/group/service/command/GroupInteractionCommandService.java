package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupInteractionResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupPoke;
import com.lirouti.domain.group.repository.GroupPokeRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import com.lirouti.domain.notification.enums.*;
import com.lirouti.domain.notification.event.NotificationRequestedEvent;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.*;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;

/** 그룹 인증 아쉬워요와 하루 1회 찌르기를 원자적으로 처리한다. */
@Service @RequiredArgsConstructor
public class GroupInteractionCommandService {
    private final GroupValidationService validationService;
    private final GroupRoutineVerificationRepository verificationRepository;
    private final GroupRoutineVerificationLikeRepository likeRepository;
    private final GroupRoutineVerificationDisappointmentRepository disappointmentRepository;
    private final GroupMemberActivityCommandService groupMemberActivityCommandService;
    private final com.lirouti.domain.group.repository.GroupMemberRepository groupMemberRepository;
    private final GroupPokeRepository pokeRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 좋아요를 제거한 뒤 아쉬워요를 멱등 등록해 두 반응이 동시에 남지 않게 한다. */
    @Transactional
    public GroupInteractionResDTO.Disappointment disappoint(Long memberId, Long groupId, Long verificationId) {
        validationService.lockActiveGroupForUpdate(groupId);
        GroupMember actor = validationService.validateActiveGroupMember(groupId, memberId);
        GroupRoutineVerification verification = verificationRepository.findByIdAndGroupId(verificationId, groupId)
                .orElseThrow(() -> new VerificationException(VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND));
        Long assignmentId = verification.getAssignment().getId();
        Long authorId = verification.getAssignment().getMember().getId();
        String actorNickname = actor.getMember().getNickname();
        int deletedLike = likeRepository.deleteLike(verificationId, memberId);
        if (deletedLike == 1) {
            GroupMember authorMembership = groupMemberActivityCommandService.lockMembership(
                    groupId, authorId);
            groupMemberRepository.decrementTotalLikeCountForCurrentActiveMembershipIfPositive(
                    authorMembership.getId(), assignmentId);
        }
        int inserted = disappointmentRepository.insertIfAbsent(verificationId, memberId);
        if (inserted == 1 && !authorId.equals(memberId)) {
            eventPublisher.publishEvent(new NotificationRequestedEvent(authorId, NotificationCategory.GROUP_ROUTINE,
                    NotificationType.GROUP_VERIFICATION_DISAPPOINTED, "그룹원이 인증을 응원하고 있어요",
                    actorNickname+"님이 인증에 아쉬워요를 남겼습니다.", groupId,
                    verificationId, "GROUP_ROUTINE_VERIFICATION",
                    "group-disappointment:"+verificationId+":"+memberId));
        }
        return result(verificationId, true);
    }

    /** 아쉬워요가 없어도 성공하는 멱등 취소다. */
    @Transactional
    public GroupInteractionResDTO.Disappointment undisappoint(Long memberId, Long groupId, Long verificationId) {
        validationService.lockActiveGroupForUpdate(groupId);
        validationService.validateActiveGroupMember(groupId, memberId);
        if (verificationRepository.findByIdAndGroupId(verificationId, groupId).isEmpty())
            throw new VerificationException(VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND);
        disappointmentRepository.deleteReaction(verificationId, memberId);
        return result(verificationId, false);
    }

    /** 자기 자신을 제외한 활성 그룹원에게 그룹별 하루 한 번만 찌르기를 보낸다. */
    @Transactional
    public GroupInteractionResDTO.Poke poke(Long senderId, Long groupId, Long recipientId) {
        if (senderId.equals(recipientId)) throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        Group group = validationService.lockActiveGroupForUpdate(groupId);
        GroupMember sender = validationService.validateActiveGroupMember(groupId, senderId);
        GroupMember recipient = validationService.validateActiveGroupMember(groupId, recipientId);
        LocalDate today = LocalDate.now(clock);
        try {
            pokeRepository.saveAndFlush(GroupPoke.builder().group(group).sender(sender.getMember())
                    .recipient(recipient.getMember()).pokedDate(today).build());
        } catch (DataIntegrityViolationException duplicate) {
            throw new GeneralException(GeneralErrorCode.CONFLICT);
        }
        eventPublisher.publishEvent(new NotificationRequestedEvent(recipientId, NotificationCategory.GROUP_ROUTINE,
                NotificationType.GROUP_MEMBER_POKED, "그룹원이 회원님을 찔렀어요!",
                sender.getMember().getNickname()+"님이 루틴을 기다리고 있어요 🔥", groupId, senderId,
                "GROUP_MEMBER", "group-poke:"+groupId+":"+senderId+":"+recipientId+":"+today));
        return new GroupInteractionResDTO.Poke(groupId, recipientId, true);
    }

    private GroupInteractionResDTO.Disappointment result(Long verificationId, boolean active) {
        return new GroupInteractionResDTO.Disappointment(verificationId,
                disappointmentRepository.countByVerificationId(verificationId), active);
    }
}
