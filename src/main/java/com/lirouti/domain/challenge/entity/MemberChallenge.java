package com.lirouti.domain.challenge.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.time.LocalDateTime;

/**
 * 회원의 챌린지 참여 상태.
 *
 * 이 엔티티는 #12(조회)에서 참여자 수·오늘 완료자 수 집계를 위해 정의했다.
 * 참여·이탈·연속 참여일 같은 쓰기/비즈니스 로직은 #13에서 이 클래스에 추가한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "member_challenge",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_member_challenge",
                        columnNames = {"member_id", "challenge_id"}
                )
        },
        indexes = {
                // 목록 조회의 챌린지별 참여자 수 집계 핫패스.
                @Index(name = "idx_member_challenge_challenge_active", columnList = "challenge_id, active"),
                // 내 참여 중 챌린지 목록 조회 핫패스.
                @Index(name = "idx_member_challenge_member_active", columnList = "member_id, active")
        }
)
public class MemberChallenge extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "challenge_id", nullable = false)
    private Challenge challenge;

    // 재참여할 때마다 1 증가. 인증·연속 참여일·오늘 완료 여부는 이 회차 기준으로만 판단한다.
    @Column(name = "participation_round", nullable = false)
    private Integer participationRound;

    @Column(name = "current_streak", nullable = false)
    private Integer currentStreak;

    @Column(name = "last_verified_date")
    private LocalDate lastVerifiedDate;

    @Column(name = "joined_at", nullable = false)
    private LocalDateTime joinedAt;

    // 참여 중 여부. false는 "그만둠"이며 회원 탈퇴와는 무관하다.
    @Column(nullable = false)
    private Boolean active;

    @Builder
    private MemberChallenge(
            Member member,
            Challenge challenge,
            Integer participationRound,
            Integer currentStreak,
            LocalDate lastVerifiedDate,
            LocalDateTime joinedAt,
            Boolean active
    ) {
        this.member = member;
        this.challenge = challenge;
        this.participationRound = (participationRound != null) ? participationRound : 1;
        this.currentStreak = (currentStreak != null) ? currentStreak : 0;
        this.lastVerifiedDate = lastVerifiedDate;
        this.joinedAt = joinedAt;
        this.active = (active != null) ? active : true;
    }

    public boolean isParticipating() {
        return Boolean.TRUE.equals(active);
    }

    /** 이탈: 참여 이력·인증은 보존하고 참여 상태만 끈다. */
    public void leave() {
        this.active = false;
    }

    /**
     * 재참여: 새 행을 만들지 않고 기존 행을 되살린다.
     * 회차를 1 올려 지난 회차의 인증과 분리하고, 스트릭·마지막 인증일을 초기화한다.
     */
    public void rejoin(LocalDateTime joinedAt) {
        this.active = true;
        this.participationRound += 1;
        this.currentStreak = 0;
        this.lastVerifiedDate = null;
        this.joinedAt = joinedAt;
    }

    /**
     * 인증 기록 시 스트릭 갱신. (실제 호출은 #14 인증에서.)
     * 마지막 인증일이 어제면 +1, 오늘이면 그대로, 그보다 오래됐거나 없으면 1로 시작한다.
     */
    public void applyVerification(LocalDate today) {
        if (lastVerifiedDate != null && lastVerifiedDate.isEqual(today)) {
            return; // 오늘 이미 인증됨 — 스트릭 유지
        }
        if (lastVerifiedDate != null && lastVerifiedDate.isEqual(today.minusDays(1))) {
            this.currentStreak += 1; // 어제 이어서 → +1
        } else {
            this.currentStreak = 1; // 처음이거나 끊긴 뒤 재시작 → 1
        }
        this.lastVerifiedDate = today;
    }

    /**
     * 조회 시점의 유효 스트릭. 저장값을 그대로 믿지 않는다.
     * 마지막 인증일이 어제보다 오래됐으면(또는 없으면) 스트릭이 끊긴 것이므로 0.
     */
    public int currentStreakAsOf(LocalDate today) {
        if (lastVerifiedDate == null || lastVerifiedDate.isBefore(today.minusDays(1))) {
            return 0;
        }
        return currentStreak;
    }

    /**
     * 유효한 인증 날짜 목록으로 스트릭을 <b>다시 센다.</b>
     *
     * <p>보류가 반려로 확정되거나 인증이 지워지면 그 한 건이 사라진다. 이때
     * {@code currentStreak} 을 1 줄이거나 {@code lastVerifiedDate} 를 전날로 되돌리면
     * <b>틀린 값이 남는다</b> — 사라진 인증 뒤에 다른 인증이 붙어 있었다면 단순 감산은 그것까지
     * 지운 셈이 된다.
     *
     * <p>그래서 남은 인증으로 처음부터 센다. {@code verifiedDates} 는 <b>그 회차의 유효한
     * 인증 날짜</b>(보류·반려 제외)이고 <b>오름차순</b>이어야 한다.
     *
     * <p>빈 목록이면 인증이 하나도 없는 상태로 되돌린다.
     */
    public void recalculateStreak(List<LocalDate> verifiedDates) {
        if (verifiedDates.isEmpty()) {
            this.currentStreak = 0;
            this.lastVerifiedDate = null;
            return;
        }

        int streak = 1;
        LocalDate previous = verifiedDates.get(0);
        for (int i = 1; i < verifiedDates.size(); i++) {
            LocalDate current = verifiedDates.get(i);
            // 같은 날이 두 번 오는 일은 유니크 제약이 막지만, 들어와도 스트릭이 부풀지 않게 둔다.
            if (current.isEqual(previous)) {
                continue;
            }
            streak = current.isEqual(previous.plusDays(1)) ? streak + 1 : 1;
            previous = current;
        }

        this.currentStreak = streak;
        this.lastVerifiedDate = previous;
    }
}
