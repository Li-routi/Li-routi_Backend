package com.lirouti.domain.verification.entity;

import com.lirouti.domain.challenge.entity.MemberChallenge;
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
 * 그 인덱스가 피드의 참여 조인과 "오늘 인증 행 찾기"를 커버한다.
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

    @Builder
    private ChallengeVerification(
            MemberChallenge memberChallenge,
            Integer participationRound,
            LocalDate verifiedDate,
            LocalDateTime verifiedAt,
            String imageUrl,
            String content
    ) {
        this.memberChallenge = memberChallenge;
        this.participationRound = participationRound;
        this.verifiedDate = verifiedDate;
        this.verifiedAt = verifiedAt;
        this.imageUrl = imageUrl;
        this.content = content;
    }

    /**
     * 당일 재인증. 행을 지우고 새로 만들지 않고 사진·코멘트·인증 시각을 덮어쓴다.
     *
     * 하루에 한 행이라는 사실이 변하지 않으므로 유니크 제약과 충돌하지 않고,
     * 이 인증을 참조하는 신고 데이터의 외래 키도 깨지지 않는다.
     * 인증 기준일(verifiedDate)과 회차는 바뀌지 않으므로 건드리지 않는다.
     */
    public void reverify(String imageUrl, String content, LocalDateTime verifiedAt) {
        this.imageUrl = imageUrl;
        this.content = content;
        this.verifiedAt = verifiedAt;
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
