package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** 그룹 생성 시 사용할 고유한 영구 초대코드 후보 생성기다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupInviteCodeGenerator {
    private static final String INVITE_CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ0123456789";
    private static final int INVITE_CODE_LENGTH = 7;
    private static final int MAX_GENERATION_ATTEMPTS = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final GroupRepository groupRepository;

    /** 현재 저장된 코드와 겹치지 않는 후보를 생성한다. */
    public String generate() {
        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String inviteCode = generateCandidate();
            if (!groupRepository.existsByInviteCode(inviteCode)) {
                return inviteCode;
            }
        }

        log.error("초대코드 유니크 값 생성에 반복해서 실패했습니다.");
        throw new GroupException(GroupErrorCode.INVITE_CODE_ISSUE_FAILED);
    }

    private String generateCandidate() {
        StringBuilder inviteCode = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
            int characterIndex = SECURE_RANDOM.nextInt(INVITE_CODE_CHARACTERS.length());
            inviteCode.append(INVITE_CODE_CHARACTERS.charAt(characterIndex));
        }
        return inviteCode.toString();
    }
}
