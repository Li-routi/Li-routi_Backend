package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.Group;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface GroupRepository extends JpaRepository<Group, Long> {

    /** 그룹 단위 개수 상한 검증과 후속 저장을 직렬화한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select groupEntity from Group groupEntity where groupEntity.id = :groupId")
    Optional<Group> findByIdForUpdate(@Param("groupId") Long groupId);

    Optional<Group> findByInviteCode(String inviteCode);

    // 새 초대코드가 기존 그룹에서 사용 중인지 확인
    boolean existsByInviteCode(String inviteCode);
}
