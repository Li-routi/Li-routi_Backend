package com.lirouti.domain.verification.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.dto.response.VerificationResDTO;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.exception.VerificationException;
import com.lirouti.domain.verification.exception.code.error.VerificationErrorCode;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@SpringBootTest
@Transactional
@DisplayName("그룹 루틴 인증 게시물 좋아요 테스트")
class GroupRoutineVerificationLikeTest {
    @Autowired private GroupRoutineVerificationLikeCommandService likeCommandService;
    @Autowired private GroupRoutineVerificationLikeRepository likeRepository;

    @PersistenceContext private EntityManager em;
    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    @DisplayName("좋아요·취소는 멱등이고 하드 삭제 뒤 재좋아요할 수 있다")
    void likeAndUnlike_AreIdempotent() {
        Fixture fixture = fixture();

        VerificationResDTO.GroupRoutineLike first = likeCommandService.like(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());
        VerificationResDTO.GroupRoutineLike repeated = likeCommandService.like(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());
        VerificationResDTO.GroupRoutineLike removed = likeCommandService.unlike(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());
        VerificationResDTO.GroupRoutineLike absent = likeCommandService.unlike(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());
        VerificationResDTO.GroupRoutineLike reliked = likeCommandService.like(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());

        assertThat(first.likeCount()).isEqualTo(1);
        assertThat(first.liked()).isTrue();
        assertThat(repeated.likeCount()).isEqualTo(1);
        assertThat(removed.likeCount()).isZero();
        assertThat(removed.liked()).isFalse();
        assertThat(absent.likeCount()).isZero();
        assertThat(reliked.likeCount()).isEqualTo(1);
        assertThat(likeRepository.countByVerificationIds(java.util.List.of(fixture.verification().getId()))
                .getOrDefault(fixture.verification().getId(), 0L)).isEqualTo(1);
        assertThat(em.find(GroupMember.class, fixture.authorMembership().getId()).getTotalLikeCount())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("자기 인증 좋아요는 허용한다")
    void like_OwnVerification_IsAllowed() {
        Fixture fixture = fixture();

        VerificationResDTO.GroupRoutineLike result = likeCommandService.like(
                fixture.author().getId(), fixture.group().getId(), fixture.verification().getId());

        assertThat(result.likeCount()).isEqualTo(1);
        assertThat(result.liked()).isTrue();
    }

    @Test
    @DisplayName("방 밖 회원은 좋아요·취소할 수 없다")
    void like_Outsider_IsDenied() {
        Fixture fixture = fixture();
        Member outsider = member();

        assertThatThrownBy(() -> likeCommandService.like(
                outsider.getId(), fixture.group().getId(), fixture.verification().getId()))
                .isInstanceOf(GroupException.class)
                .hasFieldOrPropertyWithValue("code", GroupErrorCode.GROUP_MEMBER_ACCESS_DENIED);
    }

    @Test
    @DisplayName("내 방 자격으로 다른 방 인증에 좋아요할 수 없다")
    void like_VerificationOfAnotherGroup_IsNotFound() {
        Fixture mine = fixture();
        Fixture other = fixture();

        assertThatThrownBy(() -> likeCommandService.like(
                mine.liker().getId(), mine.group().getId(), other.verification().getId()))
                .isInstanceOf(VerificationException.class)
                .hasFieldOrPropertyWithValue(
                        "code", VerificationErrorCode.GROUP_ROUTINE_VERIFICATION_NOT_FOUND);
    }

    @Test
    @DisplayName("좋아요를 남긴 회원이 탈퇴해도 Like 행과 수는 유지된다")
    void like_RemainsAfterLikerLeaves() {
        Fixture fixture = fixture();
        likeCommandService.like(fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());
        fixture.likerMembership().leave();
        em.flush();
        em.clear();

        assertThat(likeRepository.countByVerificationIds(java.util.List.of(fixture.verification().getId()))
                .getOrDefault(fixture.verification().getId(), 0L)).isEqualTo(1);
    }

    @Test
    @DisplayName("작성자가 탈퇴한 과거 인증에는 Like를 남겨도 현재 활동 누적값을 갱신하지 않는다")
    void like_ForLeftAuthor_DoesNotChangeTotalLikeCount() {
        Fixture fixture = fixture();
        fixture.authorMembership().leave();
        em.flush();

        likeCommandService.like(
                fixture.liker().getId(), fixture.group().getId(), fixture.verification().getId());

        assertThat(likeRepository.countByVerificationIds(java.util.List.of(fixture.verification().getId()))
                .getOrDefault(fixture.verification().getId(), 0L)).isEqualTo(1);
        assertThat(fixture.authorMembership().getTotalLikeCount()).isZero();
    }

    private Fixture fixture() {
        int n = sequence.incrementAndGet();
        Member author = member();
        Member liker = member();
        Group group = Group.builder().name("좋아요그룹" + n).inviteCode("GL" + n + "AA").build();
        em.persist(group);
        GroupMember authorMembership = membership(group, author);
        GroupMember likerMembership = membership(group, liker);
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group).name("카테고리" + n).active(true).build();
        em.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("루틴" + n).description("설명").build();
        em.persist(routine);
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(author).assignedDate(LocalDate.now())
                .scheduledStartTime(LocalTime.MIDNIGHT).scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.COMPLETED).build();
        em.persist(assignment);
        GroupRoutineVerification verification = GroupRoutineVerification.builder()
                .assignment(assignment).verifiedAt(LocalDateTime.now())
                .imageUrl("group-routine-verifications/test-" + n + ".jpg").content("완료").build();
        em.persist(verification);
        em.flush();
        return new Fixture(group, author, liker, authorMembership, likerMembership, verification);
    }

    private Member member() {
        int n = sequence.incrementAndGet();
        Member member = Member.builder().email("grvl" + n + "@ex.com").nickname("회원" + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER).socialId("grvl-" + n).build();
        em.persist(member);
        return member;
    }

    private GroupMember membership(Group group, Member member) {
        GroupMember membership = GroupMember.builder()
                .group(group).member(member).role(GroupMemberRole.MEMBER).build();
        em.persist(membership);
        return membership;
    }

    private record Fixture(
            Group group,
            Member author,
            Member liker,
            GroupMember authorMembership,
            GroupMember likerMembership,
            GroupRoutineVerification verification
    ) {}
}
