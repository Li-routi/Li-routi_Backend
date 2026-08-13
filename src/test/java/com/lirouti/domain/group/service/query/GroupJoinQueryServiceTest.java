package com.lirouti.domain.group.service.query;

import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.entity.Group;
import com.lirouti.domain.group.entity.GroupMember;
import com.lirouti.domain.group.enums.GroupJoinUnavailableReason;
import com.lirouti.domain.group.enums.GroupMemberStatus;
import com.lirouti.domain.group.enums.GroupStatus;
import com.lirouti.domain.group.exception.GroupException;
import com.lirouti.domain.group.exception.code.error.GroupErrorCode;
import com.lirouti.domain.group.repository.GroupMemberRepository;
import com.lirouti.domain.group.repository.GroupRepository;
import com.lirouti.domain.group.repository.GroupRoutineRepository;
import com.lirouti.domain.member.entity.Member;
import com.lirouti.domain.member.service.query.MemberQueryService;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import com.lirouti.domain.media.service.MediaService;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;
import java.lang.reflect.RecordComponent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("GroupJoinQueryService 테스트")
class GroupJoinQueryServiceTest {
    private static final Long MEMBER_ID = 1L;
    private static final Long GROUP_ID = 10L;
    private static final String INVITE_CODE = "AB12CD3";

    @Mock private GroupRepository groupRepository;
    @Mock private GroupMemberRepository groupMemberRepository;
    @Mock private GroupRoutineRepository groupRoutineRepository;
    @Mock private MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    @Mock private MediaService mediaService;
    @Mock private MemberQueryService memberQueryService;
    @Mock private Member member;
    @Mock private Group group;
    @Mock private GroupMember groupMember;
    @InjectMocks private GroupJoinQueryService groupJoinQueryService;

