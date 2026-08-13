package com.lirouti.domain.routine.cache;

import com.lirouti.domain.routine.cache.RoutineTemplateCacheReader.CachedTemplate;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import com.lirouti.global.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RoutineTemplateCacheReader 테스트")
class RoutineTemplateCacheReaderTest {

    @Test
    @DisplayName("활성 템플릿의 공통 필드만 Repository 순서대로 캐싱한다")
    void getAll_CachesCommonFieldsInRepositoryOrder() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            RoutineTemplateRepository repository =
                    context.getBean(RoutineTemplateRepository.class);
            RoutineTemplateCacheReader cacheReader =
                    context.getBean(RoutineTemplateCacheReader.class);
            CacheManager cacheManager = context.getBean(CacheManager.class);
            RoutineCategory exercise = category(1L, "운동");
            RoutineCategory health = category(2L, "건강");
            when(repository.findAllActiveWithCategory()).thenReturn(List.of(
                    template(11L, exercise, "아침 스트레칭"),
                    template(21L, health, "물 마시기")
            ));

            List<CachedTemplate> first = cacheReader.getAll();
            List<CachedTemplate> second = cacheReader.getAll();

            assertThat(first).containsExactly(
                    new CachedTemplate(11L, 1L, "운동", "아침 스트레칭"),
                    new CachedTemplate(21L, 2L, "건강", "물 마시기")
            );
            assertThat(second).isEqualTo(first);
            assertThat(cacheManager.getCache(RedisConfig.ROUTINE_TEMPLATES_CACHE))
                    .isNotNull()
                    .extracting(cache -> cache.get("all", List.class))
                    .isEqualTo(first);
            verify(repository).findAllActiveWithCategory();
        }
    }

    @Test
    @DisplayName("빈 템플릿 목록도 정상 결과로 캐싱한다")
    void getAll_CachesEmptyList() {
        try (AnnotationConfigApplicationContext context = createContext()) {
            RoutineTemplateRepository repository =
                    context.getBean(RoutineTemplateRepository.class);
            RoutineTemplateCacheReader cacheReader =
                    context.getBean(RoutineTemplateCacheReader.class);
            when(repository.findAllActiveWithCategory()).thenReturn(List.of());

            assertThat(cacheReader.getAll()).isEmpty();
            assertThat(cacheReader.getAll()).isEmpty();

            verify(repository).findAllActiveWithCategory();
        }
    }

    @Test
    @DisplayName("캐시 모델은 공통 필드 네 개만 가진다")
    void cachedTemplate_ContainsOnlyCommonFields() {
        assertThat(CachedTemplate.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("templateId", "categoryId", "categoryName", "name");
    }

    @Test
    @DisplayName("현재 Redis serializer가 캐시 모델 목록과 빈 목록을 왕복 보존한다")
    void cachedTemplate_RoundTripsWithRedisSerializer() {
        RedisSerializer<Object> serializer = new RedisConfig().redisSerializer();
        // JDK 불변 List는 현재 generic serializer가 root type 정보를 기록하지 않아 복원할 수 없다.
        List<CachedTemplate> templates = new ArrayList<>(List.of(
                new CachedTemplate(11L, 1L, "운동", "아침 스트레칭"),
                new CachedTemplate(21L, 2L, "건강", "물 마시기")
        ));

        assertThat(roundTrip(serializer, templates)).isEqualTo(templates);
        assertThat(roundTrip(serializer, new ArrayList<>())).isEqualTo(List.of());
    }

    private AnnotationConfigApplicationContext createContext() {
        return new AnnotationConfigApplicationContext(CacheTestConfig.class);
    }

    private Object roundTrip(RedisSerializer<Object> serializer, Object value) {
        byte[] serialized = serializer.serialize(value);
        return serializer.deserialize(serialized);
    }

    private RoutineCategory category(Long id, String name) {
        RoutineCategory category = RoutineCategory.builder()
                .name(name)
                .active(true)
                .build();
        ReflectionTestUtils.setField(category, "id", id);
        return category;
    }

    private RoutineTemplate template(Long id, RoutineCategory category, String name) {
        RoutineTemplate template = RoutineTemplate.builder()
                .category(category)
                .name(name)
                .active(true)
                .build();
        ReflectionTestUtils.setField(template, "id", id);
        return template;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(RedisConfig.ROUTINE_TEMPLATES_CACHE);
        }

        @Bean
        RoutineTemplateRepository routineTemplateRepository() {
            return mock(RoutineTemplateRepository.class);
        }

        @Bean
        RoutineTemplateCacheReader routineTemplateCacheReader(
                RoutineTemplateRepository routineTemplateRepository
        ) {
            return new RoutineTemplateCacheReader(routineTemplateRepository);
        }
    }
}
