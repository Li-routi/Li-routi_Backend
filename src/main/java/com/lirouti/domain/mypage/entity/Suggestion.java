package com.lirouti.domain.mypage.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.mypage.enums.SuggestionStatus;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 보낸 건의.
 *
 * <p>수정·삭제를 두지 않는다. 보낸 것을 되돌리는 화면이 없고, 운영이 읽은 뒤에 내용이 바뀌면
 * 무엇을 보고 처리했는지 알 수 없다.
 */
@Entity
@Getter
@Table(name = "suggestion")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Suggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "suggestion_category_id", nullable = false)
    private SuggestionCategory category;

    /**
     * 제목. <b>목록에서 이것 하나로 무슨 건의인지 알아볼 수 있어야 한다</b> — 본문 앞부분을
     * 잘라 보여주면 "안녕하세요" 로 시작하는 건의가 전부 같아 보인다.
     *
     * <p>검색이 이 컬럼을 본다. 본문까지 뒤지지 않는 것은, 본문 검색은 긴 글 안의 아무 단어나
     * 걸려 목록이 뭉개지기 때문이다 — 제목은 사용자가 그 건의를 부르려고 지은 이름이다.
     */
    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 2000)
    private String content;

    /**
     * 처리 상태.
     *
     * <p><b>{@code EnumType.STRING} 이다.</b> 순서로 저장하면 enum 상수를 사이에 끼워 넣는 순간
     * 이미 저장된 행의 뜻이 통째로 어긋난다.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SuggestionStatus status;

    @Builder
    private Suggestion(Member member, SuggestionCategory category, String title, String content) {
        this.member = member;
        this.category = category;
        this.title = title;
        this.content = content;
        // 상태를 밖에서 받지 않는다. 등록은 언제나 접수에서 시작한다.
        this.status = SuggestionStatus.RECEIVED;
    }
}
