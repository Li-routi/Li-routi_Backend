package com.lirouti.domain.verification.service.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

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
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.media.enums.MediaPurpose;
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
import com.lirouti.domain.verification.service.RoutineVerificationService;
import com.lirouti.global.util.TimeUtil;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 루틴 인증 조회 — <b>접근 경계가 이 테스트의 주제다.</b>
 *
 * <p>인증 사진은 비공개 저장소에 있어 주소만으로는 열리지 않는다. 조회가 서명을 붙여 주는
 * 순간 그 URL 을 받은 사람은 사진을 볼 수 있으므로, <b>서명을 받을 자격을 잘못 판정하면
 * 그것이 곧 유출이다.</b> 그래서 "내 것이 나오는가"만큼이나 "남의 것이 막히는가"를 본다.
 *
 * <p>서명 자체는 mock 한다. 여기서 보려는 것은 <b>누구에게 서명을 만들어 주는가</b>와
 * <b>어느 용도로 만드는가</b>이지, 서명 문자열이 맞는지가 아니다(그건 MediaServiceTest 몫).
 */
@SpringBootTest
@Transactional
@DisplayName("루틴 인증 조회 테스트")
class RoutineVerificationQueryTest {

    private static final String MEMBER_KEY =
            "member-routine-verifications/2026/07/31/aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa.jpg";
    private static final String GROUP_KEY =
            "group-routine-verifications/2026/07/31/bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb.jpg";
    private static final String SIGNED_URL = "https://s3.example.com/signed?X-Amz-Signature=stub";

