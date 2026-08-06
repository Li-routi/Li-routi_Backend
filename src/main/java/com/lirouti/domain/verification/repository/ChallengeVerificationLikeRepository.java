package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.ChallengeVerificationLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChallengeVerificationLikeRepository
        extends JpaRepository<ChallengeVerificationLike, Long>, ChallengeVerificationLikeRepositoryCustom {

    /**
     * 좋아요를 남긴다. <b>이미 있으면 아무 일도 하지 않고 성공한다</b>.
     *
     * JPA save + 제약 위반 잡기로 하지 않은 이유가 있다. 유니크 제약 위반이 나면 영속성 컨텍스트가
     * 깨지고 <b>트랜잭션이 롤백 전용으로 표시된다.</b> 예외를 잡아 "이미 눌림"으로 넘겨도 이어지는
     * 집계 쿼리가 커밋 시점에 UnexpectedRollbackException으로 터진다.
     *
     * ON DUPLICATE KEY UPDATE는 <b>애초에 예외를 만들지 않아</b> 그 문제가 없다. INSERT IGNORE가
     * 아닌 것은 그쪽이 FK 위반까지 경고로 삼켜, 없는 인증에 좋아요가 조용히 실패하기 때문이다.
     * {@code id = id}는 아무것도 바꾸지 않는 갱신이다.
     *
     * created_at·updated_at은 네이티브 INSERT라 JPA Auditing이 개입하지 않으므로 직접 넣는다.
     *
     * flushAutomatically만 켠다. 같은 트랜잭션에서 방금 만든 인증이 아직 DB에 없으면 FK가 깨지므로
     * 앞선 변경을 먼저 내보내야 한다. 반면 clearAutomatically는 켜지 않는다 — INSERT가 다른 타입의
     * 영속 엔티티를 낡게 만들지 않고, 이어지는 집계도 영속성 컨텍스트가 아닌 DB 쿼리라 영향이 없다.
     * 켜면 호출자가 들고 있던 엔티티까지 detach되어 그 뒤 변경이 조용히 사라진다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO challenge_verification_like
                (challenge_verification_id, member_id, created_at, updated_at)
            VALUES (:verificationId, :memberId, NOW(6), NOW(6))
            ON DUPLICATE KEY UPDATE id = id
            """, nativeQuery = true)
    void insertIfAbsent(
            @Param("verificationId") Long verificationId,
            @Param("memberId") Long memberId
    );

    /**
     * 취소는 행 삭제다(하드 삭제). 좋아요가 없으면 0을 돌려주고, 호출부는 그것도 성공으로 본다 —
     * 좋아요·취소는 멱등하다(database-schema.md).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            delete from ChallengeVerificationLike l
             where l.challengeVerification.id = :verificationId
               and l.member.id = :memberId
            """)
    int deleteLike(
            @Param("verificationId") Long verificationId,
            @Param("memberId") Long memberId
    );
}
