package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.GroupRoutineVerificationLike;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface GroupRoutineVerificationLikeRepository
        extends JpaRepository<GroupRoutineVerificationLike, Long>, GroupRoutineVerificationLikeRepositoryCustom {

    Optional<GroupRoutineVerificationLike> findByGroupRoutineVerificationIdAndMemberId(
            Long verificationId,
            Long memberId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select likeEntity
            from GroupRoutineVerificationLike likeEntity
            where likeEntity.groupRoutineVerification.id = :verificationId
              and likeEntity.member.id = :memberId
            """)
    Optional<GroupRoutineVerificationLike> findByVerificationIdAndMemberIdForUpdate(
            @Param("verificationId") Long verificationId,
            @Param("memberId") Long memberId
    );

    /** 이미 있으면 예외 없이 그대로 성공한다. FK 위반을 숨기지 않기 위해 INSERT IGNORE는 쓰지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO group_routine_verification_like
                (group_routine_verification_id, member_id, created_at, updated_at)
            VALUES (:verificationId, :memberId, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("verificationId") Long verificationId,
            @Param("memberId") Long memberId
    );

    /** 좋아요가 없으면 0건 삭제로 성공한다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from GroupRoutineVerificationLike likeEntity
             where likeEntity.groupRoutineVerification.id = :verificationId
               and likeEntity.member.id = :memberId
            """)
    int deleteLike(
            @Param("verificationId") Long verificationId,
            @Param("memberId") Long memberId
    );

    /** 재인증 전 interaction 초기화용. persistence context를 clear하지 않는다. */
    @Modifying(flushAutomatically = true)
    @Query("delete from GroupRoutineVerificationLike likeEntity where likeEntity.groupRoutineVerification.id = :verificationId")
    int deleteAllByVerificationId(@Param("verificationId") Long verificationId);
}
