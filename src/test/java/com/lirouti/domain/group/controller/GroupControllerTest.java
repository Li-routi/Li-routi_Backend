package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("GroupController 그룹 루틴 통합 테스트")
class GroupControllerTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("인증 회원의 오늘 그룹 루틴을 공통 응답 형식으로 반환한다")
    void getTodayRoutines_AuthenticatedMember_ReturnsAssignments() throws Exception {
        // given
        Group group = group("GQ00001");
        Member member = member();
        membership(member, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category(true);
        GroupRoutine routine = routine(group, category, "오늘 정리");
        GroupRoutineAssignment assignment = assignment(
                routine,
                member,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.IN_PROGRESS
        );
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/groups/routines/today")
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_1"))
                .andExpect(jsonPath("$.result.routines.length()").value(1))
                .andExpect(jsonPath("$.result.routines[0].assignmentId").value(assignment.getId()))
                .andExpect(jsonPath("$.result.routines[0].routineId").value(routine.getId()))
                .andExpect(jsonPath("$.result.routines[0].groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.routines[0].groupName").value(group.getName()))
                .andExpect(jsonPath("$.result.routines[0].categoryId").value(category.getId()))
                .andExpect(jsonPath("$.result.routines[0].categoryName").value(category.getName()))
                .andExpect(jsonPath("$.result.routines[0].title").value("오늘 정리"))
                .andExpect(jsonPath("$.result.routines[0].assignedDate").value(today().toString()))
                .andExpect(jsonPath("$.result.routines[0].scheduledStartTime").value("09:00"))
                .andExpect(jsonPath("$.result.routines[0].scheduledEndTime").value("10:00"))
                .andExpect(jsonPath("$.result.routines[0].status").value("IN_PROGRESS"));
    }

    @Test
    @DisplayName("오늘 할당이 없으면 빈 목록을 반환한다")
    void getTodayRoutines_NoAssignments_ReturnsEmptyList() throws Exception {
        // given
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(get("/api/groups/routines/today")
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_1"))
                .andExpect(jsonPath("$.result.routines").isArray())
                .andExpect(jsonPath("$.result.routines").isEmpty());
    }

    @Test
    @DisplayName("그룹에서 탈퇴한 회원의 기존 오늘 할당은 반환하지 않는다")
    void getTodayRoutines_LeftGroup_ExcludesExistingAssignment() throws Exception {
        // given
        Group group = group("GQ00002");
        Member member = member();
        GroupMember membership = membership(member, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category(true);
        GroupRoutine routine = routine(group, category, "탈퇴 전 루틴");
        assignment(
                routine,
                member,
                LocalTime.of(11, 0),
                LocalTime.of(12, 0),
                GroupRoutineAssignmentStatus.PENDING
        );
        membership.leave();
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/groups/routines/today")
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.routines").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 회원 인증 정보로 조회하면 회원 도메인 오류를 반환한다")
    void getTodayRoutines_MemberNotFound_ReturnsMemberError() throws Exception {
        CustomUserDetails unknownMember = new CustomUserDetails(Long.MAX_VALUE, Role.ROLE_USER);

        mockMvc.perform(get("/api/groups/routines/today")
                        .with(user(unknownMember)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("MEMBER404_1"));
    }

    @Test
    @DisplayName("인증 없이 오늘 그룹 루틴을 조회하면 거부된다")
    void getTodayRoutines_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/routines/today"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OpenAPI 문서에 오늘 그룹 루틴 조회 경로와 200 응답이 노출된다")
    void openApi_TodayGroupRoutineQuery_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/routines/today")))
                .andExpect(content().string(containsString("오늘의 그룹 루틴 조회")))
                .andExpect(content().string(containsString("200")));
    }

    @Test
    @DisplayName("OWNER가 생성하면 201과 저장 결과 및 전체 구성원 할당 수를 반환한다")
    void createRoutine_Owner_ReturnsCreatedResult() throws Exception {
        // given
        Group group = group("GC00001");
        Member owner = member();
        Member member = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(member, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "공동 정리")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP201_1"))
                .andExpect(jsonPath("$.result.groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.categoryId").value(category.getId()))
                .andExpect(jsonPath("$.result.categoryName").value(category.getName()))
                .andExpect(jsonPath("$.result.title").value("공동 정리"))
                .andExpect(jsonPath("$.result.assignmentCount").value(2))
                .andExpect(jsonPath("$.result.schedules[0].repeatDay").value(today().getDayOfWeek().name()))
                .andExpect(jsonPath("$.result.schedules[0].startTime").value("09:00"));

        em.flush();
        em.clear();
        Long routineCount = em.createQuery(
                        "select count(gr) from GroupRoutine gr where gr.group.id = :groupId",
                        Long.class
                ).setParameter("groupId", group.getId())
                .getSingleResult();
        Long assignmentCount = em.createQuery(
                        "select count(a) from GroupRoutineAssignment a "
                                + "where a.groupRoutine.group.id = :groupId",
                        Long.class
                ).setParameter("groupId", group.getId())
                .getSingleResult();
        org.assertj.core.api.Assertions.assertThat(routineCount).isEqualTo(1L);
        org.assertj.core.api.Assertions.assertThat(assignmentCount).isEqualTo(2L);
        List<GroupRoutineAssignment> assignments = em.createQuery(
                        "select a from GroupRoutineAssignment a "
                                + "where a.groupRoutine.group.id = :groupId",
                        GroupRoutineAssignment.class
                ).setParameter("groupId", group.getId())
                .getResultList();
        org.assertj.core.api.Assertions.assertThat(assignments).allSatisfy(assignment -> {
            org.assertj.core.api.Assertions.assertThat(assignment.getAssignedDate()).isEqualTo(today());
            org.assertj.core.api.Assertions.assertThat(assignment.getScheduledStartTime())
                    .isEqualTo(LocalTime.of(9, 0));
            org.assertj.core.api.Assertions.assertThat(assignment.getScheduledEndTime())
                    .isEqualTo(LocalTime.of(10, 0));
        });
    }

    @Test
    @DisplayName("OWNER 한 명만 있는 그룹도 할당 한 건으로 생성한다")
    void createRoutine_OwnerOnly_ReturnsOneAssignment() throws Exception {
        // given
        Group group = group("GC00002");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupRoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "단독 루틴")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.assignmentCount").value(1));
    }

    @Test
    @DisplayName("일반 구성원은 그룹 루틴을 생성할 수 없다")
    void createRoutine_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        // given
        Group group = group("GC00003");
        Member regularMember = member();
        membership(regularMember, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category(true);
        em.flush();

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(regularMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest(category.getId(), "권한 없는 루틴")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("동일 그룹의 같은 제목은 409를 반환한다")
    void createRoutine_DuplicateTitle_ReturnsConflict() throws Exception {
        // given
        Group group = group("GC00004");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupRoutineCategory category = category(true);
        em.flush();
        String request = validRequest(category.getId(), "중복 루틴");
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isCreated());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUP409_3"));
    }

    @Test
    @DisplayName("같은 요일이 중복되거나 시간 범위가 잘못되면 400을 반환한다")
    void createRoutine_InvalidSchedules_ReturnsBadRequest() throws Exception {
        // given
        Group group = group("GC00005");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupRoutineCategory category = category(true);
        em.flush();
        String request = """
                {
                  "categoryId": %d,
                  "title": "잘못된 일정",
                  "description": "요일과 시간 검증",
                  "schedules": [
                    {"repeatDay": "MONDAY", "startTime": "10:00", "endTime": "09:00"},
                    {"repeatDay": "MONDAY", "startTime": "18:00", "endTime": "19:00"}
                  ]
                }
                """.formatted(category.getId());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("반복 일정에 null 요소가 포함되면 400을 반환한다")
    void createRoutine_NullSchedule_ReturnsBadRequest() throws Exception {
        // given
        Group group = group("GC00006");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupRoutineCategory category = category(true);
        em.flush();
        String request = """
                {
                  "categoryId": %d,
                  "title": "null 일정",
                  "description": "null 일정 요소 검증",
                  "schedules": [null]
                }
                """.formatted(category.getId());

        // when & then
        mockMvc.perform(post("/api/groups/{groupId}/routines", group.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("인증 없이 그룹 루틴 생성 요청을 하면 거부된다")
    void createRoutine_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups/{groupId}/routines", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 루틴 생성 경로와 201 응답이 노출된다")
    void openApi_GroupRoutineCreation_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/{groupId}/routines")))
                .andExpect(content().string(containsString("그룹 루틴 생성")))
                .andExpect(content().string(containsString("201")));
    }

    @Test
    @DisplayName("OWNER가 루틴을 수정하면 미확정 할당만 새 시간으로 동기화하고 확정 이력을 보존한다")
    void updateRoutine_Owner_UpdatesRoutineAndReconcilesAssignments() throws Exception {
        // given
        Group group = group("GU00001");
        Member owner = member();
        Member member = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(member, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory oldCategory = category(true);
        GroupRoutineCategory changedCategory = category(true);
        GroupRoutine routine = routine(group, oldCategory, "기존 공동 루틴");
        routine.addSchedule(
                today().getDayOfWeek(),
                LocalTime.of(9, 0),
                LocalTime.of(10, 0)
        );
        GroupRoutineAssignment completed = assignment(
                routine,
                owner,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.COMPLETED
        );
        GroupRoutineAssignment pending = assignment(
                routine,
                member,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                GroupRoutineAssignmentStatus.PENDING
        );
        em.flush();
        Long completedId = completed.getId();
        Long pendingId = pending.getId();
        em.clear();

        // when & then
        mockMvc.perform(put("/api/groups/{groupId}/routines/{routineId}",
                        group.getId(), routine.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(
                                changedCategory.getId(),
                                "수정 공동 루틴",
                                today().getDayOfWeek(),
                                LocalTime.of(18, 0),
                                LocalTime.of(19, 0)
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_3"))
                .andExpect(jsonPath("$.result.routineId").value(routine.getId()))
                .andExpect(jsonPath("$.result.groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.categoryId").value(changedCategory.getId()))
                .andExpect(jsonPath("$.result.title").value("수정 공동 루틴"))
                .andExpect(jsonPath("$.result.assignmentCount").value(2))
                .andExpect(jsonPath("$.result.schedules[0].startTime").value("18:00"))
                .andExpect(jsonPath("$.result.schedules[0].endTime").value("19:00"));

        em.flush();
        em.clear();
        GroupRoutine updated = em.find(GroupRoutine.class, routine.getId());
        GroupRoutineAssignment preserved = em.find(GroupRoutineAssignment.class, completedId);
        GroupRoutineAssignment rescheduled = em.find(GroupRoutineAssignment.class, pendingId);
        org.assertj.core.api.Assertions.assertThat(updated.getCategory().getId())
                .isEqualTo(changedCategory.getId());
        org.assertj.core.api.Assertions.assertThat(updated.getTitle()).isEqualTo("수정 공동 루틴");
        org.assertj.core.api.Assertions.assertThat(updated.getSchedules()).singleElement()
                .satisfies(schedule -> {
                    org.assertj.core.api.Assertions.assertThat(schedule.getStartTime())
                            .isEqualTo(LocalTime.of(18, 0));
                    org.assertj.core.api.Assertions.assertThat(schedule.getEndTime())
                            .isEqualTo(LocalTime.of(19, 0));
                });
        org.assertj.core.api.Assertions.assertThat(preserved.getStatus())
                .isEqualTo(GroupRoutineAssignmentStatus.COMPLETED);
        org.assertj.core.api.Assertions.assertThat(preserved.getScheduledStartTime())
                .isEqualTo(LocalTime.of(9, 0));
        org.assertj.core.api.Assertions.assertThat(rescheduled.getScheduledStartTime())
                .isEqualTo(LocalTime.of(18, 0));
        org.assertj.core.api.Assertions.assertThat(rescheduled.getScheduledEndTime())
                .isEqualTo(LocalTime.of(19, 0));
    }

    @Test
    @DisplayName("일반 구성원은 그룹 루틴을 수정할 수 없다")
    void updateRoutine_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        // given
        Group group = group("GU00002");
        Member regularMember = member();
        membership(regularMember, group, GroupMemberRole.MEMBER);
        GroupRoutineCategory category = category(true);
        GroupRoutine routine = routine(group, category, "수정 권한 루틴");
        em.flush();

        // when & then
        mockMvc.perform(put("/api/groups/{groupId}/routines/{routineId}",
                        group.getId(), routine.getId())
                        .with(user(principal(regularMember)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(
                                category.getId(),
                                "권한 없는 수정",
                                today().getDayOfWeek(),
                                LocalTime.of(9, 0),
                                LocalTime.of(10, 0)
                        )))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("요청 그룹에 속하지 않은 루틴은 찾을 수 없는 루틴으로 처리한다")
    void updateRoutine_RoutineInAnotherGroup_ReturnsNotFound() throws Exception {
        // given
        Group targetGroup = group("GU00003");
        Group otherGroup = group("GU00004");
        Member owner = member();
        membership(owner, targetGroup, GroupMemberRole.OWNER);
        GroupRoutineCategory category = category(true);
        GroupRoutine otherRoutine = routine(otherGroup, category, "다른 그룹 루틴");
        em.flush();

        // when & then
        mockMvc.perform(put("/api/groups/{groupId}/routines/{routineId}",
                        targetGroup.getId(), otherRoutine.getId())
                        .with(user(principal(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest(
                                category.getId(),
                                "잘못된 소속 수정",
                                today().getDayOfWeek(),
                                LocalTime.of(9, 0),
                                LocalTime.of(10, 0)
                        )))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP404_4"));
    }

    @Test
    @DisplayName("그룹 루틴 수정 일정의 시간 범위가 잘못되면 400을 반환한다")
    void updateRoutine_InvalidSchedule_ReturnsBadRequest() throws Exception {
        // given
        String request = updateRequest(
                1L,
                "잘못된 수정",
                DayOfWeek.MONDAY,
                LocalTime.of(10, 0),
                LocalTime.of(9, 0)
        );

        // when & then
        mockMvc.perform(put("/api/groups/{groupId}/routines/{routineId}", 1L, 1L)
                        .with(user(new CustomUserDetails(1L, Role.ROLE_USER)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400_1"));
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 루틴 수정 경로와 200 응답이 노출된다")
    void openApi_GroupRoutineUpdate_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(
                        "/api/groups/{groupId}/routines/{routineId}"
                )))
                .andExpect(content().string(containsString("그룹 루틴 수정")))
                .andExpect(content().string(containsString("200")));
    }

    @Test
    @DisplayName("OWNER가 초대코드를 발급하면 201과 코드 및 말소 시각을 반환한다")
    void issueInviteCode_Owner_ReturnsCreatedResult() throws Exception {
        // given
        Group group = group("IC00001");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        commitFixtureForRequiresNew();
        try {
            // when & then
            mockMvc.perform(post("/api/groups/{groupId}/invite-code", group.getId())
                            .with(user(principal(owner))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.code").value("GROUP201_2"))
                    .andExpect(jsonPath("$.result.inviteCode", matchesPattern("[A-Z0-9]{7}")))
                    .andExpect(jsonPath("$.result.expiresAt").isNotEmpty());
        } finally {
            cleanupInviteFixture(group.getId(), owner.getId());
        }
    }

    @Test
    @DisplayName("초대코드를 재발급한 후 조회하면 새 코드를 반환한다")
    void reissueInviteCode_ThenGet_ReturnsNewCode() throws Exception {
        // given
        Group group = group("IC00004");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        commitFixtureForRequiresNew();
        try {
            mockMvc.perform(post("/api/groups/{groupId}/invite-code", group.getId())
                            .with(user(principal(owner))))
                    .andExpect(status().isCreated());
            restartTestTransaction();
            String firstIssuedCode = findInviteCode(group.getId());

            // when
            mockMvc.perform(post("/api/groups/{groupId}/invite-code", group.getId())
                            .with(user(principal(owner))))
                    .andExpect(status().isCreated());
            restartTestTransaction();
            String reissuedCode = findInviteCode(group.getId());

            // then
            org.assertj.core.api.Assertions.assertThat(reissuedCode)
                    .isNotEqualTo(firstIssuedCode);
            em.clear();
            mockMvc.perform(get("/api/groups/{groupId}/invite-code", group.getId())
                            .with(user(principal(owner))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("GROUP200_2"))
                    .andExpect(jsonPath("$.result.inviteCode").value(reissuedCode))
                    .andExpect(jsonPath("$.result.expiresAt").isNotEmpty());
        } finally {
            cleanupInviteFixture(group.getId(), owner.getId());
        }
    }

    @Test
    @DisplayName("만료된 초대코드를 조회해도 자동 재발급하지 않는다")
    void getInviteCode_ExpiredCode_ReturnsStoredCode() throws Exception {
        // given
        Group group = group("IC00002");
        group.issueInviteCode("EXP1234", LocalDateTime.of(2020, 1, 1, 0, 0));
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/groups/{groupId}/invite-code", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_2"))
                .andExpect(jsonPath("$.result.inviteCode").value("EXP1234"))
                .andExpect(jsonPath("$.result.expiresAt").value("2020-01-01T00:00:00"));
    }

    @Test
    @DisplayName("일반 구성원은 초대코드를 발급할 수 없다")
    void issueInviteCode_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        // given
        Group group = group("IC00003");
        Member regularMember = member();
        membership(regularMember, group, GroupMemberRole.MEMBER);
        em.flush();

        commitFixtureForRequiresNew();
        try {
            // when & then
            mockMvc.perform(post("/api/groups/{groupId}/invite-code", group.getId())
                            .with(user(principal(regularMember))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GROUP403_3"));
        } finally {
            cleanupInviteFixture(group.getId(), regularMember.getId());
        }
    }

    private String validRequest(Long categoryId, String title) {
        return """
                {
                  "categoryId": %d,
                  "title": "%s",
                  "description": "그룹 루틴 설명입니다.",
                  "schedules": [
                    {"repeatDay": "%s", "startTime": "09:00", "endTime": "10:00"}
                  ]
                }
                """.formatted(categoryId, title, today().getDayOfWeek().name());
    }

    private String updateRequest(
            Long categoryId,
            String title,
            DayOfWeek repeatDay,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return """
                {
                  "categoryId": %d,
                  "title": "%s",
                  "description": "수정된 그룹 루틴 설명입니다.",
                  "schedules": [
                    {"repeatDay": "%s", "startTime": "%s", "endTime": "%s"}
                  ]
                }
                """.formatted(categoryId, title, repeatDay.name(), startTime, endTime);
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Seoul"));
    }

    private Group group(String inviteCode) {
        Group group = Group.builder().name("컨트롤러 그룹").inviteCode(inviteCode).build();
        em.persist(group);
        return group;
    }

    private void commitFixtureForRequiresNew() {
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
    }

    private void restartTestTransaction() {
        TestTransaction.flagForRollback();
        TestTransaction.end();
        TestTransaction.start();
    }

    private String findInviteCode(Long groupId) {
        em.clear();
        return em.find(Group.class, groupId).getInviteCode();
    }

    private void cleanupInviteFixture(Long groupId, Long memberId) {
        if (TestTransaction.isActive()) {
            TestTransaction.flagForRollback();
            TestTransaction.end();
        }
        TestTransaction.start();
        em.clear();
        em.createNativeQuery("DELETE FROM group_member WHERE group_id = :groupId")
                .setParameter("groupId", groupId)
                .executeUpdate();
        em.createNativeQuery("DELETE FROM member_group WHERE id = :groupId")
                .setParameter("groupId", groupId)
                .executeUpdate();
        em.createNativeQuery("DELETE FROM member WHERE id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        TestTransaction.flagForCommit();
        TestTransaction.end();
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("group-controller-" + value + "@example.com")
                .nickname("컨트롤러회원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("group-controller-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Member member, Group group, GroupMemberRole role) {
        GroupMember membership = GroupMember.builder()
                .member(member)
                .group(group)
                .role(role)
                .build();
        em.persist(membership);
        return membership;
    }

    private GroupRoutineCategory category(boolean active) {
        int value = sequence.incrementAndGet();
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .name("컨트롤러 카테고리" + value)
                .active(active)
                .build();
        em.persist(category);
        return category;
    }

    private GroupRoutine routine(
            Group group,
            GroupRoutineCategory category,
            String title
    ) {
        GroupRoutine routine = GroupRoutine.builder()
                .group(group)
                .category(category)
                .title(title)
                .description("그룹 루틴 설명입니다.")
                .build();
        em.persist(routine);
        return routine;
    }

    private GroupRoutineAssignment assignment(
            GroupRoutine routine,
            Member member,
            LocalTime startTime,
            LocalTime endTime,
            GroupRoutineAssignmentStatus status
    ) {
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(member)
                .assignedDate(today())
                .scheduledStartTime(startTime)
                .scheduledEndTime(endTime)
                .status(status)
                .build();
        em.persist(assignment);
        return assignment;
    }

    private CustomUserDetails principal(Member member) {
        return new CustomUserDetails(member.getId(), Role.ROLE_USER);
    }
}
