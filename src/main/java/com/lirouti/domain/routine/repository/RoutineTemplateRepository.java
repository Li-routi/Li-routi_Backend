package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.RoutineTemplate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoutineTemplateRepository extends JpaRepository<RoutineTemplate, Long> {
    /**
     * 한 카테고리의 기본 제공 루틴을 노출 순서대로 조회한다.
     *
     * @param categoryId 카테고리 ID
     * @return 활성 기본 제공 루틴 목록
     */
    List<RoutineTemplate> findByCategoryIdAndActiveTrueOrderByDisplayOrderAscIdAsc(Long categoryId);

    /**
     * 모든 카테고리의 기본 제공 루틴을 "카테고리 순서 → 카테고리 내 순서"로 조회한다.
     * 루틴 추가 화면의 `전체` 탭에 쓴다.
     *
     * <p>카테고리를 함께 가져오는 이유는 응답이 카테고리 이름을 함께 내려 주기 때문이다.
     * fetch join이 없으면 템플릿 수만큼 카테고리 조회가 따라붙는다.
     *
     * @return 활성 기본 제공 루틴 목록
     */
    @Query("""
            select template
            from RoutineTemplate template
            join fetch template.category category
            where template.active = true and category.active = true
            order by category.displayOrder asc,
                     category.id asc,
                     template.displayOrder asc,
                     template.id asc
            """)
    List<RoutineTemplate> findAllActiveWithCategory();

    /**
     * 요청으로 받은 기본 루틴 ID들을 카테고리와 함께 한 번에 조회한다.
     * 벌크 생성에서 템플릿마다 조회가 반복되지 않게 한다.
     *
     * @param ids 조회할 기본 루틴 ID 목록
     * @return 활성 기본 제공 루틴 목록. 없는 ID는 결과에 포함되지 않는다
     */
    @Query("""
            select template
            from RoutineTemplate template
            join fetch template.category
            where template.id in :ids and template.active = true
            """)
    List<RoutineTemplate> findAllActiveByIdIn(@Param("ids") Collection<Long> ids);
}
