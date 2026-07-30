package com.lirouti.domain.routine.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.routine.converter.RoutineConverter;
import com.lirouti.domain.routine.dto.request.RoutineReqDTO;
import com.lirouti.domain.routine.dto.response.RoutineResDTO;
import com.lirouti.domain.routine.entity.MemberRoutine;
import com.lirouti.domain.routine.entity.RoutineCategory;
import com.lirouti.domain.routine.entity.RoutineTemplate;
import com.lirouti.domain.routine.exception.RoutineException;
import com.lirouti.domain.routine.exception.code.error.RoutineErrorCode;
import com.lirouti.domain.routine.repository.MemberRoutineRepository;
import com.lirouti.domain.routine.repository.RoutineCategoryRepository;
import com.lirouti.domain.routine.repository.RoutineTemplateRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineCommandService {
    private final MemberQueryService memberQueryService;
    private final RoutineCategoryRepository routineCategoryRepository;
    private final RoutineTemplateRepository routineTemplateRepository;
    private final MemberRoutineRepository memberRoutineRepository;

    /**
     * 루틴 추가 화면에서 선택·작성한 루틴들을 한 트랜잭션으로 등록한다.
     *
     * <p>활성 루틴 상한은 요청 전체를 기존 개수에 더해서 판단한다. 한 건씩 나눠 검사하면
     * 상한을 넘는 요청의 앞부분만 저장되는 어중간한 상태가 만들어진다.
     *
     * @param memberId 등록을 요청한 회원 ID
     * @param request 등록할 루틴 목록
     * @return 생성된 루틴 목록과 생성 후 활성 루틴 총 개수
     * @throws RoutineException 카테고리·기본 루틴이 없거나, 상한을 넘거나, 이미 등록한 기본 루틴인 경우
     */
    @Transactional
    public RoutineResDTO.RoutineCreateResult createRoutines(
            Long memberId,
            RoutineReqDTO.CreateRoutines request
    ) {
        validateRequest(request);

        Member member = memberQueryService.getActiveMember(memberId);
        List<RoutineReqDTO.CreateRoutine> items = request.routines();

        long existingCount = memberRoutineRepository.countByMemberIdAndActiveTrue(memberId);
        validateActiveLimit(memberId, existingCount, items.size());

        Map<Long, RoutineCategory> categories = loadUsableCategories(memberId, items);
        Map<Long, RoutineTemplate> templates = loadActiveTemplates(items);

        List<MemberRoutine> routines = buildRoutines(memberId, member, items, categories, templates);
        saveRoutines(memberId, routines);

        long activeCount = existingCount + routines.size();
        log.info("개인 루틴 생성을 완료했습니다. memberId={}, createdCount={}, activeCount={}",
                memberId, routines.size(), activeCount);

        return RoutineConverter.toRoutineCreateResult(routines, activeCount);
    }

    /**
     * 회원이 직접 쓰는 사용자 카테고리를 추가한다.
     *
     * @param memberId 추가를 요청한 회원 ID
     * @param request 카테고리 이름과 색상
     * @return 생성된 카테고리
     * @throws RoutineException 이름 규칙 위반, 개수 상한 초과, 이름 중복인 경우
     */
    @Transactional
    public RoutineResDTO.Category createCategory(
            Long memberId,
            RoutineReqDTO.CreateCategory request
    ) {
        if (request == null) {
            log.warn("카테고리 생성 요청이 비어 있습니다. memberId={}", memberId);
            throw new IllegalArgumentException("유효하지 않은 카테고리 생성 요청입니다.");
        }

        Member member = memberQueryService.getActiveMember(memberId);
        String name = normalizedCategoryName(memberId, request.name());

        long ownedCount = routineCategoryRepository.countByOwnerIdAndActiveTrue(memberId);
        if (ownedCount >= RoutineCategory.MAX_MEMBER_CATEGORY_COUNT) {
            log.warn("카테고리 개수 상한을 초과했습니다. memberId={}, ownedCount={}",
                    memberId, ownedCount);
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_LIMIT_EXCEEDED);
        }
        if (routineCategoryRepository.existsUsableName(memberId, name)) {
            log.warn("중복된 카테고리 이름을 차단했습니다. memberId={}, name={}", memberId, name);
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
        }

        RoutineCategory category = RoutineCategory.builder()
                .owner(member)
                .name(name)
                .color(request.color())
                .active(true)
                .build();
        saveCategory(memberId, category);

        log.info("사용자 루틴 카테고리를 생성했습니다. memberId={}, categoryId={}",
                memberId, category.getId());

        return RoutineConverter.toCategory(category);
    }

    /**
     * Controller 외의 호출 경로에서도 필수값을 방어적으로 검증한다.
     *
     * @param request 검증할 벌크 생성 요청
     * @throws IllegalArgumentException 요청이나 항목이 비어 있는 경우
     */
    private void validateRequest(RoutineReqDTO.CreateRoutines request) {
        if (request == null
                || request.routines() == null
                || request.routines().isEmpty()
                || request.routines().stream().anyMatch(Objects::isNull)) {
            log.warn("개인 루틴 생성 요청 검증에 실패했습니다.");
            throw new IllegalArgumentException("유효하지 않은 루틴 생성 요청입니다.");
        }
    }

    /**
     * 생성 후 활성 루틴이 상한을 넘지 않는지 검증한다.
     *
     * @param memberId 요청 회원 ID
     * @param existingCount 기존 활성 루틴 수
     * @param requestedCount 이번에 등록하려는 루틴 수
     * @throws RoutineException 합계가 {@link MemberRoutine#MAX_ACTIVE_COUNT}를 넘는 경우
     */
    private void validateActiveLimit(Long memberId, long existingCount, int requestedCount) {
        if (existingCount + requestedCount > MemberRoutine.MAX_ACTIVE_COUNT) {
            log.warn("활성 루틴 상한을 초과했습니다. memberId={}, existingCount={}, requestedCount={}",
                    memberId, existingCount, requestedCount);
            throw new RoutineException(RoutineErrorCode.ACTIVE_ROUTINE_LIMIT_EXCEEDED);
        }
    }

    /**
     * 요청에 등장하는 카테고리를 한 번에 조회하고, 회원이 쓸 수 있는지 검증한다.
     *
     * @param memberId 요청 회원 ID
     * @param items 등록할 루틴 목록
     * @return 카테고리 ID로 조회할 수 있는 검증된 카테고리 맵
     * @throws RoutineException 없거나 비활성인 카테고리, 다른 회원의 카테고리가 섞인 경우
     */
    private Map<Long, RoutineCategory> loadUsableCategories(
            Long memberId,
            List<RoutineReqDTO.CreateRoutine> items
    ) {
        Set<Long> categoryIds = items.stream()
                .map(RoutineReqDTO.CreateRoutine::categoryId)
                .collect(Collectors.toSet());

        Map<Long, RoutineCategory> categories = routineCategoryRepository.findAllById(categoryIds)
                .stream()
                .filter(category -> Boolean.TRUE.equals(category.getActive()))
                .collect(Collectors.toMap(RoutineCategory::getId, Function.identity()));

        if (categories.size() != categoryIds.size()) {
            log.warn("활성 루틴 카테고리 조회에 실패했습니다. memberId={}, requestedIds={}, foundIds={}",
                    memberId, categoryIds, categories.keySet());
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
        }
        categories.values().stream()
                .filter(category -> !category.isUsableBy(memberId))
                .findFirst()
                .ifPresent(category -> {
                    log.warn("다른 회원의 카테고리 사용을 차단했습니다. memberId={}, categoryId={}",
                            memberId, category.getId());
                    throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
                });
        return categories;
    }

    /**
     * 요청에 등장하는 기본 제공 루틴을 한 번에 조회한다.
     *
     * @param items 등록할 루틴 목록
     * @return 기본 루틴 ID로 조회할 수 있는 맵. 선택한 기본 루틴이 없으면 빈 맵
     * @throws RoutineException 없거나 비활성인 기본 루틴이 섞인 경우
     */
    private Map<Long, RoutineTemplate> loadActiveTemplates(
            List<RoutineReqDTO.CreateRoutine> items
    ) {
        Set<Long> templateIds = items.stream()
                .map(RoutineReqDTO.CreateRoutine::templateId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (templateIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, RoutineTemplate> templates = routineTemplateRepository
                .findAllActiveByIdIn(templateIds).stream()
                .collect(Collectors.toMap(RoutineTemplate::getId, Function.identity()));

        if (templates.size() != templateIds.size()) {
            log.warn("활성 기본 제공 루틴 조회에 실패했습니다. requestedIds={}, foundIds={}",
                    templateIds, templates.keySet());
            throw new RoutineException(RoutineErrorCode.ROUTINE_TEMPLATE_NOT_FOUND);
        }
        return templates;
    }

    /**
     * 검증된 카테고리·기본 루틴을 붙여 저장 대상 엔티티를 만든다.
     *
     * <p>기본 루틴을 골랐더라도 이름을 바꿨다면 원본 참조를 떼고 사용자 루틴으로 만든다.
     * 원본을 떼면 "이미 등록한 기본 루틴" 검사 대상에서도 빠진다 — 원본 선택이 해제된
     * 상태이므로 같은 기본 루틴을 다시 고를 수 있어야 한다.
     *
     * @param memberId 요청 회원 ID
     * @param member 소유 회원
     * @param items 등록할 루틴 목록
     * @param categories 검증된 카테고리 맵
     * @param templates 검증된 기본 루틴 맵
     * @return 요청 순서를 유지한 저장 대상 목록
     * @throws RoutineException 이름 규칙 위반, 카테고리 불일치, 기본 루틴 중복 등록인 경우
     */
    private List<MemberRoutine> buildRoutines(
            Long memberId,
            Member member,
            List<RoutineReqDTO.CreateRoutine> items,
            Map<Long, RoutineCategory> categories,
            Map<Long, RoutineTemplate> templates
    ) {
        Set<Long> takenTemplateIds =
                new HashSet<>(memberRoutineRepository.findTemplateIdsByMemberId(memberId));
        List<MemberRoutine> routines = new ArrayList<>(items.size());

        for (RoutineReqDTO.CreateRoutine item : items) {
            String name = normalizedRoutineName(memberId, item.name());
            RoutineCategory category = categories.get(item.categoryId());
            RoutineTemplate template = resolveTemplate(memberId, item, name, templates);

            if (template != null && !takenTemplateIds.add(template.getId())) {
                log.warn("이미 등록한 기본 제공 루틴입니다. memberId={}, templateId={}",
                        memberId, template.getId());
                throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_TEMPLATE);
            }
            routines.add(RoutineConverter.toMemberRoutine(item, member, category, template, name));
        }
        return routines;
    }

    /**
     * 항목이 실제로 참조할 기본 루틴을 결정한다.
     * 기본 루틴을 고르지 않았거나 이름을 바꿨으면 참조가 없는 사용자 루틴이 된다.
     *
     * @param memberId 요청 회원 ID
     * @param item 등록할 루틴 한 건
     * @param name 정규화된 루틴 이름
     * @param templates 검증된 기본 루틴 맵
     * @return 유지할 기본 루틴. 사용자 루틴이면 {@code null}
     * @throws RoutineException 기본 루틴이 요청한 카테고리에 속하지 않는 경우
     */
    private RoutineTemplate resolveTemplate(
            Long memberId,
            RoutineReqDTO.CreateRoutine item,
            String name,
            Map<Long, RoutineTemplate> templates
    ) {
        if (item.templateId() == null) {
            return null;
        }
        RoutineTemplate template = templates.get(item.templateId());
        if (!template.getCategory().getId().equals(item.categoryId())) {
            log.warn("기본 제공 루틴과 카테고리가 일치하지 않습니다. "
                            + "memberId={}, templateId={}, requestedCategoryId={}",
                    memberId, item.templateId(), item.categoryId());
            throw new RoutineException(RoutineErrorCode.ROUTINE_TEMPLATE_CATEGORY_MISMATCH);
        }
        return template.getName().equals(name) ? template : null;
    }

    /**
     * 루틴 이름을 앞뒤 공백을 제거한 형태로 정규화하고 길이를 검증한다.
     * Controller의 요청 검증과 별개로, 다른 호출 경로에서도 같은 규칙이 지켜지게 한다.
     *
     * @param memberId 요청 회원 ID
     * @param rawName 요청에 담긴 이름
     * @return 정규화된 이름
     * @throws RoutineException 비었거나 길이 규칙을 벗어난 경우
     */
    private String normalizedRoutineName(Long memberId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MemberRoutine.MAX_NAME_LENGTH) {
            log.warn("루틴 이름 검증에 실패했습니다. memberId={}, length={}", memberId, name.length());
            throw new RoutineException(RoutineErrorCode.INVALID_ROUTINE_NAME);
        }
        return name;
    }

    /**
     * 카테고리 이름을 앞뒤 공백을 제거한 형태로 정규화하고 길이를 검증한다.
     *
     * @param memberId 요청 회원 ID
     * @param rawName 요청에 담긴 이름
     * @return 정규화된 이름
     * @throws RoutineException 비었거나 길이 규칙을 벗어난 경우
     */
    private String normalizedCategoryName(Long memberId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > RoutineCategory.MAX_MEMBER_CATEGORY_NAME_LENGTH) {
            log.warn("카테고리 이름 검증에 실패했습니다. memberId={}, length={}", memberId, name.length());
            throw new RoutineException(RoutineErrorCode.INVALID_ROUTINE_CATEGORY_NAME);
        }
        return name;
    }

    /**
     * 루틴과 cascade로 연결된 반복 요일을 즉시 반영하고, 저장 중 무결성 오류를 도메인 예외로 바꾼다.
     * 애플리케이션 검증을 통과했는데도 걸린다면 같은 요청이 동시에 두 번 들어온 경우다.
     *
     * @param memberId 요청 회원 ID
     * @param routines 저장할 루틴 목록
     * @throws RoutineException 저장 과정에서 무결성 제약을 위반한 경우
     */
    private void saveRoutines(Long memberId, List<MemberRoutine> routines) {
        try {
            memberRoutineRepository.saveAll(routines);
            memberRoutineRepository.flush();
        } catch (DataIntegrityViolationException e) {
            log.warn("개인 루틴 저장 중 무결성 제약을 위반했습니다. memberId={}", memberId);
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_TEMPLATE);
        }
    }

    /**
     * 카테고리를 즉시 반영하고, 저장 중 무결성 오류를 도메인 예외로 바꾼다.
     *
     * @param memberId 요청 회원 ID
     * @param category 저장할 카테고리
     * @throws RoutineException 같은 이름이 동시에 등록된 경우
     */
    private void saveCategory(Long memberId, RoutineCategory category) {
        try {
            routineCategoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            log.warn("카테고리 저장 중 무결성 제약을 위반했습니다. memberId={}, name={}",
                    memberId, category.getName());
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
        }
    }
}
