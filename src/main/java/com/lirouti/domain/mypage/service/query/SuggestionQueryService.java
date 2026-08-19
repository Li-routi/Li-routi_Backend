package com.lirouti.domain.mypage.service.query;

import com.lirouti.domain.mypage.converter.SuggestionConverter;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.entity.Suggestion;
import com.lirouti.domain.mypage.exception.SuggestionException;
import com.lirouti.domain.mypage.exception.code.error.SuggestionErrorCode;
import com.lirouti.domain.mypage.repository.SuggestionCategoryRepository;
import com.lirouti.domain.mypage.repository.SuggestionRepository;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestionQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 50;

    private final SuggestionRepository suggestionRepository;
    private final SuggestionCategoryRepository suggestionCategoryRepository;

    /** 고를 수 있는 분류. 내려간 것은 실리지 않는다. */
    @Transactional(readOnly = true)
    public SuggestionResDTO.Categories getCategories() {
        return SuggestionConverter.toCategories(
                suggestionCategoryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc());
    }

    /**
     * 내 건의 목록. <b>{@code keyword} 를 주면 제목으로 거른다.</b>
     *
     * <p><b>{@code memberId} 는 인증 주체에서만 온다.</b> 컨트롤러가
     * {@code @AuthenticationPrincipal} 로 꺼내 넘기고, 요청 경로·쿼리·본문에서는 받지 않는다 —
     * 요청이 주는 값을 믿으면 남의 id 를 넣는 것만으로 남의 건의를 읽는다.
     *
     * <p><b>한 건을 더 읽어 다음 쪽이 있는지 본다.</b> 별도의 count 조회를 두면 목록과 개수가
     * 서로 다른 시점을 보게 되고, 그 사이에 하나가 들어오면 어긋난다.
     *
     * <p><b>검색해도 커서 방식은 그대로다.</b> 같은 검색어를 유지한 채 {@code nextCursor} 를
     * 넘기면 다음 쪽이 온다 — 검색어를 바꾸면 커서를 버리고 처음부터 받아야 한다.
     */
    @Transactional(readOnly = true)
    public SuggestionResDTO.Listing getMySuggestions(Long memberId, Long cursor, int size,
                                                     String keyword) {
        // memberId 가 비면 조건이 아무것도 못 걸러 빈 목록이 정상처럼 나간다. 그것을 "건의가
        // 없다" 로 읽으면 격리가 깨진 것을 알아챌 기회가 사라진다.
        if (memberId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }

        // 상한이 없으면 큰 값을 넣는 것만으로 자기 건의 전부를 한 번에 끌어갈 수 있다.
        // 조용히 깎지 않고 거절한다 — 요청한 수와 받은 수가 다르면 그 이유를 알 수 없다.
        if (size < MIN_PAGE_SIZE || size > MAX_PAGE_SIZE) {
            throw new SuggestionException(SuggestionErrorCode.INVALID_PAGE_SIZE);
        }

        List<Suggestion> found = new ArrayList<>(suggestionRepository.findMine(
                memberId, cursor, toTitlePattern(keyword), Limit.of(size + 1)));

        boolean hasNext = found.size() > size;
        if (hasNext) {
            // 다음 쪽이 있는지 알려고 읽은 여분이다. 응답에는 싣지 않는다.
            found.remove(found.size() - 1);
        }
        return SuggestionConverter.toListing(found, hasNext);
    }

    /**
     * 검색어를 {@code like} 패턴으로 바꾼다. 검색하지 않으면 {@code null}.
     *
     * <p><b>공백만 보낸 것은 검색하지 않은 것으로 본다.</b> 빈 검색어를 그대로 패턴으로 만들면
     * {@code '%%'} 가 되어 전부 걸리는데, 그것은 "검색해서 다 나왔다" 와 "검색어가 없어서 다
     * 나왔다" 를 구별할 수 없게 만든다. 여기서 {@code null} 로 접어 두 경우를 하나로 합친다.
     *
     * <p><b>{@code %} 와 {@code _} 를 이스케이프한다.</b> 사용자가 제목에 실제로 쓴 {@code %} 를
     * 찾고 싶을 수 있는데, 그대로 넘기면 와일드카드가 되어 모든 건의가 걸린다. 이스케이프
     * 문자로 {@code !} 를 쓰므로 <b>{@code !} 자체를 먼저 이스케이프해야 한다</b> — 순서를
     * 바꾸면 앞서 넣은 이스케이프 문자까지 다시 이스케이프된다.
     *
     * <p>대소문자는 구별하지 않는다. 컬럼 콜레이션이 {@code utf8mb4_unicode_ci} 라 비교가
     * 이미 대소문자를 무시한다 — 여기서 {@code lower()} 를 부르면 같은 일을 두 번 한다.
     */
    private String toTitlePattern(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        String escaped = trimmed
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
