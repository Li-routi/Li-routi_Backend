package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.converter.GroupConverter;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.service.GroupValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 내부 호출은 Spring 트랜잭션 프록시를 우회한다.
 * REQUIRES_NEW 적용을 위해 발급 시도를 별도 Bean으로 유지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupInviteCodeIssueAttemptService {
    private final GroupValidationService groupValidationService;
    private final GroupRepository groupRepository;
    private final GroupInviteCodeGenerator inviteCodeGenerator;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public GroupResDTO.InviteCode issueOnce(Long groupId, Long memberId) {
        GroupMember ownerMembership = groupValidationService.validateGroupOwner(groupId, memberId);
        Group group = ownerMembership.getGroup();
        GroupInviteCodeGenerator.GeneratedInviteCode generatedInviteCode =
                inviteCodeGenerator.generate();

        group.issueInviteCode(generatedInviteCode.value(), generatedInviteCode.expiresAt());
        // unique 충돌은 호출자에게 전파해 현재 발급 시도를 롤백한다.
        groupRepository.saveAndFlush(group);

        log.debug("초대코드 발급 시도를 완료했습니다. groupId={}, memberId={}, expiresAt={}",
                groupId, memberId, generatedInviteCode.expiresAt());
        return GroupConverter.toInviteCodeResult(group);
    }
}
