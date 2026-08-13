package com.lirouti.domain.activity;

import com.lirouti.domain.activity.entity.MemberActivityDay;
import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 전역 활동일.
 *
 * <p>이 표의 계약은 둘이다 — <b>하루는 한 행</b>, 그리고 <b>{@code all_completed} 는 한
 * 방향으로만 움직인다</b>. 뒤엣것이 뚫리면 아침에 챌린지를 한 사람의 "전부 완수" 가 저녁에
 * 조용히 사라진다.
 */
@SpringBootTest
@Transactional
@DisplayName("활동일 기록 테스트")
class MemberActivityDayTest {

    @Autowired
    private MemberActivityDayRepository memberActivityDayRepository;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private final LocalDate today = LocalDate.of(2026, 8, 13);

    @BeforeEach
    void setUp() {
        int n = seq.incrementAndGet();
        me = Member.builder()
                .email("act" + n + "@ex.com").nickname("act" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("act-sid-" + n).build();
        em.persist(me);
        em.flush();
    }

    private MemberActivityDay find(LocalDate date) {
        em.clear();
        return memberActivityDayRepository.findAll().stream()
                .filter(row -> row.getMemberId().equals(me.getId())
                        && row.getActivityDate().equals(date))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("같은 날 여러 번 기록해도 한 행이다")
    void record_OneRowPerDay() {
        memberActivityDayRepository.record(me.getId(), today, false);
        memberActivityDayRepository.record(me.getId(), today, false);
        memberActivityDayRepository.record(me.getId(), today, false);
        em.clear();

        assertThat(memberActivityDayRepository.countByMemberId(me.getId())).isEqualTo(1);
    }

    /**
     * 아침에 챌린지 인증으로 행이 먼저 생기고, 저녁에 마지막 개인 루틴을 끝내는 순서다.
     * "중복이면 무시" 로 끝내면 이때 값이 0 인 채로 남는다.
     */
    @Test
    @DisplayName("행이 이미 있어도 전부 완수하면 올라간다")
    void record_RaisesAllCompletedOnExistingRow() {
        memberActivityDayRepository.record(me.getId(), today, false);
        memberActivityDayRepository.record(me.getId(), today, true);

        assertThat(find(today).isAllCompleted()).isTrue();
    }

    /**
     * 저녁에 그룹 인증을 하나 더 해도 낮에 세운 "전부 완수" 가 사라지면 안 된다. 그룹·챌린지
     * 경로는 언제나 0 을 쓰기 때문에 이 방어가 없으면 매번 지워진다.
     */
    @Test
    @DisplayName("이미 선 완수는 뒤이은 기록이 덮어 내리지 못한다")
    void record_NeverLowersAllCompleted() {
        memberActivityDayRepository.record(me.getId(), today, true);
        memberActivityDayRepository.record(me.getId(), today, false);

        assertThat(find(today).isAllCompleted()).isTrue();
    }

    @Test
    @DisplayName("날짜가 다르면 따로 쌓인다")
    void record_SeparateRowsPerDate() {
        memberActivityDayRepository.record(me.getId(), today, false);
        memberActivityDayRepository.record(me.getId(), today.minusDays(1), false);
        em.clear();

        assertThat(memberActivityDayRepository.countByMemberId(me.getId())).isEqualTo(2);
    }

    /**
     * 둥지 레벨이 쓰는 조회다. 창 길이만큼 나오면 그 기간이 연속이라는 뜻이고, 하루라도
     * 비면 창에 그만큼이 들어올 수 없어 끊김을 따로 판정하지 않아도 된다.
     */
    @Test
    @DisplayName("완수한 날만, 기준일부터 거슬러 센다")
    void countCompleted_WithinWindowOnly() {
        memberActivityDayRepository.record(me.getId(), today, true);
        memberActivityDayRepository.record(me.getId(), today.minusDays(1), true);
        // 창 밖 — 세면 안 된다
        memberActivityDayRepository.record(me.getId(), today.minusDays(9), true);
        // 창 안이지만 완수는 아니다
        memberActivityDayRepository.record(me.getId(), today.minusDays(2), false);
        em.clear();

        long counted = memberActivityDayRepository
                .countByMemberIdAndAllCompletedTrueAndActivityDateAfter(
                        me.getId(), today.minusDays(3));

        assertAll(
                () -> assertThat(counted).isEqualTo(2),
                () -> assertThat(memberActivityDayRepository.countByMemberId(me.getId()))
                        .as("활동일 자체는 완수 여부와 무관하게 센다").isEqualTo(4));
    }
}
