package com.lirouti.domain.verification.repository;

import com.lirouti.domain.verification.entity.ChallengeVerification;
import com.lirouti.domain.verification.enums.ReviewStatus;
import org.springframework.data.domain.Pageable;
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
     * 현재 회차의 이번 구간 인증 행. DAILY 에서 있으면 당일 재인증(덮어쓰기) 대상이다.
     * UNIQUE(member_challenge_id, participation_round, period_start_date) 인덱스의 선두 두 컬럼을 탄다.
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
     * 그 챌린지에 속한 인증인지까지 확인하며 조회한다. 신고가 대상 인증을 찾을 때 쓴다.
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
     * 그 참여의 <b>이번 구간 인증</b>을 회차와 무관하게 찾는다.
     *
     * <p>주기 1회는 회차를 넘어 적용된다. 회차를 조건에 넣으면 나갔다 다시 들어온 뒤
     * 같은 구간에 또 인증할 수 있다 — 새 회차에서는 기존 인증이 안 보여 덮어쓰기가 아니라
     * 새 행이 된다. 실제로 운영에서 그렇게 두 건이 생겼다.
     *
     * <p>{@code participation_round} 가 유니크 키에 들어 있어 DB 는 이것을 막지 않는다.
     * 대신 호출부가 참여 행을 비관 잠금으로 잡은 뒤 이 조회를 하므로, 같은 회원의 동시
     * 요청은 그 잠금에서 직렬화된다.
     *
     * <p>여러 건이면 <b>살아 있는 것을 먼저</b> 준다. 정책 도입 전에 쌓인 중복이 있어
     * 단건 조회로는 예외가 나는데(그 데이터는 지우지 않기로 했다), 그냥 최근 회차 순으로
     * 주면 <b>지워진 최근 행이 살아 있는 옛 행을 가린다.</b> 그러면 이미 인증이 있는데도
     * 호출부가 "비어 있다" 로 판단해 한 구간에 두 건이 생긴다. 운영에 그 중복이 실재한다.
     *
     * <p>⚠️ <b>{@code deleted_at IS NULL} 을 붙이면 안 된다.</b> 붙이면 내린 글을 못 찾아
     * INSERT 로 가고 유니크 제약에 걸린다 — 지운 뒤 같은 구간에 다시 인증하는 길이 막힌다.
     * 거르는 대신 <b>순서로 푼다.</b>
     */
    @Query("""
            select v from ChallengeVerification v
            where v.memberChallenge.id = :memberChallengeId
              and v.periodStartDate = :periodStartDate
            order by case when v.deletedAt is null then 0 else 1 end,
                     v.participationRound desc, v.id desc
            limit 1
            """)
    Optional<ChallengeVerification> findByMemberChallengeIdAndPeriodStart(
            @Param("memberChallengeId") Long memberChallengeId,
            @Param("periodStartDate") LocalDate periodStartDate
    );

    /**
     * 그 참여가 <b>주어진 구간 안에</b> 인증한 적이 있는지. 상세의 "현재 주기에 인증했는지" 판정용이다.
     *
     * <p><b>저장 경로와 같은 컬럼을 본다.</b> 예전에는 {@code verified_date} 의 범위로 물었는데,
     * 그러면 구간 경계 계산이 조회와 저장에 두 벌로 존재해 한쪽만 틀어질 수 있었다. 지금은
     * 저장할 때 못 박아 둔 {@code period_start_date} 를 그대로 비교하므로 어긋날 여지가 없다.
     *
     * <p>회차를 조건에 넣지 않는다. 재참여로 회차가 올라가도 그 구간에 인증한 사실은 남는다 —
     * 회차를 넣으면 나갔다 들어온 뒤 버튼이 다시 열린다.
     *
     * <p><b>내려간 글은 세지 않는다.</b> 지우면 그 구간이 다시 열리므로(재화 회수가 그 자리를
     * 막는다), 버튼도 다시 열려야 한다. 세면 <b>서버는 재인증을 받아 주는데 버튼이 잠긴 채라
     * 사용자가 거기 닿을 수 없다.</b>
     *
     * <p>예전에는 일부러 셌다 — 그때는 "지우면 하루 1회가 뚫린다" 가 근거였다. 뚫리는 것을
     * 막는 역할이 리워드 회수로 넘어가면서 그 근거가 사라졌다.
     *
     * <p>엔티티가 아니라 존재 여부만 돌려준다. 판정에 쓸 뿐이라 행을 읽을 필요가 없다.
     */
    boolean existsByMemberChallengeIdAndPeriodStartDateAndDeletedAtIsNull(
            Long memberChallengeId,
            LocalDate periodStartDate
    );

    /**
     * 그 챌린지의 인증이면서 <b>작성자가 요청자인지</b>까지 확인하며 조회한다. 메모 수정용이다.
     *
     * <p>세 조건을 모두 쿼리에 넣는다. 가져와서 뒤에서 비교하면 <b>응답만으로 그 인증의 존재
     * 여부와 작성자가 드러난다</b> — 남의 글에 404 대신 403 이 나가면 "그 id 는 있다"를 알려주는
     * 셈이다. 어긋나면 빈 값이 나가 호출부가 404 로 바꾼다.
     *
     * <p><b>가려진 인증({@code hiddenAt} 이 있는 것)은 제외한다.</b> 신고가 임계값만큼 쌓이면
     * 작성자 본인에게도 보이지 않는데, 보이지 않는 글을 고칠 수 있으면 화면에 없는 진입점이
     * 열려 있는 셈이다. 고쳐도 되살아나지 않는다는 점에서도(해제 경로가 없다) 허용할 이유가 없다.
     */
    @Query("""
            select v from ChallengeVerification v
            where v.id = :verificationId
              and v.memberChallenge.challenge.id = :challengeId
              and v.memberChallenge.member.id = :memberId
              and v.hiddenAt is null
            """)
    Optional<ChallengeVerification> findMineInChallenge(
            @Param("verificationId") Long verificationId,
            @Param("challengeId") Long challengeId,
            @Param("memberId") Long memberId
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
    /**
     * 미참조 정리가 "살아 있는 사진" 을 가리는 데 쓴다.
     *
     * <p><b>내려간 글의 key 는 참조로 세지 않는다.</b> 그 사진은 삭제 시점에 지웠어야 하는
     * 것이고, 그때 S3 호출이 실패하면 조용히 넘어간다({@code deleteQuietly}). 참조로 세면
     * 미참조 정리가 그것을 가져가지 못해 <b>지운 사진이 공개 prefix 에 영영 남는다</b> —
     * 두 번째 방어선이 첫 번째 실패를 못 받는 셈이다.
     *
     * <p>내려간 글을 다시 올리면 승격이 새 key 를 만들므로, 옛 key 가 되살아나 쓰이는 일은 없다.
     */
    @Query("""
            select v.imageUrl from ChallengeVerification v
            where v.imageUrl in :keys
              and v.deletedAt is null
            """)
    List<String> findImageUrlsIn(@Param("keys") Collection<String> keys);

    /**
     * 마이 > 내인증 화면용. 그 회원이 특정 날짜에 남긴 챌린지 인증 전부를 가져온다.
     *
     * 화면에 챌린지 이름을 함께 써야 해 challenge까지 fetch join한다. 가려진(hiddenAt)
     * 인증도 포함한다 — 이 화면은 신고 여부와 무관하게 본인이 남긴 기록을 보여주는 것이라,
     * 남에게는 숨겨졌어도 작성자 본인에게는 보여야 한다(신고 피드 필터와 다른 기준).
     *
     * <p><b>심사 보류 건도 포함한다.</b> 방금 올린 사진이 화면에서 사라지면 안 되기 때문이다.
     * 챌린지별 내 인증 목록과 같은 기준이다 — 한쪽만 다르면 같은 사진이 한 화면에서는
     * "심사 중", 다른 화면에서는 그냥 인증으로 보인다.
     *
     * <p><b>작성자가 내린 글은 뺀다.</b> 본인이 지운 것이라 본인에게도 보이면 안 된다 —
     * 신고 숨김({@code hiddenAt})을 본인에게는 보여 주는 것과 반대다.
     *
     * <p>{@code status} 를 주면 그 상태만 남긴다. 주지 않으면 전부다 — 기본값이 바뀌면
     * 파라미터를 안 보내는 기존 클라이언트의 화면이 조용히 달라진다.
     */
    @Query("""
            select v from ChallengeVerification v
            join fetch v.memberChallenge mc
            join fetch mc.challenge
            where mc.member.id = :memberId
              and v.verifiedDate = :date
              and v.deletedAt is null
              and (:status is null or v.reviewStatus = :status)
            order by v.verifiedAt desc
            """)
    List<ChallengeVerification> findByMemberAndDate(
            @Param("memberId") Long memberId,
            @Param("date") LocalDate date,
            @Param("status") ReviewStatus status
    );

    /**
     * 리포트 집계용. 기간 내 회원의 챌린지 인증 건수. 완료한 챌린지가 아니라 "인증한 횟수"다
     * (한 회원이 여러 챌린지에 참여하면 하루에도 여러 건일 수 있다) — 개인/그룹 루틴과 달리
     * 달성률 계산에는 넣지 않고 별도 지표로만 보여주기로 했다.
     */
    @Query("""
            select count(v)
            from ChallengeVerification v
            where v.memberChallenge.member.id = :memberId
              and v.verifiedDate between :start and :end
            """)
    long countByMemberAndDateBetween(
            @Param("memberId") Long memberId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    /**
     * 재심사 대상. 오래 기다린 것부터 가져온다.
     *
     * <p>인덱스 {@code (review_status, pending_since)} 를 그대로 탄다. 전체에서 보류는 극소수라
     * 선두 컬럼만으로도 후보가 크게 줄어든다.
     *
     * <p>챌린지 이름·설명이 심사 입력이라 함께 읽는다. 없으면 건마다 조회하는 N+1 이 된다.
     */
    @Query("""
            select v from ChallengeVerification v
            join fetch v.memberChallenge mc
            join fetch mc.challenge
            where v.reviewStatus = com.lirouti.domain.verification.enums.ReviewStatus.PENDING
            order by v.pendingSince asc
            """)
    List<ChallengeVerification> findPendingOldestFirst(Pageable pageable);

    /** 보류 건수. 쌓이는 것을 아무도 모르는 상태가 가장 나쁘다 — 주기적으로 로그에 남긴다. */
    long countByReviewStatus(ReviewStatus reviewStatus);

    /**
     * 그 회차의 <b>유효한</b> 인증 날짜를 오름차순으로. 스트릭을 다시 셀 때 쓴다.
     *
     * <p>보류는 제외한다 — 반려로 확정된 건을 빼고 세는 것이 목적이므로, 아직 판정이 안 난
     * 것까지 넣으면 같은 문제가 남는다.
     */
    @Query("""
            select v.verifiedDate from ChallengeVerification v
            where v.memberChallenge.id = :memberChallengeId
              and v.participationRound = :participationRound
              and v.reviewStatus = com.lirouti.domain.verification.enums.ReviewStatus.APPROVED
            order by v.verifiedDate asc
            """)
    List<LocalDate> findApprovedDatesInRound(
            @Param("memberChallengeId") Long memberChallengeId,
            @Param("participationRound") Integer participationRound
    );

}
