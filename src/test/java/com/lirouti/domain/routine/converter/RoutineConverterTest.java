package com.lirouti.domain.routine.converter;

import com.lirouti.domain.routine.cache.RoutineTemplateCacheReader.CachedTemplate;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@DisplayName("RoutineConverter 테스트")
class RoutineConverterTest {

    @Test
    @DisplayName("캐시된 공통 필드에 회원별 추가 상태를 붙이고 노출 순서를 유지한다")
    void toTemplateListFromCache_CombinesMemberStateInCacheOrder() {
        List<CachedTemplate> templates = List.of(
                new CachedTemplate(11L, 1L, "운동", "아침 스트레칭"),
                new CachedTemplate(21L, 2L, "건강", "물 마시기"),
                new CachedTemplate(22L, 2L, "건강", "영양제 먹기")
        );

        RoutineResDTO.TemplateList result = RoutineConverter.toTemplateListFromCache(
                templates,
                Set.of(21L)
        );

        assertThat(result.templates())
                .extracting(
                        RoutineResDTO.Template::templateId,
                        RoutineResDTO.Template::categoryId,
                        RoutineResDTO.Template::categoryName,
                        RoutineResDTO.Template::name,
                        RoutineResDTO.Template::alreadyAdded
                )
                .containsExactly(
                        tuple(11L, 1L, "운동", "아침 스트레칭", false),
                        tuple(21L, 2L, "건강", "물 마시기", true),
                        tuple(22L, 2L, "건강", "영양제 먹기", false)
                );
    }

    @Test
    @DisplayName("캐시 목록이 비어 있으면 빈 응답 목록을 반환한다")
    void toTemplateListFromCache_EmptyCache_ReturnsEmptyList() {
        RoutineResDTO.TemplateList result = RoutineConverter.toTemplateListFromCache(
                List.of(),
                Set.of(11L)
        );

        assertThat(result.templates()).isEmpty();
    }
}
