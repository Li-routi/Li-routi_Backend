package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.RoutineCategory;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineCategoryRepository extends JpaRepository<RoutineCategory, Long> {
    /**
     * 신규 루틴에 사용할 수 있는 활성 카테고리를 ID로 조회한다.
     *
     * @param id 카테고리 ID
     * @return 활성 카테고리, 없으면 빈 값
     */
    Optional<RoutineCategory> findByIdAndActiveTrue(Long id);

    /**
     * 회원이 사용할 수 있는 활성 카테고리, 즉 고정 카테고리 전체와 그 회원의 사용자 카테고리를
     * 화면 노출 순서대로 조회한다. 고정 카테고리가 먼저 오고 그다음이 사용자 카테고리다.
     * 고정 카테고리는 시드가 정한 {@code display_order}를, 사용자 카테고리는 그 값이 모두
     * 0이라 생성 순서(id)를 따른다.
     *
     * <p>{@code owner}를 명시적으로 left join 하는 이유는, {@code c.owner.id} 같은 암묵적 경로가
     * inner join으로 풀려 소유자가 없는 고정 카테고리를 통째로 떨어뜨리기 때문이다.
     *
     * @param memberId 조회할 회원 ID
     * @return 고정 카테고리와 해당 회원의 사용자 카테고리 목록
     */
    @Query("""
            select category
            from RoutineCategory category
            left join category.owner owner
            where category.active = true
              and (owner is null or owner.id = :memberId)
            order by case when owner is null then 0 else 1 end asc,
                     category.displayOrder asc,
                     category.id asc
            """)
    List<RoutineCategory> findUsableByMemberId(@Param("memberId") Long memberId);

    /**
     * 회원이 이미 만든 활성 사용자 카테고리 수를 센다. 추가 상한 검사에 사용한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 해당 회원이 소유한 활성 카테고리 수
     */
    long countByOwnerIdAndActiveTrue(Long memberId);

    /**
     * 회원이 쓸 이름이 이미 고정 카테고리나 그 회원의 사용자 카테고리에 있는지 확인한다.
     * 비활성 카테고리까지 함께 보는 이유는, 비활성 행이 남아 있는 이름을 다시 만들면
     * {@code uk_routine_category_member_name} 제약에 걸리기 때문이다.
     *
     * @param memberId 확인할 회원 ID
     * @param name 사용하려는 카테고리 이름
     * @return 같은 이름이 이미 있으면 {@code true}
     */
    @Query("""
            select count(category) > 0
            from RoutineCategory category
            left join category.owner owner
            where category.name = :name
              and (owner is null or owner.id = :memberId)
            """)
    boolean existsUsableName(@Param("memberId") Long memberId, @Param("name") String name);
}
