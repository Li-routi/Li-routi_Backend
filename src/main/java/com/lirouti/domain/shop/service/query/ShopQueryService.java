package com.lirouti.domain.shop.service.query;

import com.lirouti.domain.shop.converter.ShopConverter;
import com.lirouti.domain.shop.dto.response.ShopResDTO;
import com.lirouti.domain.shop.entity.AvatarItem;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.domain.shop.enums.AvatarSlot;
import com.lirouti.domain.shop.enums.ShopCategory;
import com.lirouti.domain.shop.repository.AvatarItemRepository;
import com.lirouti.domain.shop.repository.MemberAvatarEquipmentRepository;
import com.lirouti.domain.shop.repository.MemberAvatarItemRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.character.service.query.AvatarLayerAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ShopQueryService {

    private final AvatarItemRepository avatarItemRepository;
    private final MemberAvatarItemRepository memberAvatarItemRepository;
    private final MemberAvatarEquipmentRepository memberAvatarEquipmentRepository;
    private final MediaService mediaService;
    private final AvatarLayerAssembler avatarLayerAssembler;


    /**
     * 상점 화면의 탭 목록.
     *
     * <p><b>회원과 무관하다.</b> 탭 구성은 누가 보든 같다 — 보유 여부로 탭이 생기거나
     * 사라지지 않는다.
     *
     * <p>DB 를 보지 않는다. 탭은 마스터 표가 아니라 화면 구성이고, 늘거나 줄 때 배포가
     * 따르는 것이 맞다. 표로 두면 운영이 탭을 바꿀 수 있게 되는데, 탭이 늘면 그것을 그릴
     * 화면도 함께 필요하므로 데이터만 바꿔서 될 일이 아니다.
     */
    @Transactional(readOnly = true)
    public ShopResDTO.Categories getCategories() {
        return ShopConverter.toCategories(List.of(ShopCategory.values()));
    }

    /**
     * 상점 아이템 목록.
     *
     * <p><b>보유한 것도 같은 목록에 섞여 나온다.</b> 화면이 격자 하나이고, 보유 여부는
     * {@code owned} 로 가른다.
     *
     * @param slot      {@code null} 이면 전체. 화면의 "전체" 탭이 이것이다
     * @param ownedOnly 보유한 것만 볼 때
     */
    @Transactional(readOnly = true)
    public ShopResDTO.Items getItems(Long memberId, AvatarSlot slot, boolean ownedOnly) {
        Set<Long> ownedIds = Set.copyOf(memberAvatarItemRepository.findOwnedItemIds(memberId));

        // 정렬은 두 경로 모두 쿼리에서 한다. 한쪽만 자바에서 하면 같은 화면인데 순서가 갈린다.
        List<AvatarItem> items = ownedOnly
                // 보유한 것만 볼 때는 판매 여부를 보지 않는다 — 이미 산 것이다.
                ? avatarItemRepository.findOwnedForShop(slot, ownedIds)
                // 판매 중인 것 + 보유한 것. 보유했는데 판매가 내려간 아이템이 빠지면, 착장
                // 저장이 전체 목록을 받으므로 화면에서 고를 수 없어 조용히 벗겨진다.
                : avatarItemRepository.findForShop(slot, ownedIds);
        return ShopConverter.toItems(items, ownedIds, mediaService::resolveAvatarAssetUrl);
    }

    /** 현재 착용 상태. 안 입은 자리는 실리지 않는다. */
    @Transactional(readOnly = true)
    public ShopResDTO.Avatar getMyAvatar(Long memberId) {
        List<MemberAvatarEquipment> equipped =
                memberAvatarEquipmentRepository.findAllByMemberId(memberId);
        return ShopConverter.toAvatar(equipped,
                avatarLayerAssembler.assembleAsResponse(memberId, equipped),
                mediaService::resolveAvatarAssetUrl);
    }
}
