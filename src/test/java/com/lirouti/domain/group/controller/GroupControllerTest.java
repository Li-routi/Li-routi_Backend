package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupMemberStatus;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("OWNER가 구성원 강퇴 요청을 보내면 대상 멤버십을 KICKED로 변경한다")
    void kickMember_Owner_UpdatesTargetMembershipStatus() throws Exception {
        // given
        Group group = group("GM00002");
        Member owner = member();
        Member member = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupMember targetMembership = membership(member, group, GroupMemberRole.MEMBER);
        em.flush();
        Long membershipId = targetMembership.getId();

        // when & then
        mockMvc.perform(delete(
                                "/api/groups/{groupId}/members/{targetMemberId}",
                                group.getId(),
                                member.getId()
                        )
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("GROUP200_9"));

        em.flush();
        em.clear();
        assertThat(em.find(GroupMember.class, membershipId).getStatus())
                .isEqualTo(GroupMemberStatus.KICKED);
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
    @DisplayName("ACTIVE OWNER가 그룹을 삭제하면 200과 공통 성공 응답을 반환한다")
    void deleteGroup_Owner_ReturnsOk() throws Exception {
        // given
        Group group = group("GD00001");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        // when & then
        mockMvc.perform(delete("/api/groups/{groupId}", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_5"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    @DisplayName("일반 MEMBER는 그룹을 삭제할 수 없다")
    void deleteGroup_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        Group group = group("GDM001");
        Member owner = member();
        Member regularMember = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(regularMember, group, GroupMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(delete("/api/groups/{groupId}", group.getId())
                        .with(user(principal(regularMember))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("비구성원은 그룹을 삭제할 수 없다")
    void deleteGroup_NonMember_ReturnsMemberAccessDenied() throws Exception {
        Group group = group("GDN001");
        Member owner = member();
        Member outsider = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/groups/{groupId}", group.getId())
                        .with(user(principal(outsider))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_2"));
    }

    @Test
    @DisplayName("다른 그룹의 OWNER라도 대상 그룹을 삭제할 수 없다")
    void deleteGroup_OwnerOfOtherGroup_ReturnsMemberAccessDenied() throws Exception {
        Group targetGroup = group("GDO001");
        Group otherGroup = group("GDO002");
        Member otherOwner = member();
        membership(otherOwner, otherGroup, GroupMemberRole.OWNER);
        em.flush();

        mockMvc.perform(delete("/api/groups/{groupId}", targetGroup.getId())
                        .with(user(principal(otherOwner))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_2"));
    }

    @Test
    @DisplayName("LEFT 또는 KICKED 구성원은 그룹을 삭제할 수 없다")
    void deleteGroup_InactiveMembers_ReturnsMemberAccessDenied() throws Exception {
        Group group = group("GDI001");
        Member owner = member();
        Member leftMember = member();
        Member kickedMember = member();
        membership(owner, group, GroupMemberRole.OWNER);
        GroupMember leftMembership = membership(leftMember, group, GroupMemberRole.MEMBER);
        GroupMember kickedMembership = membership(kickedMember, group, GroupMemberRole.MEMBER);
        leftMembership.leave();
        kickedMembership.kick();
        em.flush();

        for (Member requester : new Member[]{leftMember, kickedMember}) {
            mockMvc.perform(delete("/api/groups/{groupId}", group.getId())
                            .with(user(principal(requester))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("GROUP403_2"));
        }
    }

    @Test
    @DisplayName("DELETED 그룹은 삭제 API에서 GROUP404_1로 응답한다")
    void deleteGroup_DeletedGroup_ReturnsNotFound() throws Exception {
        Group group = group("GDD001");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        group.delete();
        em.flush();

        mockMvc.perform(delete("/api/groups/{groupId}", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    @Test
    @DisplayName("인증 없이 그룹 삭제를 요청하면 기존 보안 동작대로 401을 반환한다")
    void deleteGroup_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/groups/{groupId}", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("ACTIVE MEMBER가 그룹을 나가면 200과 그룹 탈퇴 성공 응답을 반환한다")
    void leaveGroup_ActiveMember_ReturnsOk() throws Exception {
        // given
        Group group = group("GL00001");
        Member member = member();
        GroupMember membership = membership(member, group, GroupMemberRole.MEMBER);
        em.flush();

        // when & then
        mockMvc.perform(delete("/api/groups/{groupId}/leave", group.getId())
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_6"))
                .andExpect(jsonPath("$.result").doesNotExist());

        em.flush();
        em.clear();
        GroupMember persisted = em.find(GroupMember.class, membership.getId());
        assertThat(persisted.getStatus()).isEqualTo(com.lirouti.domain.group.enums.GroupMemberStatus.LEFT);
        assertThat(persisted.getLeftAt()).isNotNull();
    }

    @Test
    @DisplayName("인증 없이 그룹 방 나가기를 요청하면 401을 반환한다")
    void leaveGroup_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/groups/{groupId}/leave", 1L))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 방 나가기 경로와 대표 응답 코드가 노출된다")
    void openApi_GroupLeave_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/{groupId}/leave")))
                .andExpect(content().string(containsString("그룹 방 나가기")))
                .andExpect(content().string(containsString("GROUP409_1")))
                .andExpect(content().string(containsString("200")));
    }

    @Test
    @DisplayName("ACTIVE OWNER는 그룹을 잠그고 같은 요청을 반복해도 영구 초대코드를 유지한다")
    void lockGroup_Owner_ReturnsLockedStateAndPreservesInviteCode() throws Exception {
        Group group = group("GL00001");
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        mockMvc.perform(patch("/api/groups/{groupId}/lock", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_7"))
                .andExpect(jsonPath("$.result.groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.isLocked").value(true));

        mockMvc.perform(patch("/api/groups/{groupId}/lock", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isLocked").value(true));

        em.flush();
        em.clear();
        Group persisted = em.find(Group.class, group.getId());
        assertThat(persisted.isLocked()).isTrue();
        assertThat(persisted.getInviteCode()).isEqualTo("GL00001");
    }

    @Test
    @DisplayName("ACTIVE OWNER는 그룹 잠금을 해제하고 같은 요청을 반복해도 영구 초대코드를 유지한다")
    void unlockGroup_Owner_ReturnsUnlockedStateAndPreservesInviteCode() throws Exception {
        Group group = group("GU00001");
        group.lock();
        Member owner = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        mockMvc.perform(patch("/api/groups/{groupId}/unlock", group.getId())
                        .with(user(principal(owner))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("GROUP200_8"))
                .andExpect(jsonPath("$.result.groupId").value(group.getId()))
                .andExpect(jsonPath("$.result.isLocked").value(false));

        em.flush();
        em.clear();
        Group persisted = em.find(Group.class, group.getId());
        assertThat(persisted.isLocked()).isFalse();
        assertThat(persisted.getInviteCode()).isEqualTo("GU00001");
    }

    @Test
    @DisplayName("일반 MEMBER는 그룹을 잠글 수 없다")
    void lockGroup_RegularMember_ReturnsOwnerAccessDenied() throws Exception {
        Group group = group("GLM0001");
        Member owner = member();
        Member regularMember = member();
        membership(owner, group, GroupMemberRole.OWNER);
        membership(regularMember, group, GroupMemberRole.MEMBER);
        em.flush();

        mockMvc.perform(patch("/api/groups/{groupId}/lock", group.getId())
                        .with(user(principal(regularMember))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_3"));
    }

    @Test
    @DisplayName("비구성원은 그룹 잠금을 해제할 수 없다")
    void unlockGroup_NonMember_ReturnsMemberAccessDenied() throws Exception {
        Group group = group("GLN0001");
        Member owner = member();
        Member outsider = member();
        membership(owner, group, GroupMemberRole.OWNER);
        em.flush();

        mockMvc.perform(patch("/api/groups/{groupId}/unlock", group.getId())
                        .with(user(principal(outsider))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP403_2"));
    }

    @Test
    @DisplayName("존재하지 않는 그룹의 방 잠금 요청은 GROUP404_1을 반환한다")
    void lockGroup_NotFound_ReturnsGroupNotFound() throws Exception {
        Member owner = member();
        em.flush();

        mockMvc.perform(patch("/api/groups/{groupId}/lock", Long.MAX_VALUE)
                        .with(user(principal(owner))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP404_1"));
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 방 잠금과 잠금 해제 경로가 노출된다")
    void openApi_GroupLock_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/{groupId}/lock")))
                .andExpect(content().string(containsString("/api/groups/{groupId}/unlock")))
                .andExpect(content().string(containsString("그룹 방 잠금")));
    }

    @Test
    @DisplayName("OpenAPI 문서에 그룹 Hard Delete 경로와 응답 코드가 노출된다")
    void openApi_GroupDelete_IsDocumented() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/groups/{groupId}")))
                .andExpect(content().string(containsString("그룹 삭제")))
                .andExpect(content().string(containsString("GROUP403_2")))
                .andExpect(content().string(containsString("GROUP403_3")))
                .andExpect(content().string(containsString("GROUP404_1")));
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
    @DisplayName("OpenAPI 문서에서 그룹 루틴 생성 DTO를 개인 루틴 DTO와 분리한다")
    void openApi_GroupRoutineCreation_UsesDedicatedSchemas() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['paths']['/api/groups/{groupId}/routines']"
                        + "['post']['requestBody']['content']['application/json']"
                        + "['schema']['$ref']")
                        .value("#/components/schemas/GroupRoutineCreateRequest"))
                .andExpect(jsonPath("$['components']['schemas']"
                        + "['ApiResponseGroupRoutineCreateResult']['properties']"
                        + "['result']['$ref']")
                        .value("#/components/schemas/GroupRoutineCreateResult"))
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.categoryId").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.title").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.description").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.schedules").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.templateId").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.name").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.repeatDays").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateRequest"
                        + ".properties.alarmTime").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateResult"
                        + ".properties.routineId").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateResult"
                        + ".properties.groupId").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateResult"
                        + ".properties.assignmentCount").exists())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateResult"
                        + ".properties.routines").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.GroupRoutineCreateResult"
                        + ".properties.activeRoutineCount").doesNotExist())
                .andExpect(jsonPath("$.components.schemas.PersonalRoutineCreateRequest.properties"
                        + ".routines.items['$ref']")
                        .value("#/components/schemas/PersonalRoutineCreateItem"))
                .andExpect(jsonPath("$.components.schemas.PersonalRoutineCreateItem.properties.templateId")
                        .exists())
                .andExpect(jsonPath("$.components.schemas.PersonalRoutineCreateResult"
                        + ".properties.activeRoutineCount").exists());
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
    @DisplayName("OWNER는 그룹에 영구 귀속된 초대코드를 조회할 수 있다")
    void getInviteCode_Owner_ReturnsPermanentCode() throws Exception {
        // given
        Group group = group("IC00001");
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
                .andExpect(jsonPath("$.result.inviteCode").value("IC00001"))
                .andExpect(jsonPath("$.result.expiresAt").doesNotExist());
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
