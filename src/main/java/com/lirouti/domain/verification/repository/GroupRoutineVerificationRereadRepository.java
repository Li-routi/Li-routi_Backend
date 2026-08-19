package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.GroupRoutineVerificationReread;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 재인증 후 다시 확인할 인증글 marker 저장소다. */
public interface GroupRoutineVerificationRereadRepository
        extends JpaRepository<GroupRoutineVerificationReread, Long> {

    /**
     * 해당 인증글을 이미 읽음 커서로 지난 현재 ACTIVE 타인 회원에게 marker를 만든다.
     *
     * <p>현재 미조회 목록과 같은 가입일 KST 00:00 기준을 적용한다. 유니크 키와 INSERT IGNORE가
     * 읽기 전 여러 번 재인증되는 경우에도 marker 하나만 남긴다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT IGNORE INTO group_routine_verification_reread (
                created_at, group_id, member_id, updated_at, verification_id
            )
            SELECT
                CURRENT_TIMESTAMP(6), group_member.group_id, group_member.member_id,
                CURRENT_TIMESTAMP(6), :verificationId
            FROM group_member
            JOIN group_routine_verification_read verification_read
              ON verification_read.group_id = group_member.group_id
             AND verification_read.member_id = group_member.member_id
            WHERE group_member.group_id = :groupId
              AND group_member.status = 'ACTIVE'
              AND group_member.member_id <> :authorId
              AND verification_read.last_read_verification_id >= :verificationId
              AND DATE(group_member.joined_at) <= DATE(:verificationCreatedAt)
            """, nativeQuery = true)
    int createForPreviouslyReadActiveMembers(
            @Param("groupId") Long groupId,
            @Param("authorId") Long authorId,
            @Param("verificationId") Long verificationId,
            @Param("verificationCreatedAt") LocalDateTime verificationCreatedAt
    );

    /** 확인한 재인증 글 한 건만 marker에서 제거한다. */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from GroupRoutineVerificationReread reread
            where reread.group.id = :groupId
              and reread.member.id = :memberId
              and reread.verificationId = :verificationId
            """)
    int deleteByGroupIdAndMemberIdAndVerificationId(
            @Param("groupId") Long groupId,
            @Param("memberId") Long memberId,
            @Param("verificationId") Long verificationId
    );

}
