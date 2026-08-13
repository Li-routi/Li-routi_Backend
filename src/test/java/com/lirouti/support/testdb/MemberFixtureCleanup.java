package com.lirouti.support.testdb;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트가 만든 회원의 업적·스트릭·활동일 종속 행을 회원 삭제 전에 정리한다.
 *
 * <p><b>회원을 참조하는 표를 새로 만들면 여기에도 더해야 한다.</b> 빠뜨리면 회원이 지워지지
 * 않고, 다음 테스트가 같은 이메일로 회원을 만들다 중복 키로 죽는다 — 원인이 새 표라는 것이
 * 오류 메시지에 드러나지 않아 한참 헤맨다.
 */
public final class MemberFixtureCleanup {

    private MemberFixtureCleanup() {
    }

    public static void deleteDependencies(EntityManager entityManager, Long memberId) {
        entityManager.createNativeQuery("""
                delete from member_achievement_progress_category
                where member_achievement_id in (
                    select id from member_achievement where member_id = :memberId
                )
                """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                delete from member_achievement_progress_day
                where member_achievement_id in (
                    select id from member_achievement where member_id = :memberId
                )
                """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("""
                delete from member_achievement_condition
                where member_achievement_id in (
                    select id from member_achievement where member_id = :memberId
                )
                """)
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("delete from member_achievement where member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("delete from member_routine_streak where member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
        entityManager.createNativeQuery("delete from member_activity_day where member_id = :memberId")
                .setParameter("memberId", memberId)
                .executeUpdate();
    }

    public static void deleteDependencies(JdbcTemplate jdbcTemplate, Long memberId) {
        jdbcTemplate.update("""
                delete from member_achievement_progress_category
                where member_achievement_id in (
                    select id from member_achievement where member_id = ?
                )
                """, memberId);
        jdbcTemplate.update("""
                delete from member_achievement_progress_day
                where member_achievement_id in (
                    select id from member_achievement where member_id = ?
                )
                """, memberId);
        jdbcTemplate.update("""
                delete from member_achievement_condition
                where member_achievement_id in (
                    select id from member_achievement where member_id = ?
                )
                """, memberId);
        jdbcTemplate.update("delete from member_achievement where member_id = ?", memberId);
        jdbcTemplate.update("delete from member_routine_streak where member_id = ?", memberId);
        jdbcTemplate.update("delete from member_activity_day where member_id = ?", memberId);
    }
}
