package com.lirouti.domain.mypage.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.exception.MemberException;
import com.lirouti.domain.member.exception.code.error.MemberErrorCode;
import com.lirouti.domain.member.repository.MemberRepository;
import com.lirouti.domain.mypage.converter.SuggestionConverter;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.entity.Suggestion;
import com.lirouti.domain.mypage.entity.SuggestionCategory;
import com.lirouti.domain.mypage.exception.SuggestionException;
import com.lirouti.domain.mypage.exception.code.error.SuggestionErrorCode;
import com.lirouti.domain.mypage.repository.SuggestionCategoryRepository;
import com.lirouti.domain.mypage.repository.SuggestionRepository;
import com.lirouti.global.apiPayload.code.GeneralErrorCode;
import com.lirouti.global.apiPayload.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SuggestionCommandService {

    /** 요청 DTO 의 {@code @Size} 와 컬럼 길이에 맞춘다. 셋이 어긋나면 안 된다. */
    private static final int MAX_CONTENT_LENGTH = 2000;

    private final SuggestionRepository suggestionRepository;
    private final SuggestionCategoryRepository suggestionCategoryRepository;
    private final MemberRepository memberRepository;

    /**
     * 건의를 등록한다.
     *
     * <p><b>내려간 분류는 거절한다.</b> 목록에서 감추는 것과 등록을 막는 것은 다르다 — 분류 id 를
     * 아는 클라이언트는 목록을 거치지 않고 바로 등록을 부를 수 있다.
     *
     * <p><b>없는 분류와 내려간 분류를 가른다.</b> 아이템 구매와 달리 여기서는 감출 것이 없다.
     * 분류는 전원에게 같은 목록이라, 없는 id 를 넣어 봐도 새로 알게 되는 것이 없다.
     */
    @Transactional
    public SuggestionResDTO.Suggestion create(Long memberId, Long categoryId, String content) {
        // 요청 DTO 의 검증은 컨트롤러를 거칠 때만 있다. 서비스를 직접 부르는 경로가 생기면
        // 빈 본문이 그대로 저장되거나 상한 초과가 도메인 오류가 아닌 DB 오류로 나간다.
        if (memberId == null || categoryId == null) {
            throw new GeneralException(GeneralErrorCode.BAD_REQUEST);
        }
        if (content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH) {
            throw new SuggestionException(SuggestionErrorCode.INVALID_CONTENT);
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));

        SuggestionCategory category = suggestionCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new SuggestionException(SuggestionErrorCode.CATEGORY_NOT_FOUND));
        if (!category.isActive()) {
            throw new SuggestionException(SuggestionErrorCode.CATEGORY_NOT_ACTIVE);
        }

        Suggestion saved = suggestionRepository.save(
                SuggestionConverter.toEntity(member, category, content));
        return SuggestionConverter.toSuggestion(saved);
    }
}
