package com.lirouti.domain.activity.repository;

import com.lirouti.domain.activity.entity.MemberActivityDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface MemberActivityDayRepository extends JpaRepository<MemberActivityDay, Long> {

    /**
     * 활동일을 남긴다. <b>이미 있으면 {@code all_completed} 만 올린다.</b>
     *
     * <p>행을 만드는 일과 {@code all_completed} 를 올리는 일은 다르다. "중복이면 조용히 무시"
     * 로 끝내면 이 순서에서 값이 갱신되지 않는다.
     *
     * <pre>
     * 09:00  챌린지 인증        → 행 INSERT (all_completed = 0)
     * 21:00  마지막 개인 루틴 완료 → INSERT 가 유니크에 튕김 → 무시
     *                             all_completed 가 0 인 채로 남는다
     * </pre>
     *
     * <p><b>{@code GREATEST} 가 값을 한 방향으로만 움직인다.</b> 그룹·챌린지 인증이 0 을 써도
     * 이미 선 1 을 덮어 내리지 않고, 개인 루틴 완료가 1 을 쓰면 행이 있어도 올라간다.
     *
     * <p><b>한 번 1 이면 그날은 1 로 굳는다.</b> 그날 예정된 것을 다 한 순간이 있었다면 그건
     * 사실이고, 저녁에 루틴을 하나 더 만들었다고 오늘이 소급해서 실패가 되면 "루틴을 추가하면
     * 그날을 잃는" 규칙이 된다 — 앱이 권하는 행동과 반대다.
     *
     * <p>JPA 로는 이 갱신을 표현할 수 없어 네이티브로 둔다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO member_activity_day
                (member_id, activity_date, all_completed, created_at, updated_at)
            VALUES (:memberId, :activityDate, :allCompleted, NOW(6), NOW(6)) AS new_row
            ON DUPLICATE KEY UPDATE
                all_completed = GREATEST(member_activity_day.all_completed, new_row.all_completed),
                updated_at    = NOW(6)
            """, nativeQuery = true)
    void record(@Param("memberId") Long memberId,
                @Param("activityDate") LocalDate activityDate,
                @Param("allCompleted") boolean allCompleted);

    /**
     * 활동한 날 수. {@code ACTIVE_DAYS} 가 센다.
     */
    long countByMemberId(Long memberId);

    /**
     * 예정된 개인 루틴을 전부 완수한 날 수를, <b>기준일부터 거슬러 센 구간</b>에서.
     *
     * <p>둥지 레벨이 이것을 쓴다 — 창 길이만큼 나오면 그 기간이 연속이라는 뜻이다. 하루라도
     * 비면 창에 그만큼이 들어올 수 없어 <b>끊김을 따로 판정할 필요가 없다.</b>
     *
     * <p>기준일을 애플리케이션이 넘긴다. {@code CURRENT_DATE} 를 쓰면 DB 세션의 시간대가
     * 기준이 되는데 {@code activity_date} 는 KST 로 찍힌 값이라, 자정 언저리에 하루가 밀려
     * 연속이 끊긴 것처럼 보인다.
     */
    long countByMemberIdAndAllCompletedTrueAndActivityDateAfter(Long memberId, LocalDate exclusiveFrom);
}
