package com.lirouti.domain.routine.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
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
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.service.RoutineVerificationService;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 홈 화면의 "오늘 완료" 표시.
 *
 * <p>개인 루틴은 인증해도 화면에 반영될 곳이 없었다 — 챌린지는 스트릭이, 그룹은 할당의
 * status 가 바뀌지만 개인 루틴은 인증 행만 쌓였다. 이 플래그가 그 자리를 채운다.
 *
 * <p><b>인증과 목록 조회를 실제로 이어서 본다.</b> 조회만 따로 보면 "완료 집합을 잘 반영하는가"
 * 까지만 검증되고, 인증이 그 집합에 실제로 들어가는지는 확인되지 않는다.
 */
@SpringBootTest
@Transactional
@DisplayName("개인 루틴 오늘 완료 표시 테스트")
class RoutineCompletionFlagTest {
    private static final String KEY =
            "member-routine-verifications/2026/07/31/cccccccc-cccc-4ccc-8ccc-cccccccccccc.jpg";

    @Autowired
    private RoutineQueryService routineQueryService;
    @Autowired
    private RoutineVerificationService verificationService;

    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();
    private Member owner;

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());

        int n = seq.incrementAndGet();
        owner = Member.builder()
                .email("flag" + n + "@ex.com").nickname("flag" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("flag-sid-" + n).build();
        em.persist(owner);
    }

    /** 오늘 요일에 수행하는 활성 루틴. */
    private MemberRoutine routine(String name) {
        int n = seq.incrementAndGet();
        RoutineCategory category = RoutineCategory.builder()
                .owner(owner).name("카테고리" + n).active(true).build();
        em.persist(category);
        MemberRoutine routine = MemberRoutine.builder()
                .member(owner).category(category).name(name)
                .endTime(LocalTime.of(23, 59)).active(true).build();
        routine.addSchedule(LocalDate.now(TimeUtil.KST).getDayOfWeek());
        em.persist(routine);
        em.flush();
        return routine;
    }

    @Test
    @DisplayName("인증하지 않은 루틴은 completedToday=false 다")
    void getTodayRoutines_NotVerified_IsFalse() {
        // given
        routine("스트레칭");

        // when
        List<RoutineResDTO.Routine> result = routineQueryService.getTodayRoutines(owner.getId());

        // then
        assertThat(result).singleElement()
                .extracting(RoutineResDTO.Routine::completedToday)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("인증한 루틴만 completedToday=true 로 바뀐다")
    void getTodayRoutines_AfterVerify_OnlyVerifiedIsTrue() {
        // given
        MemberRoutine verified = routine("물 마시기");
        MemberRoutine untouched = routine("스트레칭");

        // when
        verificationService.verifyMemberRoutine(owner.getId(), verified.getId(),
                new VerificationReqDTO.Verify(KEY, "완료"));
        em.flush();
        em.clear();

        // then
        List<RoutineResDTO.Routine> result = routineQueryService.getTodayRoutines(owner.getId());
        assertThat(result)
                .filteredOn(r -> r.routineId().equals(verified.getId()))
                .singleElement()
                .extracting(RoutineResDTO.Routine::completedToday)
                .isEqualTo(true);
        assertThat(result)
                .filteredOn(r -> r.routineId().equals(untouched.getId()))
                .singleElement()
                .extracting(RoutineResDTO.Routine::completedToday)
                .isEqualTo(false);
    }

    @Test
    @DisplayName("루틴이 없어도 조회가 깨지지 않는다 — 빈 IN 절로 질의하지 않는다")
    void getTodayRoutines_NoRoutines_ReturnsEmpty() {
        // when & then
        assertThat(routineQueryService.getTodayRoutines(owner.getId())).isEmpty();
    }
}
