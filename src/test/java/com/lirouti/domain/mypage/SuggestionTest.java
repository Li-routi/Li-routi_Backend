package com.lirouti.domain.mypage;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.Role;
import com.lirouti.domain.member.enums.SocialProvider;
import com.lirouti.domain.mypage.dto.response.SuggestionResDTO;
import com.lirouti.domain.mypage.entity.SuggestionCategory;
import com.lirouti.domain.mypage.enums.SuggestionStatus;
import com.lirouti.domain.mypage.exception.SuggestionException;
import com.lirouti.domain.mypage.exception.code.error.SuggestionErrorCode;
import com.lirouti.domain.mypage.service.command.SuggestionCommandService;
import com.lirouti.domain.mypage.service.query.SuggestionQueryService;
import com.lirouti.global.apiPayload.exception.GeneralException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 마이페이지 건의.
 *
 * <p>여기서 지키는 것은 셋이다 — <b>남의 건의가 섞이지 않는다</b>, <b>내려간 분류로는 못
 * 보낸다</b>, <b>내려간 분류로 보낸 것은 계속 보인다</b>.
 */
@SpringBootTest
@Transactional
@DisplayName("건의")
class SuggestionTest {

    private static final long BUG = 1L;
    private static final long ETC = 4L;

    @Autowired private SuggestionQueryService suggestionQueryService;
    @Autowired private SuggestionCommandService suggestionCommandService;

    @PersistenceContext private EntityManager em;

    private final AtomicInteger seq = new AtomicInteger();

    private Member me;
    private Member other;

    @BeforeEach
    void setUp() {
        me = member("me");
        other = member("other");
        em.flush();
    }

    // ── 분류 ──

    @Test
    @DisplayName("분류는 노출 순서대로 나가고 내려간 것은 빠진다")
    void categories_AreOrderedAndExcludeInactive() {
        deactivate(ETC);

        SuggestionResDTO.Categories categories = suggestionQueryService.getCategories();

        assertAll(
                () -> assertThat(categories.categories())
                        .extracting(SuggestionResDTO.Category::code)
                        .as("시드 순서 그대로, 내려간 ETC 는 빠진다")
                        .containsExactly("BUG", "FEATURE", "INCONVENIENCE"),
                () -> assertThat(categories.categories())
                        .allSatisfy(category -> assertThat(category.name()).isNotBlank()));
    }

    // ── 등록 ──

    @Test
    @DisplayName("건의를 등록하면 접수 상태로 남는다")
    void create_StartsAsReceived() {
        SuggestionResDTO.Suggestion created =
                suggestionCommandService.create(me.getId(), BUG, "루틴 알림이 두 번 옵니다");

        assertAll(
                () -> assertThat(created.id()).isNotNull(),
                () -> assertThat(created.category().code()).isEqualTo("BUG"),
                () -> assertThat(created.content()).isEqualTo("루틴 알림이 두 번 옵니다"),
                () -> assertThat(created.status())
                        .as("등록은 언제나 접수에서 시작한다")
                        .isEqualTo(SuggestionStatus.RECEIVED));
    }

    @Test
    @DisplayName("없는 분류로는 등록할 수 없다")
    void create_RejectsMissingCategory() {
        assertThatThrownBy(() -> suggestionCommandService.create(me.getId(), 9_999L, "내용"))
                .isInstanceOf(SuggestionException.class)
                .satisfies(e -> assertThat(((SuggestionException) e).getCode())
                        .isEqualTo(SuggestionErrorCode.CATEGORY_NOT_FOUND));
    }

    /** 목록에서 감추는 것과 등록을 막는 것은 다르다. id 를 아는 클라이언트는 목록을 건너뛴다. */
    @Test
    @DisplayName("내려간 분류로는 등록할 수 없다")
    void create_RejectsInactiveCategory() {
        deactivate(ETC);

        assertThatThrownBy(() -> suggestionCommandService.create(me.getId(), ETC, "내용"))
                .isInstanceOf(SuggestionException.class)
                .satisfies(e -> assertThat(((SuggestionException) e).getCode())
                        .isEqualTo(SuggestionErrorCode.CATEGORY_NOT_ACTIVE));
    }

    /**
     * <b>요청 DTO 의 검증은 컨트롤러를 거칠 때만 있다.</b> 서비스를 직접 부르는 경로가 생기면
     * 빈 본문이 그대로 저장되거나 상한 초과가 도메인 오류가 아닌 DB 오류로 나간다.
     */
    @Test
    @DisplayName("서비스를 직접 불러도 빈 본문과 상한 초과는 거절한다")
    void create_ValidatesContentAtServiceEntry() {
        String tooLong = "가".repeat(2001);

        assertAll(
                () -> assertThatThrownBy(() -> suggestionCommandService.create(me.getId(), BUG, "  "))
                        .isInstanceOf(SuggestionException.class)
                        .satisfies(e -> assertThat(((SuggestionException) e).getCode())
                                .isEqualTo(SuggestionErrorCode.INVALID_CONTENT)),
                () -> assertThatThrownBy(() -> suggestionCommandService.create(me.getId(), BUG, tooLong))
                        .isInstanceOf(SuggestionException.class)
                        .satisfies(e -> assertThat(((SuggestionException) e).getCode())
                                .isEqualTo(SuggestionErrorCode.INVALID_CONTENT)),
                () -> assertThatThrownBy(() -> suggestionCommandService.create(me.getId(), null, "내용"))
                        .isInstanceOf(GeneralException.class));
    }

