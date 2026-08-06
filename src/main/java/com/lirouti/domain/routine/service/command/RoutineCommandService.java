package com.lirouti.domain.routine.service.command;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.repository.MemberRepository;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoutineCommandService {
    /** 같은 기본 제공 루틴을 두 번 고르지 못하게 하는 제약. V7 마이그레이션과 이름이 같아야 한다. */
    private static final String UK_MEMBER_ROUTINE_TEMPLATE = "uk_member_routine_member_template";

    /** 한 회원 안에서 카테고리 이름 중복을 막는 제약. V7 마이그레이션과 이름이 같아야 한다. */
    private static final String UK_ROUTINE_CATEGORY_MEMBER_NAME = "uk_routine_category_member_name";

    private final MemberQueryService memberQueryService;
    private final MemberRepository memberRepository;
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

        lockMember(memberId);
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

        lockMember(memberId);
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

    /** 인증 회원이 직접 만든 활성 카테고리의 이름과 색상을 수정한다. */
    @Transactional
    public RoutineResDTO.Category updateCategory(
            Long memberId,
            Long categoryId,
            RoutineReqDTO.UpdateCategory request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("유효하지 않은 카테고리 수정 요청입니다.");
        }

        lockMember(memberId);
        memberQueryService.getActiveMember(memberId);
        RoutineCategory category = findOwnedMutableCategory(memberId, categoryId);
        String name = normalizedCategoryName(memberId, request.name());

        if (!category.getName().equals(name)
                && routineCategoryRepository.existsUsableName(memberId, name)) {
            log.warn("중복된 카테고리 이름 수정을 차단했습니다. memberId={}, categoryId={}, name={}",
                    memberId, categoryId, name);
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
        }

        category.update(name, request.color());
        saveCategory(memberId, category);
        log.info("사용자 루틴 카테고리를 수정했습니다. memberId={}, categoryId={}",
                memberId, categoryId);
        return RoutineConverter.toCategory(category);
    }

    /** 개인 루틴이 전혀 참조하지 않는 본인 사용자 카테고리를 물리 삭제한다. */
    @Transactional
    public void deleteCategory(Long memberId, Long categoryId) {
        lockMember(memberId);
        memberQueryService.getActiveMember(memberId);
        RoutineCategory category = findOwnedMutableCategory(memberId, categoryId);

        if (memberRoutineRepository.existsByCategoryId(categoryId)) {
            log.warn("개인 루틴이 포함된 카테고리 삭제를 차단했습니다. memberId={}, categoryId={}",
                    memberId, categoryId);
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_NOT_EMPTY);
        }

        routineCategoryRepository.delete(category);
        routineCategoryRepository.flush();
        log.info("사용자 루틴 카테고리를 삭제했습니다. memberId={}, categoryId={}",
                memberId, categoryId);
    }

    /** 인증 회원이 소유한 활성 개인 루틴의 설정을 전체 교체한다. */
    @Transactional
    public RoutineResDTO.Routine updateRoutine(
            Long memberId,
            Long routineId,
            RoutineReqDTO.UpdateRoutine request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("유효하지 않은 루틴 수정 요청입니다.");
        }
        memberQueryService.getActiveMember(memberId);
        MemberRoutine routine = findOwnedActiveRoutine(memberId, routineId);
        String name = normalizedRoutineName(memberId, request.name());

        try {
            // 같은 요일을 유지하는 수정에서는 새 일정 INSERT가 기존 일정 DELETE보다 먼저
            // 실행되면 (member_routine_id, repeat_day) 유니크 키가 충돌한다.
            // orphanRemoval 삭제를 먼저 확정한 뒤 새 일정을 추가한다.
            routine.clearSchedules();
            memberRoutineRepository.flush();
            routine.update(name, request.endTime(), request.alarmTime(), request.repeatDays());
        } catch (IllegalArgumentException e) {
            log.warn("개인 루틴 수정 요청 검증에 실패했습니다. memberId={}, routineId={}",
                    memberId, routineId);
            throw new RoutineException(RoutineErrorCode.INVALID_ROUTINE_UPDATE);
        }

        memberRoutineRepository.flush();
        log.info("개인 루틴을 수정했습니다. memberId={}, routineId={}", memberId, routineId);
        return RoutineConverter.toRoutine(routine);
    }

    /**
     * 인증 회원이 소유한 개인 루틴을 소프트 삭제한다.
     * 수행 이력의 FK는 보존하고, 기본 루틴 참조는 해제해 같은 템플릿을 다시 등록할 수 있다.
     */
    @Transactional
    public void deleteRoutine(Long memberId, Long routineId) {
        memberQueryService.getActiveMember(memberId);
        MemberRoutine routine = findOwnedActiveRoutine(memberId, routineId);
        routine.deactivate();
        memberRoutineRepository.flush();
        log.info("개인 루틴을 삭제했습니다. memberId={}, routineId={}", memberId, routineId);
    }

    private MemberRoutine findOwnedActiveRoutine(Long memberId, Long routineId) {
        if (routineId == null) {
            throw new RoutineException(RoutineErrorCode.ROUTINE_NOT_FOUND);
        }
        return memberRoutineRepository.findByIdAndMemberIdAndActiveTrue(routineId, memberId)
                .orElseThrow(() -> {
                    log.warn("수정·삭제할 개인 루틴을 찾을 수 없습니다. memberId={}, routineId={}",
                            memberId, routineId);
                    return new RoutineException(RoutineErrorCode.ROUTINE_NOT_FOUND);
                });
    }

    private RoutineCategory findOwnedMutableCategory(Long memberId, Long categoryId) {
        if (categoryId == null) {
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND);
        }
        RoutineCategory category = routineCategoryRepository.findByIdAndActiveTrue(categoryId)
                .orElseThrow(() -> new RoutineException(
                        RoutineErrorCode.ROUTINE_CATEGORY_NOT_FOUND));
        if (category.isFixed()) {
            log.warn("고정 카테고리 변경을 차단했습니다. memberId={}, categoryId={}",
                    memberId, categoryId);
            throw new RoutineException(
                    RoutineErrorCode.FIXED_ROUTINE_CATEGORY_MODIFICATION_NOT_ALLOWED);
        }
        if (!category.isOwnedBy(memberId)) {
            log.warn("다른 회원의 카테고리 변경을 차단했습니다. memberId={}, categoryId={}",
                    memberId, categoryId);
            throw new RoutineException(RoutineErrorCode.ROUTINE_CATEGORY_ACCESS_DENIED);
        }
        return category;
    }

    /**
     * 개수 상한을 세는 시점부터 저장까지를 회원 단위로 직렬화하기 위해 회원 행에 쓰기 잠금을 건다.
     *
     * <p>활성 루틴 30개와 카테고리 5개 상한은 "지금 개수 + 이번 요청"으로 판단한다. 세는 것과
     * 저장하는 것이 원자적이지 않으므로, 같은 회원의 요청 둘이 겹치면(더블탭·재시도) 양쪽 모두
     * 상한 검사를 통과해 합계가 상한을 넘을 수 있다. 이 상한은 유니크 제약처럼 DB로 표현할 수
     * 있는 규칙이 아니라서, 잠금으로 구간을 직렬화하는 방법을 쓴다.
     *
     * <p>잠그는 대상이 회원 행이므로 다른 회원의 요청은 서로 막지 않는다. 회원 한 명이 자기
     * 루틴을 동시에 여러 번 만드는 일은 드물어 대기 비용도 사실상 없다.
     *
     * @param memberId 잠글 회원 ID
     */
    private void lockMember(Long memberId) {
        if (memberId == null) {
            log.warn("회원 ID 없이 루틴 생성이 호출됐습니다.");
            throw new IllegalArgumentException("회원 ID는 필수입니다.");
        }
        memberRepository.findByIdForUpdate(memberId);
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
     * 루틴 이름을 앞뒤 공백을 제거한 형태로 정규화하고 길이와 줄바꿈을 검증한다.
     * Controller의 요청 검증과 별개로, 다른 호출 경로에서도 같은 규칙이 지켜지게 한다.
     *
     * @param memberId 요청 회원 ID
     * @param rawName 요청에 담긴 이름
     * @return 정규화된 이름
     * @throws RoutineException 비었거나 길이 규칙을 벗어나거나 줄바꿈을 포함한 경우
     */
    private String normalizedRoutineName(Long memberId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.length() > MemberRoutine.MAX_NAME_LENGTH || hasLineBreak(name)) {
            log.warn("루틴 이름 검증에 실패했습니다. memberId={}, length={}", memberId, name.length());
            throw new RoutineException(RoutineErrorCode.INVALID_ROUTINE_NAME);
        }
        return name;
    }

    /**
     * 카테고리 이름을 앞뒤 공백을 제거한 형태로 정규화하고 길이와 줄바꿈을 검증한다.
     *
     * @param memberId 요청 회원 ID
     * @param rawName 요청에 담긴 이름
     * @return 정규화된 이름
     * @throws RoutineException 비었거나 길이 규칙을 벗어나거나 줄바꿈을 포함한 경우
     */
    private String normalizedCategoryName(Long memberId, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()
                || name.length() > RoutineCategory.MAX_MEMBER_CATEGORY_NAME_LENGTH
                || hasLineBreak(name)) {
            log.warn("카테고리 이름 검증에 실패했습니다. memberId={}, length={}", memberId, name.length());
            throw new RoutineException(RoutineErrorCode.INVALID_ROUTINE_CATEGORY_NAME);
        }
        return name;
    }

    /**
     * 이름에 줄바꿈이 들어 있는지 확인한다. 루틴과 카테고리 모두 목록에서 한 줄로 그려진다.
     *
     * <p>trim만으로는 걸러지지 않는다. 앞뒤 공백은 잘려도 가운데 줄바꿈은 남기 때문이다.
     *
     * @param name 정규화된 이름
     * @return 줄바꿈을 포함하면 {@code true}
     */
    private static boolean hasLineBreak(String name) {
        return name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0;
    }

    /**
     * 루틴과 cascade로 연결된 반복 요일을 즉시 반영하고, 기본 루틴 중복만 도메인 예외로 바꾼다.
     * 애플리케이션 검증을 통과했는데도 걸린다면 같은 요청이 동시에 두 번 들어온 경우다.
     *
     * @param memberId 요청 회원 ID
     * @param routines 저장할 루틴 목록
     * @throws RoutineException 같은 기본 루틴이 이미 등록되어 유니크 제약을 위반한 경우
     */
    private void saveRoutines(Long memberId, List<MemberRoutine> routines) {
        try {
            memberRoutineRepository.saveAll(routines);
            memberRoutineRepository.flush();
        } catch (DuplicateKeyException e) {
            if (!violates(e, UK_MEMBER_ROUTINE_TEMPLATE)) {
                throw e;
            }
            log.warn("이미 등록된 기본 제공 루틴을 동시에 저장하려 했습니다. memberId={}", memberId);
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_TEMPLATE);
        }
    }

    /**
     * 카테고리를 즉시 반영하고, 이름 중복만 도메인 예외로 바꾼다.
     *
     * @param memberId 요청 회원 ID
     * @param category 저장할 카테고리
     * @throws RoutineException 같은 이름이 동시에 등록되어 유니크 제약을 위반한 경우
     */
    private void saveCategory(Long memberId, RoutineCategory category) {
        try {
            routineCategoryRepository.saveAndFlush(category);
        } catch (DuplicateKeyException e) {
            if (!violates(e, UK_ROUTINE_CATEGORY_MEMBER_NAME)) {
                throw e;
            }
            log.warn("같은 이름의 카테고리를 동시에 저장하려 했습니다. memberId={}, name={}",
                    memberId, category.getName());
            throw new RoutineException(RoutineErrorCode.DUPLICATE_ROUTINE_CATEGORY_NAME);
        }
    }

    /**
     * 무결성 예외가 특정 유니크 제약 때문인지 확인한다.
     *
     * <p>제약 이름으로 판별하는 이유는, 한 저장 경로에서 걸릴 수 있는 제약이 여럿이기 때문이다.
     * 예를 들어 루틴 저장은 cascade로 반복 요일까지 함께 넣으므로
     * {@code uk_member_routine_schedule_day}에도 걸릴 수 있는데, 그것까지 "이미 등록한 기본
     * 제공 루틴입니다"로 응답하면 클라이언트가 엉뚱한 안내를 하게 된다. 해당 제약이 아니면
     * 원래 예외를 그대로 올려 전역 예외 처리기가 500으로 다루게 둔다 — 우리가 예상하지 못한
     * 무결성 위반은 사용자 입력 문제가 아니라 버그이므로 조용히 409로 덮으면 안 된다.
     *
     * <p>MySQL은 중복 키 메시지에 제약 이름을 담는다
     * ({@code Duplicate entry '...' for key 'member_routine.uk_...'}). 예외 원인 사슬 전체를
     * 훑는 것은 Spring이 드라이버 예외를 감싸면서 메시지를 다시 쓰기 때문이다.
     *
     * @param exception 저장 중 발생한 중복 키 예외
     * @param constraintName 확인할 유니크 제약 이름
     * @return 그 제약 위반이면 {@code true}
     */
    private static boolean violates(DuplicateKeyException exception, String constraintName) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains(constraintName)) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }
}
