package com.lirouti.domain.group.repository;

import com.lirouti.domain.group.entity.GroupPoke;
import org.springframework.data.jpa.repository.JpaRepository;

/** 찌르기 이력 저장소다. */
public interface GroupPokeRepository extends JpaRepository<GroupPoke, Long> {}
