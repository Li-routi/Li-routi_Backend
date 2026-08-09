package com.lirouti.domain.group.entity;

import com.lirouti.domain.member.entity.Member;
import com.lirouti.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;

/** 같은 그룹의 한 회원이 다른 회원을 하루 한 번 찌른 기록이다. */
@Entity @Getter @NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "group_poke")
public class GroupPoke extends BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="group_id") private Group group;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="sender_id") private Member sender;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name="recipient_id") private Member recipient;
    @Column(nullable = false) private LocalDate pokedDate;

    /** 활성 그룹원 검증을 마친 발신자와 수신자의 당일 기록을 만든다. */
    @Builder
    private GroupPoke(Group group, Member sender, Member recipient, LocalDate pokedDate) {
        this.group=group; this.sender=sender; this.recipient=recipient; this.pokedDate=pokedDate;
    }
}