    @BeforeEach
    void setUp() {
        when(memberQueryService.getActiveMember(MEMBER_ID)).thenReturn(member);
        lenient().when(member.getId()).thenReturn(MEMBER_ID);
        lenient().when(groupRepository.findByInviteCode(INVITE_CODE)).thenReturn(Optional.of(group));
        lenient().when(group.getId()).thenReturn(GROUP_ID);
        lenient().when(group.getName()).thenReturn("아침 루틴 모임");
        lenient().when(group.getStatus()).thenReturn(GroupStatus.ACTIVE);
        lenient().when(group.isLocked()).thenReturn(false);
        lenient().when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(5L);
        lenient().when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.empty());
        lenient().when(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE)).thenReturn(5L);
        lenient().when(groupMemberRepository.findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
                GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(List.of(101L, 102L, 103L, 104L, 105L));
        lenient().when(groupRoutineRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(2L);
    }

    @Test
    @DisplayName("참여 가능한 ACTIVE 그룹의 최소 Preview 정보를 반환한다")
    void getJoinPreview_Joinable_ReturnsGroupInformation() {
        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, "ab12cd3");

        assertThat(result).isEqualTo(new GroupResDTO.JoinPreview(
                GROUP_ID,
                "아침 루틴 모임",
                5,
                6,
                2,
                List.of(
                        new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of())),
                        new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of())),
                        new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of())),
                        new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of())),
                        new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of()))
                ),
                true,
                null));
        verify(groupRepository).findByInviteCode(INVITE_CODE);
    }

    @Test
    @DisplayName("ACTIVE 구성원 수만큼 정렬 조회 결과 순서대로 아바타 요약을 반환한다")
    void getJoinPreview_ActiveMembers_ReturnsMemberSummariesWithoutIdentifiers() {
        when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(3L);
        when(groupMemberRepository.findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
                GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(List.of(30L, 10L, 20L));
        Member equipmentOwner = mock(Member.class);
        AvatarItem avatarItem = mock(AvatarItem.class);
        MemberAvatarEquipment equipment = mock(MemberAvatarEquipment.class);
        when(equipmentOwner.getId()).thenReturn(10L);
        when(avatarItem.getImageKey()).thenReturn("avatar/item/head/hat-v1.png");
        when(mediaService.resolveAvatarAssetUrl("avatar/item/head/hat-v1.png"))
                .thenReturn("https://cdn/hat.png");
        when(equipment.getMember()).thenReturn(equipmentOwner);
        when(equipment.getAvatarItem()).thenReturn(avatarItem);
        when(equipment.getSlot()).thenReturn(AvatarSlot.HEAD);
        when(memberAvatarEquipmentRepository.findAllByMemberIdInWithMemberAndAvatarItem(
                List.of(30L, 10L, 20L))).thenReturn(List.of(equipment));

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.members()).containsExactly(
                new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of())),
                new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of(
                        new GroupResDTO.Equipped(AvatarSlot.HEAD, "https://cdn/hat.png")))),
                new GroupResDTO.JoinPreviewMember(new GroupResDTO.Avatar(List.of()))
        );
        assertThat(GroupResDTO.JoinPreviewMember.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("avatar");
        verify(groupMemberRepository).findMemberIdsByGroupIdAndStatusOrderByJoinedAtAscIdAsc(
                GROUP_ID, GroupMemberStatus.ACTIVE);
        verify(memberAvatarEquipmentRepository)
                .findAllByMemberIdInWithMemberAndAvatarItem(List.of(30L, 10L, 20L));
    }

    @Test
    @DisplayName("ACTIVE 루틴 count만 Preview의 totalRoutineCount로 반환한다")
    void getJoinPreview_ActiveRoutineCount_ReturnsCount() {
        when(groupRoutineRepository.countByGroupIdAndActiveTrue(GROUP_ID)).thenReturn(4L);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.totalRoutineCount()).isEqualTo(4);
        verify(groupRoutineRepository).countByGroupIdAndActiveTrue(GROUP_ID);
    }

    @Test
    @DisplayName("잠긴 그룹은 Preview 조회를 거부한다")
    void getJoinPreview_LockedGroup_ThrowsGroupLocked() {
        when(group.isLocked()).thenReturn(true);

        assertThatThrownBy(() -> groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_LOCKED);
        verify(groupMemberRepository, never()).countActiveMembersByGroupId(GROUP_ID, GroupMemberStatus.ACTIVE);
    }

    @Test
    @DisplayName("이미 ACTIVE 구성원이면 중복 가입 불가 사유를 반환한다")
    void getJoinPreview_ActiveMember_ReturnsAlreadyActiveReason() {
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.ACTIVE);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.joinable()).isFalse();
        assertThat(result.unavailableReason())
                .isEqualTo(GroupJoinUnavailableReason.ALREADY_ACTIVE_MEMBER);
    }

    @Test
    @DisplayName("KICKED 구성원은 재가입 불가 사유를 반환한다")
    void getJoinPreview_KickedMember_ReturnsKickedReason() {
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.KICKED);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.joinable()).isFalse();
        assertThat(result.unavailableReason()).isEqualTo(GroupJoinUnavailableReason.KICKED_MEMBER);
    }

    @Test
    @DisplayName("회원이 이미 ACTIVE 그룹 6개에 참여하면 참여 불가 사유를 반환한다")
    void getJoinPreview_MemberAtLimit_ReturnsMemberLimitReason() {
        when(groupMemberRepository.countByMemberIdAndStatusAndGroupStatus(
                MEMBER_ID, GroupMemberStatus.ACTIVE, GroupStatus.ACTIVE)).thenReturn(6L);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.joinable()).isFalse();
        assertThat(result.unavailableReason())
                .isEqualTo(GroupJoinUnavailableReason.MEMBER_GROUP_LIMIT_REACHED);
    }

    @Test
    @DisplayName("그룹 ACTIVE 구성원이 이미 6명이면 참여 불가 사유를 반환한다")
    void getJoinPreview_GroupAtLimit_ReturnsGroupLimitReason() {
        when(groupMemberRepository.countActiveMembersByGroupId(
                GROUP_ID, GroupMemberStatus.ACTIVE)).thenReturn(6L);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.joinable()).isFalse();
        assertThat(result.unavailableReason())
                .isEqualTo(GroupJoinUnavailableReason.GROUP_MEMBER_LIMIT_REACHED);
    }

    @Test
    @DisplayName("LEFT 구성원은 재가입 가능한 대상으로 반환한다")
    void getJoinPreview_LeftMember_ReturnsJoinable() {
        when(groupMemberRepository.findByGroupIdAndMemberId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(groupMember));
        when(groupMember.getStatus()).thenReturn(GroupMemberStatus.LEFT);

        GroupResDTO.JoinPreview result = groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        assertThat(result.joinable()).isTrue();
        assertThat(result.unavailableReason()).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 초대코드와 비활성 그룹은 조회할 수 없다")
    void getJoinPreview_MissingOrInactiveGroup_ThrowsDomainError() {
        when(groupRepository.findByInviteCode(INVITE_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_NOT_FOUND);

        when(groupRepository.findByInviteCode(INVITE_CODE)).thenReturn(Optional.of(group));
        when(group.getStatus()).thenReturn(GroupStatus.DELETED);

        assertThatThrownBy(() -> groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE))
                .isInstanceOf(GroupException.class)
                .extracting("code")
                .isEqualTo(GroupErrorCode.GROUP_INACTIVE);
    }

    @Test
    @DisplayName("Preview는 가입 관계나 Assignment를 저장하지 않는 읽기 전용 조회다")
    void getJoinPreview_DoesNotMutateData() {
        groupJoinQueryService.getJoinPreview(MEMBER_ID, INVITE_CODE);

        verify(groupRepository, never()).save(group);
        verify(groupMemberRepository, never()).save(groupMember);
    }
}