    // ── 목록 ──

    /**
     * <b>이 규칙은 코드를 읽어서는 깨진 것을 알아채기 어렵다.</b> 조회에 회원 조건이 빠져도
     * 화면은 그럴듯하게 뜨고, 남의 건의가 섞여 있다는 것만 다르다.
     */
    @Test
    @DisplayName("남이 보낸 건의는 내 목록에 섞이지 않는다")
    void list_IsIsolatedPerMember() {
        suggestionCommandService.create(me.getId(), BUG, "내 건의");
        suggestionCommandService.create(other.getId(), BUG, "남의 건의");
        em.flush();
        em.clear();

        SuggestionResDTO.Listing mine =
                suggestionQueryService.getMySuggestions(me.getId(), null, 20);

        assertThat(mine.suggestions()).extracting(SuggestionResDTO.Suggestion::content)
                .containsExactly("내 건의");
    }

    @Test
    @DisplayName("최신순으로 나가고 커서로 이어 받는다")
    void list_IsNewestFirstAndPaged() {
        for (int i = 1; i <= 5; i++) {
            suggestionCommandService.create(me.getId(), BUG, "건의 " + i);
        }
        em.flush();
        em.clear();

        SuggestionResDTO.Listing first =
                suggestionQueryService.getMySuggestions(me.getId(), null, 2);
        SuggestionResDTO.Listing second =
                suggestionQueryService.getMySuggestions(me.getId(), first.nextCursor(), 2);

        assertAll(
                () -> assertThat(first.suggestions())
                        .extracting(SuggestionResDTO.Suggestion::content)
                        .as("최신순").containsExactly("건의 5", "건의 4"),
                () -> assertThat(first.hasNext()).isTrue(),
                () -> assertThat(first.nextCursor())
                        .as("마지막 항목의 id 를 커서로 준다")
                        .isEqualTo(first.suggestions().get(1).id()),
                () -> assertThat(second.suggestions())
                        .extracting(SuggestionResDTO.Suggestion::content)
                        .as("여분은 응답에 실리지 않는다").containsExactly("건의 3", "건의 2"));
    }

    /** 비면 조건이 아무것도 못 걸러 빈 목록이 정상처럼 나간다 — 격리가 깨진 것을 못 알아챈다. */
    @Test
    @DisplayName("memberId 없이 부르면 빈 목록이 아니라 거절이다")
    void list_RejectsMissingMember() {
        assertThatThrownBy(() -> suggestionQueryService.getMySuggestions(null, null, 20))
                .isInstanceOf(GeneralException.class);
    }

    @Test
    @DisplayName("size 가 범위를 벗어나면 거절한다")
    void list_RejectsOutOfRangeSize() {
        assertAll(
                () -> assertThatThrownBy(
                        () -> suggestionQueryService.getMySuggestions(me.getId(), null, 0))
                        .isInstanceOf(SuggestionException.class),
                () -> assertThatThrownBy(
                        () -> suggestionQueryService.getMySuggestions(me.getId(), null, 51))
                        .isInstanceOf(SuggestionException.class));
    }

    @Test
    @DisplayName("마지막 쪽에서는 커서가 비고 다음이 없다고 알린다")
    void list_LastPageHasNoCursor() {
        suggestionCommandService.create(me.getId(), BUG, "하나뿐");
        em.flush();
        em.clear();

        SuggestionResDTO.Listing listing =
                suggestionQueryService.getMySuggestions(me.getId(), null, 20);

        assertAll(
                () -> assertThat(listing.hasNext()).isFalse(),
                () -> assertThat(listing.nextCursor()).isNull());
    }

    /** 고를 수 없게 하는 것과 이미 보낸 것을 감추는 것은 다르다. */
    @Test
    @DisplayName("분류가 내려가도 그 분류로 보낸 건의는 계속 보인다")
    void list_KeepsSuggestionsOfInactiveCategory() {
        suggestionCommandService.create(me.getId(), ETC, "기타 건의");
        em.flush();
        deactivate(ETC);
        em.clear();

        SuggestionResDTO.Listing listing =
                suggestionQueryService.getMySuggestions(me.getId(), null, 20);

        assertAll(
                () -> assertThat(listing.suggestions()).hasSize(1),
                () -> assertThat(listing.suggestions().get(0).category().name())
                        .as("분류 이름도 그대로 나간다").isEqualTo("기타"));
    }

    // ── 픽스처 ──

    private Member member(String prefix) {
        int n = seq.incrementAndGet();
        Member member = Member.builder()
                .email(prefix + n + "@ex.com").nickname(prefix + n)
                .socialProvider(SocialProvider.GOOGLE).role(Role.ROLE_USER)
                .socialId(prefix + "-sid-" + n).build();
        em.persist(member);
        return member;
    }

    private void deactivate(long categoryId) {
        em.createQuery("update SuggestionCategory c set c.active = false where c.id = :id")
                .setParameter("id", categoryId).executeUpdate();
        em.clear();
    }
}
