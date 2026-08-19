package com.lirouti.domain.shop;

import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.enums.ShopCategory;
import com.lirouti.domain.shop.enums.ShopCategorySource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 상점 탭 목록.
 *
 * <p>탭은 데이터가 아니라 화면 구성이라 DB 를 타지 않는다. 그래서 여기서 못 박는 것은
 * <b>무엇이 탭이고 무엇이 아닌가</b>이다.
 */
class ShopCategoryTest {

    @Test
    @DisplayName("탭은 전체·머리·옷·소품 넷이고 그 순서로 나간다")
    void 탭_구성과_순서() {
        ShopResDTO.Categories result = ShopConverter.toCategories(List.of(ShopCategory.values()));

        assertThat(result.categories())
                .extracting(ShopResDTO.Category::key)
                .containsExactly(
                        ShopCategory.ALL,
                        ShopCategory.HEAD,
                        ShopCategory.BODY,
                        ShopCategory.HAND
                );
        assertThat(result.categories())
                .extracting(ShopResDTO.Category::name)
                .containsExactly("전체", "머리", "옷", "소품");
    }

    @Test
    @DisplayName("입력 순서가 뒤섞여도 sortOrder 로 정렬해 내린다")
    void 정렬은_서버가_한다() {
        ShopResDTO.Categories result = ShopConverter.toCategories(
                List.of(ShopCategory.HAND, ShopCategory.ALL, ShopCategory.HEAD));

        assertThat(result.categories())
                .extracting(ShopResDTO.Category::key)
                .containsExactly(ShopCategory.ALL, ShopCategory.HEAD, ShopCategory.HAND);
    }

    /**
     * <b>상점은 파는 것만 늘어놓는다.</b> 캐릭터는 돈이 아니라 업적으로 열리므로 값이 붙지
     * 않는데, 같은 격자에 섞이면 잠긴 캐릭터를 눌렀을 때 상점이 "얼마" 가 아니라 "무엇을 해야
     * 하는가" 를 답해야 한다. 그 화면은 {@code GET /api/characters} 가 맡는다.
     *
     * <p>탭을 {@code ShopCategory} 에 다시 넣는 순간 이 테스트가 깨진다.
     */
    @Test
    @DisplayName("캐릭터는 상점 탭이 아니다")
    void 캐릭터는_상점_탭이_아니다() {
        assertThat(ShopCategory.values())
                .extracting(ShopCategory::name)
                .doesNotContain("CHARACTER");

        assertThat(ShopCategory.values())
                .allSatisfy(category -> assertThat(category.getSource())
                        .as("상점 탭은 전부 아이템을 가져온다")
                        .isEqualTo(ShopCategorySource.ITEM));
    }

    /**
     * 탭을 {@code AvatarSlot.values()} 로 만들면 이 테스트가 깨진다. 슬롯 셋에 슬롯이 아닌
     * 탭 하나(전체)가 더 있기 때문이다 — 무엇을 파는가와 무엇을 보여주는가는 같은 속도로
     * 바뀌지 않는다.
     */
    @Test
    @DisplayName("탭에는 슬롯이 아닌 것이 하나 섞여 있다")
    void 탭은_슬롯_목록이_아니다() {
        assertThat(ShopCategory.values()).hasSize(AvatarSlot.values().length + 1);

        assertThat(ShopCategory.values())
                .filteredOn(category -> category.getSlot() == null)
                .extracting(ShopCategory::name)
                .containsExactly("ALL");
    }

    @Test
    @DisplayName("아이템 탭은 저마다 짝이 되는 슬롯을 갖는다")
    void 원천과_슬롯이_짝을_이룬다() {
        assertThat(ShopCategory.HEAD.getSlot()).isEqualTo(AvatarSlot.HEAD);
        assertThat(ShopCategory.BODY.getSlot()).isEqualTo(AvatarSlot.BODY);
        assertThat(ShopCategory.HAND.getSlot()).isEqualTo(AvatarSlot.HAND);

        assertThat(ShopCategory.ALL.getSlot())
                .as("전체는 필터가 없다는 뜻이라 슬롯이 없다")
                .isNull();
    }
}
