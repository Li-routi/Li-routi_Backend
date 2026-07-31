package com.lirouti.domain.verification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineAssignmentRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.verification.dto.request.VerificationReqDTO;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import com.lirouti.global.util.TimeUtil;

/**
 * 그룹 루틴 인증에서 완료 처리가 실패했을 때 사진 저장이 함께 되돌아가는지.
 *
 * <p><b>{@code @Transactional}을 쓰지 않는다.</b> 테스트가 트랜잭션을 열면 서비스가 거기
 * 합류해 롤백이 관찰되지 않는다 — 커밋된 상태를 봐야 하므로 셋업을 실제로 커밋하고 뒤에서
 * 정리한다.
 *
 * <p>지키려는 불변식은 <b>"완료되지 못한 할당에 인증 행이 남지 않는다"</b>이다. 남으면
 * 다음 시도가 409와 유니크 제약에 막혀 <b>그 할당은 영영 완료될 수 없다.</b>
 */
@SpringBootTest
@DisplayName("그룹 루틴 인증 롤백 테스트")
class GroupRoutineVerificationRollbackTest {
    private static final String KEY =
            "group-routine-verifications/2026/07/31/dddddddd-dddd-4ddd-8ddd-dddddddddddd.jpg";

    @Autowired
    private RoutineVerificationService verificationService;
    @Autowired
    private GroupRoutineVerificationRepository verificationRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private RoutineCategoryRepository routineCategoryRepository;
    @Autowired
    private GroupRoutineRepository groupRoutineRepository;
    @Autowired
    private GroupRoutineAssignmentRepository assignmentRepository;

    @MockitoBean
    private MediaService mediaService;

    private Long memberId;
    private Long groupId;
    private Long categoryId;
    private Long routineId;
    private Long assignmentId;

    @BeforeEach
    void setUp() {
        doNothing().when(mediaService).validateMediaKey(any(), any());
        doNothing().when(mediaService).validateUploadedBytes(any(), any());

        Member member = memberRepository.save(Member.builder()
                .email("rollback@ex.com").nickname("rollback")
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("rollback-sid").build());
        Group group = groupRepository.save(
                Group.builder().name("롤백그룹").inviteCode("RBK0001").build());
        RoutineCategory category = routineCategoryRepository.save(
                RoutineCategory.builder().name("롤백카테고리").active(true).build());
        GroupRoutine routine = groupRoutineRepository.save(GroupRoutine.builder()
                .group(group).category(category).title("아침 청소").description("설명").build());

        // 수행 시간이 이미 지난 할당. 완료 처리가 반드시 실패한다.
        GroupRoutineAssignment assignment = assignmentRepository.save(GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member)
                .assignedDate(LocalDate.now(TimeUtil.KST))
                .scheduledStartTime(LocalTime.MIDNIGHT)
                .scheduledEndTime(LocalTime.of(0, 0, 1))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build());

        memberId = member.getId();
        groupId = group.getId();
        categoryId = category.getId();
        routineId = routine.getId();
        assignmentId = assignment.getId();
    }

    @AfterEach
    void tearDown() {
        verificationRepository.findByAssignmentId(assignmentId)
                .ifPresent(verificationRepository::delete);
        assignmentRepository.deleteById(assignmentId);
        groupRoutineRepository.deleteById(routineId);
        routineCategoryRepository.deleteById(categoryId);
        groupRepository.deleteById(groupId);
        memberRepository.deleteById(memberId);
    }

    @Test
    @DisplayName("완료 처리가 실패하면 인증 행도 남지 않는다 — 남으면 그 할당이 영영 완료되지 못한다")
    void verify_CompletionFails_RollsBackVerification() {
        // when: 수행 시간 밖이라 그룹 도메인이 막는다
        assertThatThrownBy(() -> verificationService.verifyGroupRoutine(
                memberId, groupId, routineId, new VerificationReqDTO.Verify(KEY, "청소 완료")))
                .isInstanceOf(GroupException.class);

        // then
        assertThat(verificationRepository.findByAssignmentId(assignmentId)).isEmpty();
    }
}
