package com.lirouti.domain.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 그룹 루틴 인증.
 *
 * <p>개인 루틴과 달리 <b>인증 행이 완료 그 자체가 아니다.</b> 완료 여부는 할당의 status 가
 * 들고 있고 인증은 그 완료에 붙는 증빙이다. 그래서 "저장되는가"만 보면 부족하고
 * <b>"status 가 실제로 바뀌는가"</b>를 같이 봐야 한다 — 그것이 화면에 나타나는 값이다.
 */
@SpringBootTest
@Transactional
@DisplayName("그룹 루틴 인증 테스트")
class GroupRoutineVerificationTest {
    private static final String KEY =
            "group-routine-verifications/2026/07/31/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";

    @Autowired
    private RoutineVerificationService verificationService;
    @Autowired
    private GroupRoutineVerificationRepository verificationRepository;

    @MockitoBean
    private MediaService mediaService;

    @PersistenceContext
    private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());
    }

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("grv" + n + "@ex.com").nickname("grv" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("grv-sid-" + n).build();
        em.persist(m);
        return m;
    }

    /** 지금 수행 가능한 시간대(하루 종일)로 오늘자 할당을 만든다. */
    private GroupRoutineAssignment assignment(Member member, GroupRoutineAssignmentStatus status) {
        int n = seq.incrementAndGet();
        Group group = Group.builder().name("그룹" + n).inviteCode("GRV" + n).build();
        em.persist(group);
        RoutineCategory category = RoutineCategory.builder().name("카테고리" + n).active(true).build();
        em.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("아침 청소").description("설명").build();
        em.persist(routine);

        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member)
                .assignedDate(LocalDate.now(TimeUtil.KST))
                .scheduledStartTime(LocalTime.MIDNIGHT)
                .scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(status)
                .build();
        em.persist(assignment);
        em.flush();
        return assignment;
    }

    /** 수행 가능 시간대를 지정해 오늘자 할당을 만든다. */
    private GroupRoutineAssignment assignmentInWindow(
            Member member, GroupRoutineAssignmentStatus status, LocalTime start, LocalTime end) {
        int n = seq.incrementAndGet();
        Group group = Group.builder().name("그룹" + n).inviteCode("GRW" + n).build();
        em.persist(group);
        RoutineCategory category = RoutineCategory.builder().name("카테고리" + n).active(true).build();
        em.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("아침 청소").description("설명").build();
        em.persist(routine);

        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member)
                .assignedDate(LocalDate.now(TimeUtil.KST))
                .scheduledStartTime(start).scheduledEndTime(end)
                .status(status).build();
        em.persist(assignment);
        em.flush();
        return assignment;
    }

    private VerificationReqDTO.Verify request() {
        return new VerificationReqDTO.Verify(KEY, "청소 완료");
    }

    private Long groupIdOf(GroupRoutineAssignment assignment) {
        return assignment.getGroupRoutine().getGroup().getId();
    }

    private Long routineIdOf(GroupRoutineAssignment assignment) {
        return assignment.getGroupRoutine().getId();
    }

    // ── 테스트 ──
    @Test
    @DisplayName("인증하면 사진이 저장되고 할당이 완료로 바뀐다 — 화면에 보이는 것은 이 상태다")
    void verify_SavesPhotoAndCompletesAssignment() {
        // given
        Member member = member();
        GroupRoutineAssignment assignment = assignment(member, GroupRoutineAssignmentStatus.IN_PROGRESS);

        // when
        VerificationResDTO.GroupRoutine result = verificationService.verifyGroupRoutine(
                member.getId(), groupIdOf(assignment), routineIdOf(assignment), request());

        // then
        assertThat(result.assignmentId()).isEqualTo(assignment.getId());
        assertThat(result.imageKey()).isEqualTo(KEY);
        assertThat(verificationRepository.findByAssignmentId(assignment.getId())).isPresent();

        em.flush();
        em.clear();
        assertThat(em.find(GroupRoutineAssignment.class, assignment.getId()).getStatus())
                .isEqualTo(GroupRoutineAssignmentStatus.COMPLETED);
    }

    @Test
    @DisplayName("이미 인증한 할당은 다시 인증할 수 없다")
    void verify_Twice_IsBlocked() {
        // given
        Member member = member();
        GroupRoutineAssignment assignment = assignment(member, GroupRoutineAssignmentStatus.IN_PROGRESS);
        verificationService.verifyGroupRoutine(
                member.getId(), groupIdOf(assignment), routineIdOf(assignment), request());
        em.flush();
        em.clear();

        // when & then
        assertThatThrownBy(() -> verificationService.verifyGroupRoutine(
                member.getId(), groupIdOf(assignment), routineIdOf(assignment), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ALREADY_VERIFIED);
    }

    @Test
    @DisplayName("남의 할당은 인증할 수 없다")
    void verify_OthersAssignment_IsNotFound() {
        // given
        GroupRoutineAssignment assignment =
                assignment(member(), GroupRoutineAssignmentStatus.IN_PROGRESS);
        Member outsider = member();

        // when & then
        assertThatThrownBy(() -> verificationService.verifyGroupRoutine(
                outsider.getId(), groupIdOf(assignment), routineIdOf(assignment), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ASSIGNMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("경로의 groupId가 그 루틴의 그룹과 다르면 막는다")
    void verify_GroupMismatch_IsNotFound() {
        // given
        Member member = member();
        GroupRoutineAssignment assignment =
                assignment(member, GroupRoutineAssignmentStatus.IN_PROGRESS);
        Long otherGroupId = groupIdOf(assignment(member(), GroupRoutineAssignmentStatus.IN_PROGRESS));

        // when & then
        assertThatThrownBy(() -> verificationService.verifyGroupRoutine(
                member.getId(), otherGroupId, routineIdOf(assignment), request()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ASSIGNMENT_NOT_FOUND);
    }
}