    @Autowired
    private RoutineVerificationQueryService queryService;
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
        when(mediaService.resolveViewUrl(any(), any())).thenReturn(SIGNED_URL);
        owner = member();
    }

    // ── 픽스처 ──
    private Member member() {
        int n = seq.incrementAndGet();
        Member m = Member.builder()
                .email("rvq" + n + "@ex.com").nickname("조회자" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId("rvq-sid-" + n).build();
        em.persist(m);
        return m;
    }

    /** 오늘 요일에 수행하는 개인 루틴. active 를 꺼서 "비활성 루틴"도 만들 수 있다. */
    private MemberRoutine memberRoutine(Member member, boolean active) {
        int n = seq.incrementAndGet();
        RoutineCategory category = RoutineCategory.builder()
                .owner(member).name("카테고리" + n).active(true).build();
        em.persist(category);
        MemberRoutine routine = MemberRoutine.builder()
                .member(member).category(category).name("아침 스트레칭")
                .endTime(LocalTime.of(9, 0)).active(active).build();
        routine.addSchedule(LocalDate.now(TimeUtil.KST).getDayOfWeek());
        em.persist(routine);
        em.flush();
        return routine;
    }

    /** 그룹과 그 방의 활성 멤버 한 명을 만든다. */
    private Group group(Member... members) {
        int n = seq.incrementAndGet();
        Group group = Group.builder().name("그룹" + n).inviteCode("RVQ" + n).build();
        em.persist(group);
        for (Member member : members) {
            // status 는 생성자가 ACTIVE 로 세팅한다(빌더에 없다).
            em.persist(GroupMember.builder()
                    .group(group).member(member).role(GroupMemberRole.MEMBER)
                    .build());
        }
        em.flush();
        return group;
    }

    private GroupRoutineAssignment assignment(Group group, Member member) {
        int n = seq.incrementAndGet();
        RoutineCategory category = RoutineCategory.builder().name("카테고리" + n).active(true).build();
        em.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("아침 청소" + n).description("설명").build();
        em.persist(routine);

        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(member)
                .assignedDate(LocalDate.now(TimeUtil.KST))
                .scheduledStartTime(LocalTime.MIDNIGHT)
                .scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build();
        em.persist(assignment);
        em.flush();
        return assignment;
    }

    private void verifyMember(Member member, MemberRoutine routine) {
        verificationService.verifyMemberRoutine(member.getId(), routine.getId(),
                new VerificationReqDTO.Verify(MEMBER_KEY, "완료"));
    }

    private void verifyGroup(Member member, GroupRoutineAssignment assignment) {
        verificationService.verifyGroupRoutine(
                member.getId(),
                assignment.getGroupRoutine().getGroup().getId(),
                assignment.getGroupRoutine().getId(),
                new VerificationReqDTO.Verify(GROUP_KEY, "청소 완료"));
    }

    // ── 개인 루틴 ──

    @Test
    @DisplayName("내 인증에는 서명된 사진 주소가 실려 나온다 — key 그대로 내리면 열리지 않는다")
    void getMemberRoutineVerifications_ReturnsSignedUrl() {
        // given
        MemberRoutine routine = memberRoutine(owner, true);
        verifyMember(owner, routine);
        em.flush();
        em.clear();

        // when
        VerificationResDTO.MemberRoutineFeed feed =
                queryService.getMemberRoutineVerifications(owner.getId(), routine.getId(), null, null);

        // then
        assertAll(
                () -> assertThat(feed.verifications()).hasSize(1),
                () -> assertThat(feed.verifications().get(0).imageUrl()).isEqualTo(SIGNED_URL),
                () -> assertThat(feed.hasNext()).isFalse(),
                () -> assertThat(feed.nextCursor()).isNull()
        );
    }

    @Test
    @DisplayName("개인 인증은 개인 용도로 서명한다 — 용도를 섞으면 공개 주소가 나갈 수 있다")
    void getMemberRoutineVerifications_SignsWithMemberPurpose() {
        // given
        MemberRoutine routine = memberRoutine(owner, true);
        verifyMember(owner, routine);
        em.flush();
        em.clear();

        // when
        queryService.getMemberRoutineVerifications(owner.getId(), routine.getId(), null, null);

        // then
        org.mockito.Mockito.verify(mediaService)
                .resolveViewUrl(eq(MEMBER_KEY), eq(MediaPurpose.MEMBER_ROUTINE_VERIFICATION));
    }

    @Test
    @DisplayName("남의 루틴 인증은 볼 수 없다 — 없는 루틴과 같은 404다")
    void getMemberRoutineVerifications_OthersRoutine_IsNotFound() {
        // given
        MemberRoutine othersRoutine = memberRoutine(member(), true);

        // when & then
        assertThatThrownBy(() -> queryService.getMemberRoutineVerifications(
                owner.getId(), othersRoutine.getId(), null, null))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue("code", VerificationErrorCode.ROUTINE_NOT_FOUND);
    }

    @Test
    @DisplayName("비활성 루틴의 기록도 볼 수 있다 — 루틴을 끈 것과 기록을 지운 것은 다르다")
    void getMemberRoutineVerifications_InactiveRoutine_StillReadable() {
        // given: 인증은 활성일 때 하고, 그 뒤 루틴을 끈다.
        // 비활성화 API 가 아직 없어 엔티티에 상태 변경 메서드도 없다. 여기서는 그 상황을
        // 만들어야 하므로 직접 UPDATE 한다. 루틴 수정·삭제 API가 생기면 그 경로로 바꾼다.
        MemberRoutine routine = memberRoutine(owner, true);
        verifyMember(owner, routine);
        em.createQuery("update MemberRoutine r set r.active = false where r.id = :id")
                .setParameter("id", routine.getId())
                .executeUpdate();
        em.flush();
        em.clear();

        // when
        VerificationResDTO.MemberRoutineFeed feed =
                queryService.getMemberRoutineVerifications(owner.getId(), routine.getId(), null, null);

        // then
        assertThat(feed.verifications()).hasSize(1);
    }

    // ── 그룹 루틴 ──

    @Test
    @DisplayName("방 멤버는 다른 멤버의 인증까지 작성자와 함께 본다 — 방 피드의 핵심이다")
    void getGroupRoutineVerifications_MemberSeesOthersWithAuthor() {
        // given: 작성자는 다른 사람이고, 조회자는 같은 방의 또 다른 멤버다
        Member author = member();
        Group group = group(author, owner);
        GroupRoutineAssignment assignment = assignment(group, author);
        verifyGroup(author, assignment);
        em.flush();
        em.clear();

        // when
        VerificationResDTO.GroupRoutineFeed feed = queryService.getGroupRoutineVerifications(
                owner.getId(), group.getId(), assignment.getGroupRoutine().getId(), null, null);

        // then
        assertAll(
                () -> assertThat(feed.verifications()).hasSize(1),
                () -> assertThat(feed.verifications().get(0).memberId()).isEqualTo(author.getId()),
                () -> assertThat(feed.verifications().get(0).nickname())
                        .isEqualTo(author.getNickname()),
                () -> assertThat(feed.verifications().get(0).imageUrl()).isEqualTo(SIGNED_URL)
        );
    }

    @Test
    @DisplayName("방 밖의 회원은 볼 수 없다 — 서명을 받을 자격이 없다")
    void getGroupRoutineVerifications_Outsider_IsDenied() {
        // given
        Member author = member();
        Group group = group(author);
        GroupRoutineAssignment assignment = assignment(group, author);
        verifyGroup(author, assignment);
        em.flush();
        em.clear();

        // when & then: owner 는 그 방에 없다
        assertThatThrownBy(() -> queryService.getGroupRoutineVerifications(
                owner.getId(), group.getId(), assignment.getGroupRoutine().getId(), null, null))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("내 방 자격으로 다른 방의 루틴을 읽을 수 없다 — 경로의 두 값이 짝이어야 한다")
    void getGroupRoutineVerifications_RoutineOfAnotherGroup_IsNotFound() {
        // given: owner 가 속한 방과, 인증이 들어 있는 남의 방을 따로 만든다
        Group myGroup = group(owner);
        Member outsider = member();
        Group othersGroup = group(outsider);
        GroupRoutineAssignment othersAssignment = assignment(othersGroup, outsider);
        verifyGroup(outsider, othersAssignment);
        em.flush();
        em.clear();

        // when & then: 내 방 id + 남의 방 루틴 id 조합
        assertThatThrownBy(() -> queryService.getGroupRoutineVerifications(
                owner.getId(), myGroup.getId(),
                othersAssignment.getGroupRoutine().getId(), null, null))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_ROUTINE_NOT_FOUND);
    }

    @Test
    @DisplayName("size를 넘기면 다음 커서를 준다 — 커서로 이어받아 나머지를 읽는다")
    void getGroupRoutineVerifications_Paginates() {
        // given: 같은 루틴에 두 멤버가 인증한다(할당 1건에 인증 1건이라 멤버로 늘린다)
        Member first = member();
        Member second = member();
        Group group = group(first, second, owner);
        GroupRoutineAssignment firstAssignment = assignment(group, first);
        GroupRoutine routine = firstAssignment.getGroupRoutine();

        GroupRoutineAssignment secondAssignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(second)
                .assignedDate(LocalDate.now(TimeUtil.KST))
                .scheduledStartTime(LocalTime.MIDNIGHT)
                .scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build();
        em.persist(secondAssignment);
        em.flush();

        verifyGroup(first, firstAssignment);
        verifyGroup(second, secondAssignment);
        em.flush();
        em.clear();

        // when
        VerificationResDTO.GroupRoutineFeed page =
                queryService.getGroupRoutineVerifications(
                        owner.getId(), group.getId(), routine.getId(), null, 1);
        VerificationResDTO.GroupRoutineFeed next =
                queryService.getGroupRoutineVerifications(
                        owner.getId(), group.getId(), routine.getId(), page.nextCursor(), 1);

        // then: 최신순이라 나중에 인증한 second 가 먼저 나온다
        assertAll(
                () -> assertThat(page.verifications()).hasSize(1),
                () -> assertThat(page.hasNext()).isTrue(),
                () -> assertThat(page.nextCursor()).isNotNull(),
                () -> assertThat(page.verifications().get(0).memberId()).isEqualTo(second.getId()),
                () -> assertThat(next.verifications()).hasSize(1),
                () -> assertThat(next.verifications().get(0).memberId()).isEqualTo(first.getId()),
                () -> assertThat(next.hasNext()).isFalse()
        );
    }
}
