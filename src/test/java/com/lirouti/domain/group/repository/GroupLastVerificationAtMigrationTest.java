package com.lirouti.domain.group.repository;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.verification.entity.GroupRoutineVerification;
import com.lirouti.domain.verification.repository.GroupRoutineVerificationRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** last_verification_at migration과 repair DML을 이전 데이터가 있는 상태에서 검증한다. */
@SpringBootTest
@Transactional
@DisplayName("그룹 마지막 인증 시각 Flyway migration 테스트")
class GroupLastVerificationAtMigrationTest {
    private static final String INITIAL_MIGRATION_PATH =
            "db/migration/V20260818194500__add_group_last_verification_at.sql";
    private static final String REPAIR_MIGRATION_PATH =
            "db/migration/V20260818203000__repair_group_last_verification_at_backfill.sql";
    private static final String BACKFILL_STATEMENT = "UPDATE `member_group`";

    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository groupMemberRepository;
    @Autowired private GroupRoutineCategoryRepository categoryRepository;
    @Autowired private GroupRoutineRepository routineRepository;
    @Autowired private GroupRoutineAssignmentRepository assignmentRepository;
    @Autowired private GroupRoutineVerificationRepository verificationRepository;
    @Autowired private MemberRepository memberRepository;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("같은 그룹의 여러 인증 중 가장 최신 createdAt을 backfill한다")
    void migration_BackfillsLatestVerificationCreatedAt() throws Exception {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment olderAssignment = assignment(fixture, "첫 번째 인증");
        GroupRoutineAssignment latestAssignment = assignment(fixture, "두 번째 인증");
        GroupRoutineVerification olderVerification = verificationRepository.save(verification(olderAssignment));
        GroupRoutineVerification latestVerification = verificationRepository.save(verification(latestAssignment));
        LocalDateTime olderCreatedAt = LocalDateTime.of(2026, 8, 17, 9, 30, 0, 123_000_000);
        LocalDateTime latestCreatedAt = LocalDateTime.of(2026, 8, 18, 19, 45, 0, 456_000_000);

        entityManager.flush();
        jdbcTemplate.update(
                "update group_routine_verification set created_at = ? where id = ?",
                olderCreatedAt,
                olderVerification.getId());
        jdbcTemplate.update(
                "update group_routine_verification set created_at = ? where id = ?",
                latestCreatedAt,
                latestVerification.getId());
        jdbcTemplate.update(
                "update member_group set last_verification_at = null where id = ?",
                fixture.group().getId());

        // when
        executeBackfillDml(INITIAL_MIGRATION_PATH);
        entityManager.clear();

        // then
        assertThat(groupRepository.findById(fixture.group().getId()).orElseThrow().getLastVerificationAt())
                .isEqualTo(latestCreatedAt);
    }

    @Test
    @DisplayName("인증 이력이 없는 그룹은 backfill 뒤에도 마지막 인증 시각이 null이다")
    void migration_KeepsNullWhenGroupHasNoVerificationHistory() throws Exception {
        // given
        Fixture fixture = fixture();
        jdbcTemplate.update(
                "update member_group set last_verification_at = null where id = ?",
                fixture.group().getId());

        // when
        executeBackfillDml(INITIAL_MIGRATION_PATH);

        // then
        Boolean isNull = jdbcTemplate.queryForObject(
                "select last_verification_at is null from member_group where id = ?",
                Boolean.class,
                fixture.group().getId());
        assertThat(isNull).isTrue();
    }

    @Test
    @DisplayName("repair backfill은 기존 마지막 인증 시각이 더 최신이면 유지한다")
    void repairMigration_PreservesNewerLastVerificationAt() throws Exception {
        // given
        Fixture fixture = fixture();
        GroupRoutineAssignment assignment = assignment(fixture, "repair 인증");
        GroupRoutineVerification verification = verificationRepository.save(verification(assignment));
        LocalDateTime backfillCreatedAt = LocalDateTime.of(2026, 8, 18, 19, 45, 0, 123_000_000);
        LocalDateTime existingLastVerificationAt = backfillCreatedAt.plusMinutes(1);

        entityManager.flush();
        jdbcTemplate.update(
                "update group_routine_verification set created_at = ? where id = ?",
                backfillCreatedAt,
                verification.getId());
        jdbcTemplate.update(
                "update member_group set last_verification_at = ? where id = ?",
                existingLastVerificationAt,
                fixture.group().getId());

        // when
        executeBackfillDml(REPAIR_MIGRATION_PATH);
        entityManager.clear();

        // then
        assertThat(groupRepository.findById(fixture.group().getId()).orElseThrow().getLastVerificationAt())
                .isEqualTo(existingLastVerificationAt);
    }

    private void executeBackfillDml(String migrationPath) throws Exception {
        String migrationSql = new ClassPathResource(migrationPath).getContentAsString(StandardCharsets.UTF_8);
        int backfillStart = migrationSql.indexOf(BACKFILL_STATEMENT);
        assertThat(backfillStart).isGreaterThanOrEqualTo(0);
        jdbcTemplate.execute(migrationSql.substring(backfillStart));
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Group group = groupRepository.save(Group.builder()
                .name("migration 검증 그룹")
                .inviteCode(suffix.substring(0, 7).toUpperCase())
                .build());
        Member member = memberRepository.save(Member.builder()
                .email(suffix + "@example.com")
                .nickname("migration회원")
                .socialProvider(SocialProvider.GOOGLE)
                .role(Role.ROLE_USER)
                .socialId(suffix)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .member(member)
                .role(GroupMemberRole.MEMBER)
                .build());
        GroupRoutineCategory category = categoryRepository.save(GroupRoutineCategory.builder()
                .group(group)
                .name("migration 카테고리")
                .active(true)
                .build());
        return new Fixture(group, member, category);
    }

    private GroupRoutineAssignment assignment(Fixture fixture, String title) {
        GroupRoutine routine = routineRepository.save(GroupRoutine.builder()
                .group(fixture.group())
                .category(fixture.category())
                .title(title)
                .description(title + " 설명")
                .build());
        return assignmentRepository.save(GroupRoutineAssignment.builder()
                .groupRoutine(routine)
                .member(fixture.member())
                .assignedDate(LocalDate.of(2026, 8, 18))
                .scheduledStartTime(LocalTime.of(9, 0))
                .scheduledEndTime(LocalTime.of(18, 0))
                .status(GroupRoutineAssignmentStatus.IN_PROGRESS)
                .build());
    }

    private GroupRoutineVerification verification(GroupRoutineAssignment assignment) {
        return GroupRoutineVerification.builder()
                .assignment(assignment)
                .verifiedAt(LocalDateTime.of(2026, 8, 18, 10, 0))
                .imageUrl("group-routine-verifications/migration-test.jpg")
                .content("migration backfill 검증")
                .build();
    }

    private record Fixture(Group group, Member member, GroupRoutineCategory category) {
    }
}
