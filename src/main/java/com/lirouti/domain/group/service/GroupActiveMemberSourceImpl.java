package com.lirouti.domain.group.service;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.achievement.service.GroupActiveMemberSource;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.repository.GroupMemberRepository;

import lombok.RequiredArgsConstructor;

/**
 * {@code GroupActiveMemberSource} 를 group 도메인이 구현한다.
 * {@code MemberRoutineCompletionSource} 와 같은 위치의 패턴이다 — 인터페이스는 소비하는
 * 도메인(achievement)이 소유하고, 실제 데이터를 가진 도메인(group)이 구현한다.
 */
@Component
@RequiredArgsConstructor
public class GroupActiveMemberSourceImpl implements GroupActiveMemberSource {

    private final GroupMemberRepository groupMemberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Long> findActiveMemberIds(Long groupId) {
        return groupMemberRepository.findAllByGroupIdAndStatus(groupId, GroupMemberStatus.ACTIVE)
                .stream()
                .map(groupMember -> groupMember.getMember().getId())
                .toList();
    }
}
