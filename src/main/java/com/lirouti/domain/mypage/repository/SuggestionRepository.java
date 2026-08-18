package com.lirouti.domain.mypage.repository;

import com.lirouti.domain.mypage.entity.Suggestion;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuggestionRepository extends JpaRepository<Suggestion, Long> {

    /**
     * 내 건의를 최신순으로. <b>커서는 {@code id} 다.</b>
     *
     * <p>{@code createdAt} 을 쓰지 않는 이유는 같은 초에 둘이 들어오면 순서가 흔들리기
     * 때문이다. {@code id DESC} 가 곧 최신순이다.
     *
     * <p><b>분류를 함께 읽는다.</b> 목록이 분류 이름을 보여주는데 지연 로딩으로 두면 건의 수만큼
     * 조회가 나간다.
     *
     * <p><b>분류의 {@code active} 로 거르지 않는다.</b> 분류가 내려갔다고 이미 보낸 건의가
     * 목록에서 사라지면 안 된다 — 고를 수 없게 하는 것과 보여주지 않는 것은 다르다.
     *
     * @param cursor 이 id 보다 작은 것만. 첫 요청이면 {@code null}
     */
    @Query("""
            select suggestion
            from Suggestion suggestion
            join fetch suggestion.category
            where suggestion.member.id = :memberId
              and (:cursor is null or suggestion.id < :cursor)
            order by suggestion.id desc
            """)
    List<Suggestion> findMine(@Param("memberId") Long memberId,
                              @Param("cursor") Long cursor,
                              Limit limit);
}
