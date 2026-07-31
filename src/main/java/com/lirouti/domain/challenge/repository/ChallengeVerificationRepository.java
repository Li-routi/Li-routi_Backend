package com.lirouti.domain.challenge.repository;

import com.lirouti.domain.challenge.entity.ChallengeVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

public interface ChallengeVerificationRepository
        extends JpaRepository<ChallengeVerification, Long>, ChallengeVerificationRepositoryCustom {

    /**
     * 현재 회차의 오늘 인증 행. 있으면 당일 재인증(덮어쓰기) 대상이다.
     * UNIQUE(member_challenge_id, participation_round, verified_date) 인덱스를 그대로 탄다.
     *
     * 이 조회로 "이미 인증했는지"를 판단하지만, 조회와 저장 사이의 동시 요청은 막지 못한다.
     * 그 경합은 위 유니크 제약이 DB에서 막는다.
     */
    Optional<ChallengeVerification> findByMemberChallengeIdAndParticipationRoundAndVerifiedDate(
            Long memberChallengeId,
            Integer participationRound,
            LocalDate verifiedDate
    );

    /**
     * 그 챌린지에 속한 인증인지까지 확인하며 조회한다(#15 신고용).
     *
     * 신고 경로가 /api/challenges/{challengeId}/verifications/{verificationId}/reports라
     * 두 값이 서로 맞는지 확인해야 한다. id만으로 찾으면 다른 챌린지의 인증을 이 챌린지 경로로
     * 신고할 수 있고, 응답만으로는 그 인증의 존재 여부가 드러난다.
     * 어긋나면 빈 값이 나가 404로 처리된다.
     */
    Optional<ChallengeVerification> findByIdAndMemberChallengeChallengeId(
            Long id,
            Long challengeId
    );

    /**
     * 신고 누적 판정을 위해 인증 행을 잠그고 조회한다.
     *
     * 잠금 없이 "저장 후 세기"만 하면 동시 신고가 서로의 미커밋 INSERT 를 보지 못해
     * 전부 임계값 미만으로 판단한다. 정확히 임계값만큼만 동시에 들어오면 그 뒤로 신고가
     * 없는 한 영원히 가려지지 않는다. 처음에는 "다음 신고에서 걸린다"고 봤는데, 담합 신고는
     * 오히려 동시에 몰리므로 그 가정이 성립하지 않는다.
     *
     * 신고는 드문 요청이라 이 잠금이 경합을 만들 일은 거의 없다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from ChallengeVerification v where v.id = :verificationId")
    Optional<ChallengeVerification> findByIdForUpdate(@Param("verificationId") Long verificationId);

    /**
     * 후보 key 중 인증 사진으로 실제 쓰이고 있는 것만 고른다.
     * 미참조 이미지 정리 배치가 이 결과로 삭제 대상을 판단한다.
     *
     * 정리 배치가 "지워도 되는가"를 이 결과로 판단하므로 <b>조건을 좁히면 안 된다.</b>
     * 탈퇴 회원의 인증도, 신고로 숨겨진 인증도 행이 남아 있는 한 그 파일은 살아 있다.
     * (탈퇴 회원 사진 삭제는 별도 정책이다)
     */
    @Query("select v.imageUrl from ChallengeVerification v where v.imageUrl in :keys")
    List<String> findImageUrlsIn(@Param("keys") Collection<String> keys);
}
