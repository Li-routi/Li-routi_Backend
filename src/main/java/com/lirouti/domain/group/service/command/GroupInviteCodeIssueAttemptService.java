package com.lirouti.domain.group.service.command;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 내부 호출은 Spring 트랜잭션 프록시를 우회한다.
 * REQUIRES_NEW 적용을 위해 발급 시도를 별도 Bean으로 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteCodeIssueAttemptService {
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final Duration INVITE_CODE_VALIDITY = Duration.ofMinutes(10);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GroupValidationService groupValidationService;
    private final GroupRepository groupRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupResDTO.InviteCode issueOnce(Long groupId, Long memberId) {
        GroupMember ownerMembership = groupValidationService.validateGroupOwner(groupId, memberId);
        Group group = ownerMembership.getGroup();
        String inviteCode = generateUniqueInviteCode();
        LocalDateTime expiresAt = calculateExpiresAt();

        group.issueInviteCode(inviteCode, expiresAt);
        // unique 충돌은 호출자에게 전파해 현재 발급 시도를 롤백한다.
        groupRepository.saveAndFlush(group);

        log.debug("초대코드 발급 시도를 완료했습니다. groupId={}, memberId={}, expiresAt={}",
                groupId, memberId, expiresAt);
        return GroupConverter.toInviteCodeResult(group);
    }

    private LocalDateTime calculateExpiresAt() {
        return LocalDateTime.now(clock).plus(INVITE_CODE_VALIDITY);
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = generateInviteCode();
            if (!groupRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }

        log.error("초대코드 유니크 값 생성에 반복해서 실패했습니다.");
        throw new GroupException(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
    }

    private String generateInviteCode() {
        StringBuilder inviteCode = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int characterIndex = SECURE_RANDOM.nextInt(INVITE_CODE_CHARACTERS.length());
            inviteCode.append(INVITE_CODE_CHARACTERS.charAt(characterIndex));
        }
        return inviteCode.toString();
    }
}
