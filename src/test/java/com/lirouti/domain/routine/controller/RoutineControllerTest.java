package com.lirouti.domain.routine.controller;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.enums.RoutineCategoryColor;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@DisplayName("RoutineController 개인 루틴 통합 테스트")
class RoutineControllerTest {
    private final AtomicInteger sequence = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @PersistenceContext
    private EntityManager em;

    @Test
    @DisplayName("고정 카테고리와 본인의 사용자 카테고리를 함께 반환한다")
    void getCategories_MemberWithOwnCategory_ReturnsFixedAndOwned() throws Exception {
        // given
        Member member = member();
        Member other = member();
        memberCategory(member, "내 카테고리", RoutineCategoryColor.BLUE);
        memberCategory(other, "남의 카테고리", RoutineCategoryColor.RED);
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/routines/categories").with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("ROUTINE200_1"))
                .andExpect(jsonPath("$.result.addableCount").value(4))
                .andExpect(jsonPath("$.result.categories[0].name").value("운동"))
                .andExpect(jsonPath("$.result.categories[0].fixed").value(true))
                .andExpect(jsonPath("$.result.categories[?(@.name == '내 카테고리')].fixed")
                        .value(false))
                .andExpect(jsonPath("$.result.categories[?(@.name == '내 카테고리')].color")
                        .value("BLUE"))
                .andExpect(jsonPath("$.result.categories[?(@.name == '남의 카테고리')]").isEmpty());
    }

