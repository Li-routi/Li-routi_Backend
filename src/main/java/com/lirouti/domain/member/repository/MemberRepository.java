package com.lirouti.domain.member.repository;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.enums.SocialProvider;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findBySocialProviderAndSocialId(
            SocialProvider socialProvider,
            String socialId
    );

    boolean existsByEmail(String email);
}
