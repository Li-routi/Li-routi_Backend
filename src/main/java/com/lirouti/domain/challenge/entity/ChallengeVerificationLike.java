package com.lirouti.domain.challenge.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 인증 게시물에 대한 좋아요(#63). 챌린지가 아니라 인증 한 건에 붙는다.
 *
 * 취소는 행 삭제다. 소프트 삭제를 쓰면 취소 후 다시 누를 때 남아 있는 행이 유니크 제약에
 * 걸린다 — MySQL 유니크 제약은 deleted_at IS NULL을 모른다. 좋아요는 신고와 달리 취소가
 * 일상적으로 반복되는 동작이라 이 문제가 바로 드러난다(database-schema.md).
 *
 * 유니크 제약이 중복을 막지만, 신고와 달리 위반을 오류로 돌려주지 않고 성공으로 처리한다.
 * 좋아요는 토글이라 같은 요청이 두 번 오는 것이 정상 사용이다.
 *
 * 당일 재인증은 인증 행을 덮어쓰므로(삭제·재생성이 아니다) 좋아요가 새 사진에 승계된다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "challenge_verification_like",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_verification_like_verification_member",
                        columnNames = {"challenge_verification_id", "member_id"}
                )
        }
)
public class ChallengeVerificationLike extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_verification_id", nullable = false)
    private ChallengeVerification challengeVerification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Builder
    private ChallengeVerificationLike(ChallengeVerification challengeVerification, Member member) {
        this.challengeVerification = challengeVerification;
        this.member = member;
    }
}
