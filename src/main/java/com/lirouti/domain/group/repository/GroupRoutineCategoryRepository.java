package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupRoutineCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupRoutineCategoryRepository
        extends JpaRepository<GroupRoutineCategory, Long> {

    Optional<GroupRoutineCategory> findByIdAndActiveTrue(Long id);

    /** 기본 카테고리와 해당 그룹의 사용자 카테고리를 노출 순서대로 조회한다. */
    @Query("""
            select category
            from GroupRoutineCategory category
            left join category.group ownerGroup
            where category.active = true
              and (ownerGroup is null or ownerGroup.id = :groupId)
            order by case when ownerGroup is null then 0 else 1 end asc,
                     category.displayOrder asc,
                     category.id asc
            """)
    List<GroupRoutineCategory> findUsableByGroupId(@Param("groupId") Long groupId);

    long countByGroupIdAndActiveTrue(Long groupId);

    /** 고정 카테고리 또는 해당 그룹 카테고리에 같은 이름이 존재하는지 확인한다. */
    @Query("""
            select count(category) > 0
            from GroupRoutineCategory category
            left join category.group ownerGroup
            where category.name = :name
              and category.active = true
              and (ownerGroup is null or ownerGroup.id = :groupId)
            """)
    boolean existsUsableName(@Param("groupId") Long groupId, @Param("name") String name);
}
