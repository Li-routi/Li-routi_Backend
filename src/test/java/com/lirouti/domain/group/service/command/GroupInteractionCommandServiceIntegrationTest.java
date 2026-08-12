package com.lirouti.domain.group.service.command;

import com.lirouti.domain.group.dto.response.GroupInteractionResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.entity.GroupRoutine;
import com.lirouti.domain.group.entity.GroupRoutineAssignment;
import com.lirouti.domain.group.entity.GroupRoutineCategory;
import com.lirouti.domain.group.enums.GroupMemberRole;
import com.lirouti.domain.group.enums.GroupRoutineAssignmentStatus;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.entity.GroupRoutineVerificationLike;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationDisappointmentRepository;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationLikeRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@DisplayName("그룹 인증 아쉬워요 명령 통합 테스트")
class GroupInteractionCommandServiceIntegrationTest {
    @Autowired private GroupInteractionCommandService groupInteractionCommandService;
    @Autowired private GroupRoutineVerificationLikeRepository likeRepository;
    @Autowired private GroupRoutineVerificationDisappointmentRepository disappointmentRepository;

    @PersistenceContext private EntityManager entityManager;

    @Test
    @DisplayName("기존 좋아요가 없어도 clear된 영속성 컨텍스트에서 아쉬워요를 저장한다")
    void disappoint_WithoutExistingLike_AfterPersistenceContextClear_Succeeds() {
        Fixture fixture = fixture(false);
        entityManager.flush();
        entityManager.clear();

        GroupInteractionResDTO.Disappointment result = groupInteractionCommandService.disappoint(
                fixture.actorId(), fixture.groupId(), fixture.verificationId());

        assertThat(result).isEqualTo(new GroupInteractionResDTO.Disappointment(
                fixture.verificationId(), 1L, true));
        assertThat(disappointmentRepository.countByVerificationId(fixture.verificationId())).isEqualTo(1L);
    }

    @Test
    @DisplayName("기존 좋아요를 제거해 clear된 영속성 컨텍스트에서도 아쉬워요를 저장한다")
    void disappoint_WithExistingLike_AfterPersistenceContextClear_Succeeds() {
        Fixture fixture = fixture(true);
        entityManager.flush();
        entityManager.clear();

        GroupInteractionResDTO.Disappointment result = groupInteractionCommandService.disappoint(
                fixture.actorId(), fixture.groupId(), fixture.verificationId());

        assertThat(result).isEqualTo(new GroupInteractionResDTO.Disappointment(
                fixture.verificationId(), 1L, true));
        assertThat(likeRepository.findByGroupRoutineVerificationIdAndMemberId(
                fixture.verificationId(), fixture.actorId())).isEmpty();
        assertThat(disappointmentRepository.countByVerificationId(fixture.verificationId())).isEqualTo(1L);
    }

    private Fixture fixture(boolean withExistingLike) {
        String suffix = UUID.randomUUID().toString();
        Group group = Group.builder().name("아쉬워요 그룹")
                .inviteCode(suffix.replace("-", "").substring(0, 7).toUpperCase())
                .build();
        entityManager.persist(group);
        Member author = member("author-" + suffix);
        Member actor = member("actor-" + suffix);
        entityManager.persist(GroupMember.builder()
                .group(group).member(author).role(GroupMemberRole.MEMBER).build());
        entityManager.persist(GroupMember.builder()
                .group(group).member(actor).role(GroupMemberRole.MEMBER).build());
        GroupRoutineCategory category = GroupRoutineCategory.builder()
                .group(group).name("카테고리").active(true).build();
        entityManager.persist(category);
        GroupRoutine routine = GroupRoutine.builder()
                .group(group).category(category).title("루틴").description("설명").build();
        entityManager.persist(routine);
        GroupRoutineAssignment assignment = GroupRoutineAssignment.builder()
                .groupRoutine(routine).member(author).assignedDate(LocalDate.now())
                .scheduledStartTime(LocalTime.MIDNIGHT).scheduledEndTime(LocalTime.of(23, 59, 59))
                .status(GroupRoutineAssignmentStatus.COMPLETED).build();
        entityManager.persist(assignment);
        GroupRoutineVerification verification = GroupRoutineVerification.builder()
                .assignment(assignment).verifiedAt(LocalDateTime.now())
                .imageUrl("group-routine-verifications/test.jpg").content("완료").build();
        entityManager.persist(verification);
        if (withExistingLike) {
            entityManager.persist(GroupRoutineVerificationLike.builder()
                    .groupRoutineVerification(verification).member(actor).build());
        }
        return new Fixture(group.getId(), actor.getId(), verification.getId());
    }

    private Member member(String identifier) {
        Member member = Member.builder()
                .email(identifier + "@example.com")
                .nickname(identifier)
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(identifier)
                .build();
        entityManager.persist(member);
        return member;
    }

    private record Fixture(Long groupId, Long actorId, Long verificationId) {
    }
}
