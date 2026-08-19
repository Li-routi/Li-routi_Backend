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
     * <p><b>검색은 제목만 본다.</b> 본문까지 뒤지면 긴 글 안의 아무 단어나 걸려 목록이 뭉개진다.
     *
     * <p>{@code like '%...%'} 라 인덱스를 타지 못한다. 그래도 괜찮은 것은 <b>앞의
     * {@code member_id} 조건이 이미 범위를 자기 건의로 줄여 두기 때문</b>이다 — 표 전체를 훑는
     * 검색이 아니다. 건의가 한 사람 앞에 수천 건씩 쌓이는 종류의 데이터가 되면 그때 다시 본다.
     *
     * <p><b>패턴은 부르는 쪽이 이스케이프해서 넘긴다.</b> 사용자가 {@code %} 를 넣으면 그것이
     * 와일드카드로 동작해 전부 걸리고, {@code _} 는 아무 글자 하나와 맞는다 — 검색어를 그대로
     * 이어 붙이면 "찾은 것" 과 "안 거른 것" 을 구별할 수 없다.
     *
     * <p><b>분류 조건은 {@code active} 를 보지 않는다.</b> 내려간 분류로 이미 보낸 건의도 그
     * 분류로 찾을 수 있어야 한다 — 고를 수 없게 하는 것과 찾을 수 없게 하는 것은 다르다.
     *
     * <p>제목과 분류를 함께 주면 <b>둘 다 만족하는 것만</b> 나온다.
     *
     * @param cursor       이 id 보다 작은 것만. 첫 요청이면 {@code null}
     * @param titlePattern 이스케이프까지 끝난 {@code like} 패턴. 제목으로 거르지 않으면 {@code null}
     * @param categoryId   이 분류의 것만. 분류로 거르지 않으면 {@code null}
     */
    @Query("""
            select suggestion
            from Suggestion suggestion
            join fetch suggestion.category
            where suggestion.member.id = :memberId
              and (:cursor is null or suggestion.id < :cursor)
              and (:titlePattern is null or suggestion.title like :titlePattern escape '!')
              and (:categoryId is null or suggestion.category.id = :categoryId)
            order by suggestion.id desc
            """)
    List<Suggestion> findMine(@Param("memberId") Long memberId,
                              @Param("cursor") Long cursor,
                              @Param("titlePattern") String titlePattern,
                              @Param("categoryId") Long categoryId,
                              Limit limit);
}