    @Test
    @DisplayName("카테고리를 지정하면 그 카테고리의 기본 제공 루틴만 반환한다")
    void getTemplates_WithCategoryId_ReturnsSeededTemplates() throws Exception {
        // given — 고정 카테고리와 기본 제공 루틴은 R__seed_routine.sql이 넣는다
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(get("/api/routines/templates")
                        .param("categoryId", "2")
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROUTINE200_2"))
                .andExpect(jsonPath("$.result.templates.length()").value(6))
                .andExpect(jsonPath("$.result.templates[0].name").value("물 챙겨 마시기"))
                .andExpect(jsonPath("$.result.templates[0].categoryName").value("건강"))
                .andExpect(jsonPath("$.result.templates[0].alreadyAdded").value(false));
    }

    @Test
    @DisplayName("이미 등록한 기본 루틴은 alreadyAdded로 표시한다")
    void getTemplates_AlreadyAddedTemplate_MarksAsAdded() throws Exception {
        // given
        Member member = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        routine(member, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/routines/templates")
                        .param("categoryId", "2")
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.templates[?(@.templateId == 201)].alreadyAdded")
                        .value(true));
    }

    @Test
    @DisplayName("활성 개인 루틴만 카테고리와 루틴 노출 순서대로 반환한다")
    void getRoutines_ReturnsActiveOwnedRoutinesInDisplayOrder() throws Exception {
        // given
        Member member = member();
        Member other = member();
        RoutineTemplate exerciseTemplate = em.find(RoutineTemplate.class, 101L);
        RoutineTemplate waterTemplate = em.find(RoutineTemplate.class, 201L);

        MemberRoutine healthCustom = routine(
                member, waterTemplate.getCategory(), null, "영양제 먹기");
        MemberRoutine exerciseCustom = routine(
                member, exerciseTemplate.getCategory(), null, "저녁 산책");
        MemberRoutine healthTemplate = routine(
                member, waterTemplate.getCategory(), waterTemplate, waterTemplate.getName());
        healthTemplate.addSchedule(DayOfWeek.FRIDAY);
        healthTemplate.addSchedule(DayOfWeek.WEDNESDAY);

        routine(other, exerciseTemplate.getCategory(), null, "다른 회원 루틴");
        MemberRoutine inactive = routine(
                member, exerciseTemplate.getCategory(), null, "비활성 루틴");
        ReflectionTestUtils.setField(inactive, "active", false);
        em.flush();
        em.clear();

        // when & then
        mockMvc.perform(get("/api/routines").with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("ROUTINE200_3"))
                .andExpect(jsonPath("$.result.routines.length()").value(3))
                .andExpect(jsonPath("$.result.routines[0].routineId")
                        .value(exerciseCustom.getId()))
                .andExpect(jsonPath("$.result.routines[1].routineId")
                        .value(healthTemplate.getId()))
                .andExpect(jsonPath("$.result.routines[1].templateId").value(201))
                .andExpect(jsonPath("$.result.routines[1].repeatDays")
                        .value(org.hamcrest.Matchers.contains(
                                "MONDAY", "WEDNESDAY", "FRIDAY")))
                .andExpect(jsonPath("$.result.routines[2].routineId")
                        .value(healthCustom.getId()));
    }

    @Test
    @DisplayName("이름을 유지한 수정은 기본 루틴 참조를 유지하고 설정을 교체한다")
    void updateRoutine_KeptTemplateName_UpdatesSettingsAndKeepsTemplate() throws Exception {
        Member member = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        MemberRoutine routine = routine(member, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        String body = """
                {
                  "name": "물 챙겨 마시기",
                  "endTime": "21:30",
                  "repeatDays": ["WEDNESDAY", "FRIDAY"],
                  "alarmTime": "20:30"
                }
                """;

        mockMvc.perform(patch("/api/routines/{routineId}", routine.getId())
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROUTINE200_4"))
                .andExpect(jsonPath("$.result.templateId").value(201))
                .andExpect(jsonPath("$.result.endTime").value("21:30"))
                .andExpect(jsonPath("$.result.alarmTime").value("20:30"))
                .andExpect(jsonPath("$.result.repeatDays")
                        .value(org.hamcrest.Matchers.contains("WEDNESDAY", "FRIDAY")));
    }

    @Test
    @DisplayName("기본 루틴 이름을 바꾸면 원본 참조를 해제한다")
    void updateRoutine_ChangedTemplateName_DetachesTemplate() throws Exception {
        Member member = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        MemberRoutine routine = routine(member, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        String body = """
                {
                  "name": "물 2L 마시기",
                  "endTime": "22:00",
                  "repeatDays": ["MONDAY"],
                  "alarmTime": null
                }
                """;

        mockMvc.perform(patch("/api/routines/{routineId}", routine.getId())
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.name").value("물 2L 마시기"))
                .andExpect(jsonPath("$.result.templateId").doesNotExist())
                .andExpect(jsonPath("$.result.alarmTime").doesNotExist());
    }

    @Test
    @DisplayName("다른 회원의 개인 루틴은 수정할 수 없다")
    void updateRoutine_OtherMembersRoutine_Returns404() throws Exception {
        Member owner = member();
        Member requester = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        MemberRoutine routine = routine(owner, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        String body = """
                {"name":"물 챙겨 마시기","endTime":"22:00","repeatDays":["MONDAY"]}
                """;
        mockMvc.perform(patch("/api/routines/{routineId}", routine.getId())
                        .with(user(principal(requester)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTINE404_3"));
    }

    @Test
    @DisplayName("삭제하면 비활성화되고 같은 기본 루틴을 다시 등록할 수 있다")
    void deleteRoutine_TemplateRoutine_AllowsTemplateRegistrationAgain() throws Exception {
        Member member = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        MemberRoutine routine = routine(member, water.getCategory(), water, water.getName());
        Long routineId = routine.getId();
        em.flush();
        em.clear();

        mockMvc.perform(delete("/api/routines/{routineId}", routineId)
                        .with(user(principal(member))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("ROUTINE200_5"));

        em.clear();
        MemberRoutine deleted = em.find(MemberRoutine.class, routineId);
        assertThat(deleted.getActive()).isFalse();
        assertThat(deleted.getTemplate()).isNull();
        assertThat(deleted.getSchedules()).isEmpty();

        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"routines":[
                                  {"categoryId":2,"templateId":201,"name":"물 챙겨 마시기"}
                                ]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.routines[0].templateId").value(201));
    }

    @Test
    @DisplayName("다른 회원의 개인 루틴은 삭제할 수 없다")
    void deleteRoutine_OtherMembersRoutine_Returns404() throws Exception {
        Member owner = member();
        Member requester = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        MemberRoutine routine = routine(owner, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        mockMvc.perform(delete("/api/routines/{routineId}", routine.getId())
                        .with(user(principal(requester))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTINE404_3"));
    }

    @Test
    @DisplayName("기본 루틴과 직접 추가 루틴을 한 요청으로 생성한다")
    void createRoutines_TemplateAndCustom_Returns201() throws Exception {
        // given
        Member member = member();
        em.flush();

        String body = """
                {
                  "routines": [
                    { "categoryId": 2, "templateId": 201, "name": "물 챙겨 마시기" },
                    { "categoryId": 2, "name": "영양제 먹기", "endTime": "21:00",
                      "repeatDays": ["MONDAY", "FRIDAY"], "alarmTime": "20:30" }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ROUTINE201_1"))
                .andExpect(jsonPath("$.result.activeRoutineCount").value(2))
                .andExpect(jsonPath("$.result.routines[0].templateId").value(201))
                .andExpect(jsonPath("$.result.routines[0].endTime").value("23:59"))
                .andExpect(jsonPath("$.result.routines[0].repeatDays.length()").value(7))
                .andExpect(jsonPath("$.result.routines[1].templateId").doesNotExist())
                .andExpect(jsonPath("$.result.routines[1].endTime").value("21:00"))
                .andExpect(jsonPath("$.result.routines[1].alarmTime").value("20:30"))
                .andExpect(jsonPath("$.result.routines[1].repeatDays")
                        .value(org.hamcrest.Matchers.contains("MONDAY", "FRIDAY")));
    }

    @Test
    @DisplayName("기본 루틴 이름을 바꾸면 원본 선택이 해제된 사용자 루틴으로 생성한다")
    void createRoutines_RenamedTemplate_DetachesTemplate() throws Exception {
        // given
        Member member = member();
        em.flush();

        String body = """
                {
                  "routines": [
                    { "categoryId": 2, "templateId": 201, "name": "물 2L 마시기" }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.result.routines[0].templateId").doesNotExist())
                .andExpect(jsonPath("$.result.routines[0].name").value("물 2L 마시기"))
                .andExpect(jsonPath("$.result.routines[0].categoryId").value(2));
    }

    @Test
    @DisplayName("이미 등록한 기본 루틴을 다시 등록하면 409를 반환한다")
    void createRoutines_AlreadyAddedTemplate_Returns409() throws Exception {
        // given
        Member member = member();
        RoutineTemplate water = em.find(RoutineTemplate.class, 201L);
        routine(member, water.getCategory(), water, water.getName());
        em.flush();
        em.clear();

        String body = """
                {
                  "routines": [
                    { "categoryId": 2, "templateId": 201, "name": "물 챙겨 마시기" }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("ROUTINE409_2"));
    }

    @Test
    @DisplayName("공백만 있는 이름은 400을 반환한다")
    void createRoutines_BlankName_Returns400() throws Exception {
        // given
        Member member = member();
        em.flush();

        String body = """
                {
                  "routines": [
                    { "categoryId": 2, "name": "   " }
                  ]
                }
                """;

        // when & then
        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("루틴을 하나도 선택하지 않으면 400을 반환한다")
    void createRoutines_EmptyList_Returns400() throws Exception {
        // given
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(post("/api/routines")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"routines\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    @Test
    @DisplayName("사용자 카테고리를 추가하면 색상과 함께 반환한다")
    void createCategory_ValidRequest_Returns201() throws Exception {
        // given
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(post("/api/routines/categories")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"  사이드  \", \"color\": \"MAGENTA\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ROUTINE201_2"))
                .andExpect(jsonPath("$.result.name").value("사이드"))
                .andExpect(jsonPath("$.result.color").value("MAGENTA"))
                .andExpect(jsonPath("$.result.fixed").value(false));
    }

    @Test
    @DisplayName("고정 카테고리와 같은 이름은 409를 반환한다")
    void createCategory_FixedCategoryName_Returns409() throws Exception {
        // given
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(post("/api/routines/categories")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"운동\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTINE409_4"));
    }

    @Test
    @DisplayName("10자를 넘는 카테고리 이름은 400을 반환한다")
    void createCategory_TooLongName_Returns400() throws Exception {
        // given
        Member member = member();
        em.flush();

        // when & then
        mockMvc.perform(post("/api/routines/categories")
                        .with(user(principal(member)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"열한자가넘어가는이름기\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false));
    }

    private Member member() {
        int value = sequence.incrementAndGet();
        Member member = Member.builder()
                .email("routine-controller-" + value + "@example.com")
                .nickname("루틴회원" + value)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId("routine-controller-social-" + value)
                .build();
        em.persist(member);
        return member;
    }

    private RoutineCategory memberCategory(Member owner, String name, RoutineCategoryColor color) {
        RoutineCategory category = RoutineCategory.builder()
                .owner(owner)
                .name(name)
                .color(color)
                .active(true)
                .build();
        em.persist(category);
        return category;
    }

    private MemberRoutine routine(
            Member member,
            RoutineCategory category,
            RoutineTemplate template,
            String name
    ) {
        MemberRoutine routine = MemberRoutine.builder()
                .member(member)
                .category(category)
                .template(template)
                .name(name)
                .build();
        routine.addSchedule(DayOfWeek.MONDAY);
        em.persist(routine);
        return routine;
    }

    private CustomUserDetails principal(Member member) {
        return new CustomUserDetails(member.getId(), Role.ROLE_USER);
    }
}
