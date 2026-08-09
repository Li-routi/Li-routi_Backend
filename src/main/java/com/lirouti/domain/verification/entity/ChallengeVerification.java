package com.lirouti.domain.verification.entity;

import com.lirouti.domain.challenge.entity.MemberChallenge;
import com.lirouti.domain.verification.enums.ReviewStatus;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 챌린지 참여의 일자별 인증 이력. 한 참여 회차 안에서 하루에 한 건만 존재한다.
 *
 * 소프트 삭제는 쓰지 않는다 — 당일 재인증은 삭제 후 재등록이 아니라 {@link #reverify}로 덮어쓴다.
 *
 * 피드 조회용 별도 인덱스는 두지 않는다. 아래 유니크 제약의 선두 컬럼이 member_challenge_id라,
 * 그 인덱스가 피드의 참여 조인과 "이번 구간 인증 행 찾기"를 커버한다.
 *
 * 다만 챌린지 단위 피드의 정렬(id DESC + 커서 페이징)까지 커버하지는 못한다 — 그 챌린지에 달린
 * 인증 행을 모아서 정렬해야 하므로, 인증이 쌓일수록 페이지 한 장의 비용도 같이 늘어난다.
 * 실측으로 문제가 확인되면 challenge_id 비정규화 + (challenge_id, id DESC) 인덱스를 검토한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "challenge_verification",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_verification_round_date",
                        columnNames = {"member_challenge_id", "participation_round", "verified_date"}
                )
        }
)
public class ChallengeVerification extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_challenge_id", nullable = false)
    private MemberChallenge memberChallenge;

    // 인증 당시의 참여 회차 스냅샷. 현재 회차 인증만 조회할 때 member_challenge의 회차와 비교한다.
    @Column(name = "participation_round", nullable = false)
    private Integer participationRound;

    @Column(name = "verified_date", nullable = false)
    private LocalDate verifiedDate;

    /**
     * 이 인증이 속한 주기 구간의 첫날. <b>유니크 키에 들어가 "주기 1회"를 DB 가 보장한다.</b>
     *
     * <p>{@code DAILY} 면 {@code verifiedDate} 와 같은 값이고, {@code WEEKLY} 면 그 주 일요일,
     * {@code MONTHLY} 면 그 달 1일이다. 애플리케이션에서 "이번 주에 인증이 있나"를 조회해 막는
     * 것만으로는 동시 요청 두 건이 모두 통과하므로, 제약을 이 컬럼으로 옮겼다.
     *
     * <p><b>챌린지의 주기를 나중에 바꾸면 옛 행은 옛 기준으로 남는다.</b> 예를 들어 DAILY 로
     * 쌓인 행들은 저마다 다른 {@code period_start_date} 를 들고 있어서, WEEKLY 로 바꾼 직후에는
     * 같은 주에 한 건 더 들어갈 수 있다. 주기를 바꾸는 것은 운영 작업이므로 그때 백필을 함께
     * 해야 한다 — 코드로 막지 않고 여기 적어 둔다.
     */
    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "verified_at", nullable = false)
    private LocalDateTime verifiedAt;

    // 인증 사진은 필수다. 전체 URL이 아니라 S3 오브젝트 key를 담는다(읽기 URL은 조회 시 조립).
    @Column(name = "image_url", nullable = false, length = 2048)
    private String imageUrl;

    @Column(length = 255)
    private String content;

    /**
     * 신고가 임계값만큼 쌓여 전체 회원에게 가려진 시각. NULL 이면 노출 중이다.
     *
     * 소프트 삭제가 아니다. 행도 스트릭도 그대로 두고 노출만 막는다.
     * 그래서 스트릭·오늘 완료자 수는 이 값을 보지 않는다 — 신고당했다고 달성이 취소되지는 않는다.
     */
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;

    /**
     * 심사 상태. 기본은 {@link ReviewStatus#APPROVED} — 심사를 지났거나 심사가 없던 경우다.
     *
     * <p>{@code PENDING} 은 <b>승격되지 않은 보류</b>다. 사진이 아직 대기 prefix 에 있어
     * 공개 주소가 없다. 어느 조회에 이 행을 넣을지는 database-schema.md 가 표로 정해 뒀다 —
     * 기본은 제외이고, 내 인증 목록과 완료자 수만 포함한다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ReviewStatus reviewStatus;

    /**
     * 보류로 들어온 뒤의 재심사 시도 횟수. <b>최초 심사는 세지 않는다.</b>
     *
     * <p>시도 직후에 올린다 — 성공·실패와 무관하게. 실패할 때만 올리면 계속 죽는 호출이
     * 상한에 영영 닿지 않는다.
     */
    @Column(name = "review_attempts", nullable = false)
    private Integer reviewAttempts;

    /** 보류가 시작된 시각. 상한(24시간) 판정의 기준이다. 보류가 아니면 NULL. */
    @Column(name = "pending_since")
    private LocalDateTime pendingSince;

    /**
     * 작성자가 글을 내린 시각. NULL 이면 살아 있다.
     *
     * <p><b>인증을 취소하는 것이 아니다.</b> 글과 사진만 안 보이게 하고 "그날 인증했다" 는
     * 사실은 남긴다 — 지운 뒤 다시 인증할 수 있으면 주기 1회가 뚫린다. 그래서 스트릭도
     * 건드리지 않는다.
     *
     * <p>신고 숨김({@code hiddenAt}) 과는 다르다. 그쪽은 남이 가린 것이고 이쪽은 본인이 내린
     * 것이라, 본인에게 보이는지가 갈린다 — 숨김은 본인에게도 안 보이고, 삭제는 본인이 지운 것을
     * 안다.
     */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    private ChallengeVerification(
            MemberChallenge memberChallenge,
            Integer participationRound,
            LocalDate verifiedDate,
            LocalDate periodStartDate,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content,
            ReviewStatus reviewStatus,
            LocalDateTime pendingSince
    ) {
        this.memberChallenge = memberChallenge;
        this.participationRound = participationRound;
        this.verifiedDate = verifiedDate;
        // 파생값이지만 기본값을 두지 않는다. verifiedDate 로 대신 채우면 DAILY 에서는 맞고
        // WEEKLY·MONTHLY 에서만 틀리는데, 그건 "주기 1회"가 조용히 뚫리는 것이라 알아채기
        // 어렵다. 주기를 아는 호출부가 반드시 계산해 넘기게 한다.
        if (periodStartDate == null) {
            throw new IllegalArgumentException("periodStartDate 는 주기로 계산해 넘겨야 합니다.");
        }
        this.periodStartDate = periodStartDate;
        this.verifiedAt = verifiedAt;
        this.imageUrl = imageUrl;
        this.content = content;
        // 빌더가 값을 안 주면 정상 인증이다. 보류는 명시적으로 지정해야만 만들어진다 —
        // 기본이 PENDING 이면 어디선가 빠뜨렸을 때 사진이 조용히 안 보이게 된다.
        ReviewStatus status = reviewStatus != null ? reviewStatus : ReviewStatus.APPROVED;

        // 보류인데 시작 시각이 없으면 상한 조회(review_status, pending_since)에 안 잡혀
        // 무기한 보류가 된다. 사진은 대기 prefix 에 있다가 수명 주기가 가져가고, 사용자
        // 화면에는 "심사 중"만 영영 남는다. 만들 때 막지 않으면 찾기 어려운 자리다.
        if (status == ReviewStatus.PENDING && pendingSince == null) {
            throw new IllegalArgumentException("보류 인증에는 pendingSince 가 있어야 합니다.");
        }

        this.reviewStatus = status;
        this.reviewAttempts = 0;
        // 보류가 아니면 시작 시각을 남기지 않는다. 남겨 두면 "지난번에 보류였던 흔적"과
        // "지금 보류 중"이 같은 컬럼에 섞여, 상한 조회가 무엇을 뜻하는지 흐려진다.
        this.pendingSince = status == ReviewStatus.PENDING ? pendingSince : null;
    }

    /**
     * 당일 재인증. 행을 지우고 새로 만들지 않고 사진·코멘트·인증 시각을 덮어쓴다.
     *
     * 하루에 한 행이라는 사실이 변하지 않으므로 유니크 제약과 충돌하지 않고,
     * 이 인증을 참조하는 신고 데이터의 외래 키도 깨지지 않는다.
     * 인증 기준일(verifiedDate)과 회차는 바뀌지 않으므로 건드리지 않는다.
     */
    public void reverify(
            String imageUrl,
            String content,
            LocalDateTime verifiedAt,
            ReviewStatus reviewStatus,
            LocalDateTime pendingSince
    ) {
        if (reviewStatus == ReviewStatus.PENDING && pendingSince == null) {
            throw new IllegalArgumentException("보류 인증에는 pendingSince 가 있어야 합니다.");
        }
        this.imageUrl = imageUrl;
        this.content = content;
        this.verifiedAt = verifiedAt;

        // 내렸던 글에 다시 올리면 되살아난다. 그날의 인증은 한 건이라는 성질이 유지되고,
        // 이 경로가 INSERT 가 아니라 UPDATE 라서 유니크 제약과도 부딪히지 않는다.
        this.deletedAt = null;

        // 심사 결과도 함께 갈아끼운다. 사진이 바뀌었으니 지난 심사 결과는 이 사진의 것이 아니다.
        // 보류였다가 통과로 올라오거나 그 반대도 되며, 어느 쪽이든 시도 횟수는 0 부터 다시 센다.
        this.reviewStatus = reviewStatus;
        this.reviewAttempts = 0;
        this.pendingSince = reviewStatus == ReviewStatus.PENDING ? pendingSince : null;
    }

    /**
     * 보류가 풀려 공개된다. 재심사가 통과했거나, 상한에 닿아 통과시킨 경우다.
     *
     * <p>사진이 대기 prefix 에서 공개 prefix 로 옮겨졌으므로 <b>key 도 함께 바뀐다.</b>
     * 시도 횟수는 남긴다 — "몇 번 만에 풀렸는가"를 나중에 되짚을 수 있어야 한다.
     */
    public void approveWith(String publicKey) {
        this.imageUrl = publicKey;
        this.reviewStatus = ReviewStatus.APPROVED;
        this.pendingSince = null;
    }

    /**
     * 재심사를 한 번 시도했다. <b>성공·실패와 무관하게 올린다.</b>
     *
     * <p>실패할 때만 올리면 계속 죽는 호출이 상한에 영영 닿지 않는다.
     */
    public void recordReviewAttempt() {
        this.reviewAttempts = this.reviewAttempts + 1;
    }

    public boolean isPending() {
        return reviewStatus == ReviewStatus.PENDING;
    }

    /**
     * 작성자가 글을 내린다. <b>이미 내려간 글이면 시각을 덮어쓰지 않는다.</b>
     *
     * <p>삭제는 멱등한 편이 클라이언트가 다루기 쉽고, 처음 내린 시각이 남아야 나중에 되짚을 수
     * 있다. 신고 숨김이 시각을 덮어쓰지 않는 것과 같은 이유다.
     *
     * @return 이번 호출로 실제 내려갔으면 true. 이미 내려가 있었으면 false
     */
    public boolean softDelete(LocalDateTime deletedAt) {
        if (this.deletedAt != null) {
            return false;
        }
        this.deletedAt = deletedAt;
        return true;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /**
     * 메모만 고친다. <b>사진도 인증 시각도 건드리지 않는다.</b>
     *
     * <p>{@link #reverify} 를 재사용하지 않는 이유가 그것이다. 그쪽은 "사진을 다시 올렸다"는
     * 뜻이라 {@code imageUrl} 과 {@code verifiedAt} 을 함께 바꾼다. 오타 하나 고치는데 인증
     * 시각이 밀리면 피드의 순서와 "언제 인증했나"가 달라진다.
     *
     * <p><b>날짜 제한이 없다.</b> 사진은 그날 수행했다는 증거라 당일에만 교체할 수 있지만,
     * 메모는 거기 덧붙이는 말이라 나중에 고쳐도 "그날 수행했다"가 흔들리지 않는다.
     *
     * <p>빈 문자열과 공백만 있는 값은 {@code null} 로 눕힌다. 처음 인증할 때도 선택 값이라
     * 비어 있을 수 있는데, 나중에 지운 것만 빈 문자열로 남으면 "메모 없음"이 두 가지 모양이 된다.
     */
    public void updateContent(String content) {
        this.content = (content == null || content.isBlank()) ? null : content;
    }

    /**
     * 신고 누적으로 전체 회원에게 가린다.
     *
     * 이미 가려져 있으면 시각을 덮어쓰지 않는다. 임계값을 넘긴 뒤에도 신고는 계속 들어오는데,
     * 그때마다 갱신하면 "언제 가려졌는가"가 마지막 신고 시각으로 밀린다. 처음 가려진 시각이
     * 남아야 나중에 오탐을 되짚을 수 있다.
     */
    public void hide(LocalDateTime hiddenAt) {
        if (this.hiddenAt == null) {
            this.hiddenAt = hiddenAt;
        }
    }

    public boolean isHidden() {
        return hiddenAt != null;
    }
}
