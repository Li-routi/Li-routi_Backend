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
    int insertIfAbsent(
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

    /**
     * 그 인증에 달린 좋아요를 <b>전부</b> 지운다. 인증을 내릴 때 함께 부른다.
     *
     * <p>인증 삭제는 소프트 삭제라 행이 남고, <b>다시 올리면 같은 행이 되살아난다</b>
     * ({@code deletedAt = null}). 좋아요는 그 행을 가리키므로, 여기서 지우지 않으면 새로 올린
     * 사진이 <b>지운 사진에 달렸던 좋아요를 그대로 물려받는다.</b>
     *
     * <p>같은 자리에서 심사 결과와 시도 횟수는 이미 초기화한다 — "사진이 바뀌었으니 지난 심사
     * 결과는 이 사진의 것이 아니다"가 이유다. 좋아요도 같은 이유로 남으면 안 된다.
     *
     * <p><b>신고는 이렇게 다루지 않는다.</b> 지워서 신고 누적을 회피하는 길이 되므로 그대로 둔다.
     */
    //
    // clearAutomatically 를 켜지 않는다. 이 경로는 인증을 내리는 흐름 한가운데에서 불리는데,
    // 컨텍스트를 통째로 비우면 방금 softDelete 한 인증 엔티티까지 준영속이 되어 호출부가
    // 낡은 상태를 들고 남는다. 여기서 지우는 좋아요는 그 흐름이 들고 있지 않아 비울 이유가 없다.
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from ChallengeVerificationLike l
             where l.challengeVerification.id = :verificationId
            """)
    int deleteAllByVerificationId(@Param("verificationId") Long verificationId);
}
