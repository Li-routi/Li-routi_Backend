package com.lirouti.domain.group.service.command;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteCodeCommandService {
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789"; // 초대코드 생성에 사용될 문자열
    private static final int INVITE_CODE_LENGTH = 7; // 초대코드 길이
    private static final int MAX_GENERATION_ATTEMPTS = 10; // 초대코드 생성 최대 시도 횟수
    private static final Duration INVITE_CODE_VALIDITY = Duration.ofMinutes(10); // 초대코드 유효기간
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GroupValidationService groupValidationService;
    private final GroupRepository groupRepository;
    private final Clock clock;

    // 초대코드 발급 및 재발급
    @Transactional
    public GroupResDTO.InviteCode issueInviteCode(Long groupId, Long memberId) {
        GroupMember ownerMembership = groupValidationService.validateGroupOwner(groupId, memberId);
        Group group = ownerMembership.getGroup();
        LocalDateTime expiresAt = calculateExpiresAt();
        String inviteCode = generateUniqueInviteCode();

        group.issueInviteCode(inviteCode, expiresAt);
        saveGroup(group, groupId, memberId);

        log.info("그룹 초대코드 발급을 완료했습니다. groupId={}, memberId={}, expiresAt={}",
                groupId, memberId, expiresAt);

        return GroupConverter.toInviteCodeResult(group);
    }

    // 초대코드 만료 시간 계산
    private LocalDateTime calculateExpiresAt() {
        // 현재 시간 + 유효기간
        return LocalDateTime.now(clock).plus(INVITE_CODE_VALIDITY);
    }

    // 중복되지 않는 초대코드 생성
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

    // 랜덤 초대코드 생성
    private String generateInviteCode() {
        StringBuilder inviteCode = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            int characterIndex = SECURE_RANDOM.nextInt(INVITE_CODE_CHARACTERS.length());
            inviteCode.append(INVITE_CODE_CHARACTERS.charAt(characterIndex));
        }
        return inviteCode.toString();
    }

    
    private void saveGroup(Group group, Long groupId, Long memberId) {
        try {
            // saveAndFlush를 사용하여 DB단 예외를 해당 try-catch 안에서 잡음
            groupRepository.saveAndFlush(group);
        } catch (DataIntegrityViolationException exception) {
            log.warn("초대코드 저장 중 무결성 제약을 위반했습니다. groupId={}, memberId={}",
                    groupId, memberId);
            throw new GroupException(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
        }
    }
}
