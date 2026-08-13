package com.lirouti.domain.character.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
}
