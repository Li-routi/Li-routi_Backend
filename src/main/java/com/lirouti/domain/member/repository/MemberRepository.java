package com.lirouti.domain.member.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;

import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findBySocialProviderAndSocialId(
            SocialProvider socialProvider,
            String socialId
    );

    boolean existsByEmail(String email);

    // 회원 탈퇴 시 동시성 문제를 방지하기 위한 비관적 락 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT member FROM Member member WHERE member.id = :memberId")
    Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
