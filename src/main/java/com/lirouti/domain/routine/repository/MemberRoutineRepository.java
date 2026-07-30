package com.lirouti.domain.routine.repository;

import com.lirouti.domain.routine.entity.MemberRoutine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRoutineRepository extends JpaRepository<MemberRoutine, Long> {
    /**
     * 회원의 활성 개인 루틴 수를 센다. 활성 루틴 상한 검사에 사용한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 활성 개인 루틴 수
     */
    long countByMemberIdAndActiveTrue(Long memberId);

    /**
     * 회원이 이미 고른 기본 제공 루틴의 ID를 조회한다. 같은 기본 루틴을 두 번 등록하려는
     * 요청을 DB 제약 위반 전에 걸러내고, 목록 화면에서 어떤 항목이 이미 체크된 상태인지
     * 알려 주는 데 쓴다.
     *
     * <p>활성 여부를 보지 않는다. {@code uk_member_routine_member_template} 제약이 활성
     * 여부와 무관하게 걸리므로, 비활성 루틴이 남아 있는 템플릿도 중복으로 취급해야 한다.
     *
     * @param memberId 조회할 회원 ID
     * @return 이미 등록된 기본 제공 루틴 ID 목록
     */
    @Query("""
            select routine.template.id
            from MemberRoutine routine
            where routine.member.id = :memberId
              and routine.template is not null
            """)
    List<Long> findTemplateIdsByMemberId(@Param("memberId") Long memberId);
}
