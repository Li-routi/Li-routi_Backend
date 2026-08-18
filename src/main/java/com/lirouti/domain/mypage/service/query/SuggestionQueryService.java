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
     * 내 건의 목록.
     *
     * <p><b>{@code memberId} 는 인증 주체에서만 온다.</b> 컨트롤러가
     * {@code @AuthenticationPrincipal} 로 꺼내 넘기고, 요청 경로·쿼리·본문에서는 받지 않는다 —
     * 요청이 주는 값을 믿으면 남의 id 를 넣는 것만으로 남의 건의를 읽는다.
     *
     * <p><b>한 건을 더 읽어 다음 쪽이 있는지 본다.</b> 별도의 count 조회를 두면 목록과 개수가
     * 서로 다른 시점을 보게 되고, 그 사이에 하나가 들어오면 어긋난다.
     */
    @Transactional(readOnly = true)
    public SuggestionResDTO.Listing getMySuggestions(Long memberId, Long cursor, int size) {
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

        List<Suggestion> found =
                new ArrayList<>(suggestionRepository.findMine(memberId, cursor, Limit.of(size + 1)));

        boolean hasNext = found.size() > size;
        if (hasNext) {
            // 다음 쪽이 있는지 알려고 읽은 여분이다. 응답에는 싣지 않는다.
            found.remove(found.size() - 1);
        }
        return SuggestionConverter.toListing(found, hasNext);
    }
}
