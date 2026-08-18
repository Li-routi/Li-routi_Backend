package com.lirouti.domain.routine.cache;

import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import com.lirouti.global.config.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RoutineTemplateCacheReader {
    private static final String ALL_TEMPLATES_KEY = "all";

    private final RoutineTemplateRepository routineTemplateRepository;

    /**
     * 활성 기본 루틴의 공통 필드만 Repository 정렬 순서대로 조회한다.
     * 회원별 추가 상태는 포함하지 않는다.
     */
    @Cacheable(
            cacheNames = RedisConfig.ROUTINE_TEMPLATES_CACHE,
            key = "'" + ALL_TEMPLATES_KEY + "'"
    )
    public List<CachedTemplate> getAll() {
        // ArrayList의 root type 정보가 있어야 현재 generic serializer가 목록을 복원할 수 있다.
        return routineTemplateRepository.findAllActiveWithCategory().stream()
                .map(RoutineTemplateCacheReader::toCachedTemplate)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static CachedTemplate toCachedTemplate(RoutineTemplate template) {
        return new CachedTemplate(
                template.getId(),
                template.getCategory().getId(),
                template.getCategory().getName(),
                template.getName()
        );
    }

    /**
     * Redis에 저장하는 기본 루틴 공통 읽기 모델이다.
     * 필드 호환성이 깨지면 routineTemplates cache version을 함께 올린다.
     */
    public record CachedTemplate(
            Long templateId,
            Long categoryId,
            String categoryName,
            String name
    ) {
    }
}
