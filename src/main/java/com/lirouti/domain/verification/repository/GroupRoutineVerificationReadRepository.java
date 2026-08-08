package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.GroupRoutineVerificationRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/** 그룹 루틴 인증 읽음 위치 저장소다. */
public interface GroupRoutineVerificationReadRepository
        extends JpaRepository<GroupRoutineVerificationRead, Long> {

    boolean existsByGroupIdAndMemberId(Long groupId, Long memberId);

    @Query("""
            select verificationRead.lastReadVerificationId
            from GroupRoutineVerificationRead verificationRead
            where verificationRead.group.id = :groupId
              and verificationRead.member.id = :memberId
            """)
    Optional<Long> findLastReadVerificationIdByGroupIdAndMemberId(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId
    );

    /** 회원·그룹별 읽음 위치를 생성하거나 더 큰 인증 ID로만 전진시킨다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO group_routine_verification_read (
                created_at, group_id, last_read_verification_id, member_id, read_at, updated_at
            ) VALUES (
                CURRENT_TIMESTAMP(6), :groupId, :verificationId, :memberId, :readAt, CURRENT_TIMESTAMP(6)
            ) ON DUPLICATE KEY UPDATE
                read_at = IF(
                    last_read_verification_id IS NULL OR last_read_verification_id < :verificationId,
                    :readAt, read_at
                ),
                updated_at = IF(
                    last_read_verification_id IS NULL OR last_read_verification_id < :verificationId,
                    CURRENT_TIMESTAMP(6), updated_at
                ),
                last_read_verification_id = IF(
                    last_read_verification_id IS NULL OR last_read_verification_id < :verificationId,
                    :verificationId, last_read_verification_id
                )
            """, nativeQuery = true)
    void upsertIfAhead(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("verificationId") Long verificationId,
            @Param("readAt") LocalDateTime readAt
    );

    /**
     * 그룹 hard delete 전에 읽음 위치를 먼저 정리한다.
     *
     * <p>Group 엔티티가 이 관계를 cascade하지 않으므로, FK 제약 때문에 그룹 삭제가 막히지 않게
     * 명시적으로 선행 삭제한다.
     */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GroupRoutineVerificationRead verificationRead
            where verificationRead.group.id = :groupId
            """)
    int deleteAllByGroupId(@Param("groupId") Long groupId);
}
