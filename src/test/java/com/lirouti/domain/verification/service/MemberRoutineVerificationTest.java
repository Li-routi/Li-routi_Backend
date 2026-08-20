package com.lirouti.domain.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.MemberRoutineVerificationRepository;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 개인 루틴 인증.
 *
 * <p>개인 루틴에는 날짜별 행이 없어 <b>인증 행이 곧 완료 기록</b>이다. 그래서 "저장되는가"
 * 만큼이나 <b>"저장되면 안 될 때 막히는가"</b>가 중요하다 — 남의 루틴, 수행 요일이 아닌 날,
 * 수행 시간이 아닌 때, 이미 인증한 날이 그것이다.
 *
 * <p>사진 검증은 외부 호출이라 mock 한다. 여기서 보려는 것은 우리 쪽 판정이다.
 */
@SpringBootTest
@Transactional
@DisplayName("개인 루틴 인증 테스트")
class MemberRoutineVerificationTest {
    private static final LocalDate FIXED_DATE = LocalDate.of(2026, 8, 21);
    private static final LocalTime DEFAULT_NOW = LocalTime.of(9, 30);
    private static final String KEY =
            "member-routine-verifications/2026/07/31/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";

    @Autowired
    private RoutineVerificationService verificationService;
    @Autowired
    private MemberRoutineVerificationRepository verificationRepository;

