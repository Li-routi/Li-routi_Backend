package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.GroupRoutineVerificationDisappointment;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

/** 아쉬워요 반응의 멱등 삽입·삭제·집계를 제공한다. */
public interface GroupRoutineVerificationDisappointmentRepository
        extends JpaRepository<GroupRoutineVerificationDisappointment, Long> {
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO group_routine_verification_disappointment
              (group_routine_verification_id, member_id, created_at, updated_at)
            VALUES (:verificationId, :memberId, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(@Param("verificationId") Long verificationId, @Param("memberId") Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from GroupRoutineVerificationDisappointment d where d.verification.id=:verificationId and d.member.id=:memberId")
    int deleteReaction(@Param("verificationId") Long verificationId, @Param("memberId") Long memberId);

    /** 재인증 전 interaction 초기화용. persistence context를 clear하지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupRoutineVerificationDisappointment d where d.verification.id = :verificationId")
    int deleteAllByVerificationId(@Param("verificationId") Long verificationId);

    long countByVerificationId(Long verificationId);
}
