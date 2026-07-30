package com.lirouti.domain.group.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lirouti.domain.group.entity.Group;

public interface GroupRepository extends JpaRepository<Group, Long> {

    // 새 초대코드가 기존 그룹에서 사용 중인지 확인
    boolean existsByInviteCode(String inviteCode);
}
