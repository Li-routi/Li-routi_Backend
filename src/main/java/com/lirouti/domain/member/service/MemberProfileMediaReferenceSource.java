package com.lirouti.domain.member.service;

import com.lirouti.domain.media.enums.MediaPurpose;
import com.lirouti.domain.media.service.MediaReferenceSource;
import com.lirouti.domain.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MemberProfileMediaReferenceSource implements MediaReferenceSource {

    private final MemberRepository memberRepository;

    @Override
    public Set<MediaPurpose> coveredPurposes() {
        return Set.of(MediaPurpose.PROFILE);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> findReferencedKeys(Collection<String> candidateKeys) {
        if(candidateKeys.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(memberRepository.findProfileImageKeysIn(candidateKeys));
    }
}
