package com.lirouti.support.testdb;

import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 통합 테스트가 만든 회원의 업적·스트릭 종속 행을 회원 삭제 전에 정리한다.
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
    }
}