    @MockitoBean
    private MediaService mediaService;
    @MockitoBean
    private Clock clock;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Member owner;

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
        setCurrentTime(DEFAULT_NOW);
        owner = member();
    }

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("mrv" + n + "@ex.com").nickname("mrv" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("mrv-sid-" + n).build();
        em.persist(m);
        return m;
    }

    /** 오늘 요일에 수행하는 루틴을 만든다. 다른 요일을 주면 "오늘이 아닌 날"이 된다. */
    private MemberRoutine routine(Member member, DayOfWeek day) {
        return routine(member, day, LocalTime.of(9, 0), LocalTime.of(10, 0));
    }

    /** 지정한 수행 시간으로 개인 루틴을 만든다. */
    private MemberRoutine routine(
            Member member,
            DayOfWeek day,
            LocalTime startTime,
            LocalTime endTime
    ) {
        RoutineCategory category = RoutineCategory.builder()
                .owner(member).name("카테고리" + seq.incrementAndGet()).active(true).build();
        em.persist(category);
        MemberRoutine routine = MemberRoutine.builder()
                .member(member).category(category).name("아침 스트레칭")
                .startTime(startTime).endTime(endTime).active(true).build();
        routine.addSchedule(day);
        em.persist(routine);
        em.flush();
        return routine;
    }

    private DayOfWeek today() {
        return FIXED_DATE.getDayOfWeek();
    }

    /** 서비스가 사용할 KST 현재 시각을 테스트 값으로 고정한다. */
    private void setCurrentTime(LocalTime currentTime) {
        ZonedDateTime currentDateTime = ZonedDateTime.of(FIXED_DATE, currentTime, TimeUtil.KST);
        when(clock.instant()).thenReturn(currentDateTime.toInstant());
        when(clock.getZone()).thenReturn(TimeUtil.KST);
    }

    private VerificationReqDTO.Verify request() {
        return new VerificationReqDTO.Verify(KEY, "오늘도 완료");
    }

    // ── 테스트 ──
    @Test
    @DisplayName("수행 요일에 인증하면 저장된다")
    void verify_OnScheduledDay_IsSaved() {
        // given
        MemberRoutine routine = routine(owner, today());

        // when
        VerificationResDTO.MemberRoutine result =
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request());

        // then
        assertThat(result.routineId()).isEqualTo(routine.getId());
        assertThat(result.imageKey()).isEqualTo(KEY);
        assertThat(result.content()).isEqualTo("오늘도 완료");
        assertThat(result.verifiedDate()).isEqualTo(FIXED_DATE);
        assertThat(verificationRepository
                .findByMemberRoutineIdAndVerifiedDate(routine.getId(), FIXED_DATE))
                .isPresent();
    }

    @Test
    @DisplayName("시작 시각에는 인증할 수 있다 — 시작 경계는 수행 시간에 포함한다")
    void verify_AtStartTime_IsSaved() {
        // given
        setCurrentTime(LocalTime.of(9, 0));
        MemberRoutine routine = routine(owner, today());

        // when
        VerificationResDTO.MemberRoutine result =
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request());

        // then
        assertThat(result.routineId()).isEqualTo(routine.getId());
    }

    @Test
    @DisplayName("시작 시각 전에는 인증할 수 없다")
    void verify_BeforeStartTime_IsBlocked() {
        // given
        setCurrentTime(LocalTime.of(8, 59));
        MemberRoutine routine = routine(owner, today());

        // when & then
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue(
                        "code", VerificationErrorCode.NOT_IN_ROUTINE_TIME_RANGE);
    }

    @Test
    @DisplayName("종료 시각이 속한 분에는 인증할 수 있다 — 종료 경계를 수행 시간에 포함한다")
    void verify_DuringEndMinute_IsSaved() {
        // given
        setCurrentTime(LocalTime.of(10, 0, 59));
        MemberRoutine routine = routine(owner, today());

        // when
        VerificationResDTO.MemberRoutine result =
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request());

        // then
        assertThat(result.routineId()).isEqualTo(routine.getId());
    }

    @Test
    @DisplayName("종료 시각의 다음 분부터는 인증할 수 없다")
    void verify_AfterEndMinute_IsBlocked() {
        // given
        setCurrentTime(LocalTime.of(10, 1));
        MemberRoutine routine = routine(owner, today());

        // when & then
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue(
                        "code", VerificationErrorCode.NOT_IN_ROUTINE_TIME_RANGE);
    }

    @Test
    @DisplayName("시작 시각이 없으면 시작 제한 없이 종료 시각 전까지 인증할 수 있다")
    void verify_WithoutStartTime_BeforeEndTime_IsSaved() {
        // given
        setCurrentTime(LocalTime.of(8, 0));
        MemberRoutine routine = routine(owner, today(), null, LocalTime.of(10, 0));

        // when
        VerificationResDTO.MemberRoutine result =
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request());

        // then
        assertThat(result.routineId()).isEqualTo(routine.getId());
    }

    @Test
    @DisplayName("시작 시각이 없어도 종료 시각의 다음 분부터는 인증할 수 없다")
    void verify_WithoutStartTime_AfterEndMinute_IsBlocked() {
        // given
        setCurrentTime(LocalTime.of(10, 1));
        MemberRoutine routine = routine(owner, today(), null, LocalTime.of(10, 0));

        // when & then
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue(
                        "code", VerificationErrorCode.NOT_IN_ROUTINE_TIME_RANGE);
    }

    @Test
    @DisplayName("수행 요일이 아니면 막는다 — 등록할 때 정한 날짜가 의미를 가져야 한다")
    void verify_NotScheduledToday_IsBlocked() {
        // given: 오늘이 아닌 요일만 수행하는 루틴
        MemberRoutine routine = routine(owner, today().plus(1));

        // when & then
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.NOT_SCHEDULED_TODAY);
    }

    @Test
    @DisplayName("같은 날 두 번 인증하면 막는다 — 챌린지와 달리 사진 교체를 허용하지 않는다")
    void verify_Twice_IsBlocked() {
        // given
        MemberRoutine routine = routine(owner, today());
        verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request());
        em.flush();
        em.clear();

        // when & then
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("남의 루틴은 인증할 수 없다 — 없는 루틴과 같은 404다")
    void verify_OthersRoutine_IsNotFound() {
        // given
        MemberRoutine routine = routine(member(), today());

        // when & then: 소유자 조건을 조회에 넣으므로 "남의 것"과 "없는 것"이 구분되지 않는다
        assertThatThrownBy(() ->
                verificationService.verifyMemberRoutine(owner.getId(), routine.getId(), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ROUTINE_NOT_FOUND);
    }

    @Test
    @DisplayName("오늘 인증한 루틴 id를 한 번에 가져온다 — 목록에서 루틴마다 묻지 않기 위한 경로")
    void findVerifiedRoutineIds_ReturnsOnlyVerified() {
        // given
        MemberRoutine verified = routine(owner, today());
        MemberRoutine untouched = routine(owner, today());
        verificationService.verifyMemberRoutine(owner.getId(), verified.getId(), request());
        em.flush();

        // when
        var ids = verificationRepository.findVerifiedRoutineIds(
                java.util.List.of(verified.getId(), untouched.getId()), FIXED_DATE);

        // then
        assertThat(ids).containsExactly(verified.getId());
    }
}
