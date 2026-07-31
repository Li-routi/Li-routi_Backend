package com.lirouti.domain.group.validation;

import com.lirouti.domain.group.dto.request.GroupReqDTO;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** DB 조회 없이 그룹 통합 생성 요청 자체만으로 판단 가능한 교차 필드 규칙을 검증한다. */
public final class GroupCreationRequestValidator
        implements ConstraintValidator<ValidGroupCreationRequest, GroupReqDTO.CreateGroup> {

    @Override
    public boolean isValid(
            GroupReqDTO.CreateGroup request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }

        boolean valid = true;
        Set<String> categoryKeys = new HashSet<>();
        Set<String> categoryNames = new HashSet<>();

        List<GroupReqDTO.CreateGroupCategory> categories = request.customCategories();
        if (categories != null) {
            for (GroupReqDTO.CreateGroupCategory category : categories) {
                if (category == null) {
                    continue;
                }
                if (hasText(category.clientKey()) && !categoryKeys.add(category.clientKey())) {
                    valid &= addViolation(
                            context,
                            "사용자 카테고리 clientKey는 요청 안에서 중복될 수 없습니다.",
                            "customCategories"
                    );
                }
                if (hasText(category.name())
                        && !categoryNames.add(normalizeName(category.name()))) {
                    valid &= addViolation(
                            context,
                            "카테고리 이름은 요청 내 사용자 카테고리와 중복될 수 없습니다.",
                            "customCategories"
                    );
                }
            }
        }

        Set<String> routineTitles = new HashSet<>();
        List<GroupReqDTO.CreateGroupRoutine> routines = request.routines();
        if (routines != null) {
            for (GroupReqDTO.CreateGroupRoutine routine : routines) {
                if (routine == null) {
                    continue;
                }
                valid &= validateCategoryReference(routine, categoryKeys, context);
                if (hasText(routine.title())
                        && !routineTitles.add(normalizeName(routine.title()))) {
                    valid &= addViolation(
                            context,
                            "루틴 제목은 요청 안에서 중복될 수 없습니다.",
                            "routines"
                    );
                }
            }
        }
        return valid;
    }

    private boolean validateCategoryReference(
            GroupReqDTO.CreateGroupRoutine routine,
            Set<String> categoryKeys,
            ConstraintValidatorContext context
    ) {
        boolean hasCategoryId = routine.categoryId() != null;
        boolean hasCategoryKey = hasText(routine.categoryKey());
        if (hasCategoryId == hasCategoryKey) {
            return addViolation(
                    context,
                    "기본 카테고리 ID 또는 사용자 카테고리 키 중 하나만 지정해야 합니다.",
                    "routines"
            );
        }
        if (hasCategoryKey && !categoryKeys.contains(routine.categoryKey())) {
            return addViolation(
                    context,
                    "요청에 존재하지 않는 사용자 카테고리 키입니다.",
                    "routines"
            );
        }
        return true;
    }

    private boolean addViolation(
            ConstraintValidatorContext context,
            String message,
            String property
    ) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
        return false;
    }

    private String normalizeName(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
