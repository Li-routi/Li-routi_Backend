package com.lirouti.domain.character.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.Collection;
import java.util.Map;

/**
 * 해금 조건이 세는 값 중 <b>여러 도메인을 가로지르는 것</b>만 여기서 읽는다.
 *
 * <p>한 도메인 안에서 끝나는 것(활동일 수 등)은 그 도메인의 리포지토리가 센다.
 */
@Repository
@RequiredArgsConstructor
public class CharacterConditionQueryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    /**
     * 그 카테고리를 한 <b>서로 다른 날</b>의 수.
     *
     * <p>개인·그룹·챌린지를 모두 포함하고 <b>날짜로 합집합을 만든 뒤 센다.</b> 원천별로 세어
     * 더하면 같은 날이 두 번 들어가고, 하루에 같은 카테고리 루틴을 셋 하면 3일로 잡힌다 —
     * 조건이 묻는 것은 "며칠 했는가" 이지 "몇 번 했는가" 가 아니다.
     *
     * <p>챌린지 재참여는 회차마다 인증 행이 따로 생기는데, 날짜로 중복을 제거하면 그것도 함께
     * 해결되므로 회차를 따로 거르지 않는다.
     *
     * <p>지워진 챌린지 인증은 세지 않는다 — 본인이 내린 글이다. 반면 <b>보류 중인 것은
     * 센다</b>: 심사가 늦어지는 것은 사용자가 한 일과 무관하고, 통과하면 어차피 세어진다.
     *
     * @param presetCategoryIds  개인·그룹 프리셋 카테고리 id
     * @param challengeCategories 챌린지 카테고리 이름
     */
    public long countDistinctCategoryDays(Long memberId,
                                          Collection<Long> presetCategoryIds,
                                          Collection<String> challengeCategories) {
        if (presetCategoryIds.isEmpty() || challengeCategories.isEmpty()) {
            return 0L;
        }

        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT mrv.verified_date AS activity_date
                    FROM member_routine_verification mrv
                    JOIN member_routine mr ON mr.id = mrv.member_routine_id
                    WHERE mr.member_id = :memberId
                      AND mr.category_id IN (:presetCategoryIds)
                    UNION
                    SELECT DATE(grv.verified_at) AS activity_date
                    FROM group_routine_verification grv
                    JOIN group_routine_assignment gra
                        ON gra.id = grv.group_routine_assignment_id
                    JOIN group_routine gr ON gr.id = gra.group_routine_id
                    WHERE gra.member_id = :memberId
                      AND gr.group_routine_category_id IN (:presetCategoryIds)
                    UNION
                    SELECT cv.verified_date AS activity_date
                    FROM challenge_verification cv
                    JOIN member_challenge mc ON mc.id = cv.member_challenge_id
                    JOIN challenge c ON c.id = mc.challenge_id
                    WHERE mc.member_id = :memberId
                      AND cv.deleted_at IS NULL
                      AND c.category IN (:challengeCategories)
                ) days
                """;

        Long counted = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource(Map.of(
                "memberId", memberId,
                "presetCategoryIds", presetCategoryIds,
                "challengeCategories", challengeCategories
        )), Long.class);
        return counted == null ? 0L : counted;
    }

    /**
     * 그 시간대에 완료한 <b>서로 다른 날</b>의 수.
     *
     * <p>개인·그룹·챌린지를 모두 포함한다 — 조건이 묻는 것은 "그 시간에 루틴을 했는가" 이지
     * 어느 기능을 썼는가가 아니다.
     *
     * <p>시간대는 인증이 접수된 시각으로 본다. 자정을 넘는 구간(예: 23:00-01:00)은 지금
     * 조건에 없어 다루지 않는다 — 필요해지면 그때 규칙을 정한다.
     */
    public long countDistinctTimeWindowDays(Long memberId, LocalTime from, LocalTime to) {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT mrv.verified_date AS activity_date
                    FROM member_routine_verification mrv
                    JOIN member_routine mr ON mr.id = mrv.member_routine_id
                    WHERE mr.member_id = :memberId
                      AND TIME(mrv.verified_at) BETWEEN :from AND :to
                    UNION
                    SELECT DATE(grv.verified_at) AS activity_date
                    FROM group_routine_verification grv
                    JOIN group_routine_assignment gra
                        ON gra.id = grv.group_routine_assignment_id
                    WHERE gra.member_id = :memberId
                      AND TIME(grv.verified_at) BETWEEN :from AND :to
                    UNION
                    SELECT cv.verified_date AS activity_date
                    FROM challenge_verification cv
                    JOIN member_challenge mc ON mc.id = cv.member_challenge_id
                    WHERE mc.member_id = :memberId
                      AND cv.deleted_at IS NULL
                      AND TIME(cv.verified_at) BETWEEN :from AND :to
                ) days
                """;
        return count(sql, new MapSqlParameterSource(Map.of(
                "memberId", memberId, "from", from.toString(), "to", to.toString())));
    }

    /**
     * 하루에 <b>개인·모임·챌린지를 모두</b> 완료한 날의 수.
     *
     * <p>세 종류의 날짜 집합을 교집합으로 만든다. 한 종류라도 빠진 날은 세지 않는다.
     */
    public long countAllKindsDays(Long memberId) {
        String sql = """
                SELECT COUNT(*) FROM (
                    SELECT mrv.verified_date AS activity_date
                    FROM member_routine_verification mrv
                    JOIN member_routine mr ON mr.id = mrv.member_routine_id
                    WHERE mr.member_id = :memberId
                ) personal
                JOIN (
                    SELECT DISTINCT DATE(grv.verified_at) AS activity_date
                    FROM group_routine_verification grv
                    JOIN group_routine_assignment gra
                        ON gra.id = grv.group_routine_assignment_id
                    WHERE gra.member_id = :memberId
                ) grouped ON grouped.activity_date = personal.activity_date
                JOIN (
                    SELECT DISTINCT cv.verified_date AS activity_date
                    FROM challenge_verification cv
                    JOIN member_challenge mc ON mc.id = cv.member_challenge_id
                    WHERE mc.member_id = :memberId AND cv.deleted_at IS NULL
                ) challenged ON challenged.activity_date = personal.activity_date
                """;
        return count(sql, new MapSqlParameterSource(Map.of("memberId", memberId)));
    }

    /**
     * 마감까지 남은 시간이 <b>1초 이상 {@code withinSeconds} 이하</b>일 때 완료한 건수.
     *
     * <p>마감 시각과 같거나 그 뒤는 인정하지 않는다 — "아슬아슬하게 해냈다" 가 조건의 뜻이다.
     *
     * <p><b>지금 값으로 소급 판정한다.</b> 루틴의 마감 시각을 나중에 당기면 과거 인증이 이
     * 조건에 걸릴 수 있다. 해금은 되돌리지 않으므로 잘못 열리는 쪽으로만 틀리고, 인증 시점에
     * 따로 기록하는 것보다 표와 훅이 늘지 않는다.
     */
    public long countDeadlineRush(Long memberId, int withinSeconds) {
        String sql = """
                SELECT COUNT(*)
                FROM member_routine_verification mrv
                JOIN member_routine mr ON mr.id = mrv.member_routine_id
                WHERE mr.member_id = :memberId
                  AND TIME_TO_SEC(mr.end_time) - TIME_TO_SEC(TIME(mrv.verified_at))
                      BETWEEN 1 AND :withinSeconds
                """;
        return count(sql, new MapSqlParameterSource(Map.of(
                "memberId", memberId, "withinSeconds", withinSeconds)));
    }

    /**
     * 지금 좋아요를 눌러 둔 <b>남의 인증</b> 수. 모임·챌린지를 합산한다.
     *
     * <p><b>본인 인증은 뺀다.</b> 자기 글에 눌러 채울 수 있으면 조건이 뜻을 잃는다.
     *
     * <p>취소가 하드 삭제라 누적은 셀 수 없다. 그래서 "지금 눌러 둔 수" 를 센다 — 취소하면
     * 줄어들지만, 조건에 닿는 순간 해금되고 해금은 되돌리지 않는다.
     */
    public long countLikesGiven(Long memberId) {
        String sql = """
                SELECT
                    (SELECT COUNT(*)
                       FROM challenge_verification_like cvl
                       JOIN challenge_verification cv ON cv.id = cvl.challenge_verification_id
                       JOIN member_challenge mc ON mc.id = cv.member_challenge_id
                      WHERE cvl.member_id = :memberId
                        AND cv.deleted_at IS NULL
                        AND mc.member_id <> :memberId)
                  + (SELECT COUNT(*)
                       FROM group_routine_verification_like grvl
                       JOIN group_routine_verification grv
                           ON grv.id = grvl.group_routine_verification_id
                       JOIN group_routine_assignment gra
                           ON gra.id = grv.group_routine_assignment_id
                      WHERE grvl.member_id = :memberId
                        AND gra.member_id <> :memberId)
                """;
        return count(sql, new MapSqlParameterSource(Map.of("memberId", memberId)));
    }

    private long count(String sql, MapSqlParameterSource params) {
        Long counted = jdbcTemplate.queryForObject(sql, params, Long.class);
        return counted == null ? 0L : counted;
    }
}
