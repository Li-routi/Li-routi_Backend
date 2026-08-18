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
    @DisplayName("탭은 전체·머리·옷·소품·캐릭터 다섯이고 그 순서로 나간다")
    void 탭_구성과_순서() {
        ShopResDTO.Categories result = ShopConverter.toCategories(List.of(ShopCategory.values()));

        assertThat(result.categories())
                .extracting(ShopResDTO.Category::key)
                .containsExactly(
                        ShopCategory.ALL,
                        ShopCategory.HEAD,
                        ShopCategory.BODY,
                        ShopCategory.HAND,
                        ShopCategory.CHARACTER
                );
        assertThat(result.categories())
                .extracting(ShopResDTO.Category::name)
                .containsExactly("전체", "머리", "옷", "소품", "캐릭터");
    }

    @Test
    @DisplayName("입력 순서가 뒤섞여도 sortOrder 로 정렬해 내린다")
    void 정렬은_서버가_한다() {
        ShopResDTO.Categories result = ShopConverter.toCategories(
                List.of(ShopCategory.CHARACTER, ShopCategory.HAND, ShopCategory.ALL));

        assertThat(result.categories())
                .extracting(ShopResDTO.Category::key)
                .containsExactly(ShopCategory.ALL, ShopCategory.HAND, ShopCategory.CHARACTER);
    }

    /**
     * 탭을 {@code AvatarSlot.values()} 로 만들면 이 테스트가 깨진다. 슬롯 셋에 슬롯이 아닌
     * 탭 둘(전체·캐릭터)이 더 있기 때문이다 — 무엇을 파는가와 무엇을 보여주는가는 같은
     * 속도로 바뀌지 않는다.
     */
    @Test
    @DisplayName("탭에는 슬롯이 아닌 것이 둘 섞여 있다")
    void 탭은_슬롯_목록이_아니다() {
        assertThat(ShopCategory.values()).hasSize(AvatarSlot.values().length + 2);

        assertThat(ShopCategory.values())
                .filteredOn(category -> category.getSlot() == null)
                .extracting(ShopCategory::name)
                .containsExactlyInAnyOrder("ALL", "CHARACTER");
    }

    /**
     * 이 둘을 {@code slot} 만 보고 가르려 하면 같은 값(비어 있음)이라 구분되지 않는다.
     * 클라이언트가 어느 API 를 부를지 정하는 근거가 {@code source} 인 이유다.
     */
    @Test
    @DisplayName("슬롯이 비어 있는 탭 둘은 source 로 갈린다")
    void 전체와_캐릭터는_source로_갈린다() {
        assertThat(ShopCategory.ALL.getSlot()).isNull();
        assertThat(ShopCategory.CHARACTER.getSlot()).isNull();

        assertThat(ShopCategory.ALL.getSource()).isEqualTo(ShopCategorySource.ITEM);
        assertThat(ShopCategory.CHARACTER.getSource()).isEqualTo(ShopCategorySource.CHARACTER);
    }

    @Test
    @DisplayName("아이템 탭은 슬롯을 갖고, 캐릭터 탭은 아이템 원천을 쓰지 않는다")
    void 원천과_슬롯이_짝을_이룬다() {
        assertThat(ShopCategory.HEAD.getSlot()).isEqualTo(AvatarSlot.HEAD);
        assertThat(ShopCategory.BODY.getSlot()).isEqualTo(AvatarSlot.BODY);
        assertThat(ShopCategory.HAND.getSlot()).isEqualTo(AvatarSlot.HAND);

        assertThat(ShopCategory.CHARACTER.getSource()).isNotEqualTo(ShopCategorySource.ITEM);
    }
}
