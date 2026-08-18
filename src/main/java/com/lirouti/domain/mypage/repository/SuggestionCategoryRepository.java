package com.lirouti.domain.mypage.repository;

import com.lirouti.domain.mypage.entity.SuggestionCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SuggestionCategoryRepository extends JpaRepository<SuggestionCategory, Long> {

    /**
     * 고를 수 있는 분류.
     *
     * <p><b>정렬을 계약으로 못박는다.</b> DB 기본 순서는 보장되지 않는다. {@code id} 를 뒤에
     * 두는 것은 {@code displayOrder} 가 같은 둘이 생겼을 때 순서가 흔들리지 않게 하려는 것이다.
     */
    List<SuggestionCategory> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
}
