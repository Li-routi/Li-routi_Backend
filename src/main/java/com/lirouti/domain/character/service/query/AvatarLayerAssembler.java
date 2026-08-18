package com.lirouti.domain.character.service.query;

import com.lirouti.domain.activity.repository.MemberActivityDayRepository;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.entity.AvatarCharacter;
import com.lirouti.domain.character.enums.AvatarLayer;
import com.lirouti.domain.character.repository.AvatarCharacterRepository;
import com.lirouti.domain.character.repository.MemberCharacterRepository;
import com.lirouti.domain.character.repository.MemberSelectedCharacterRepository;
import com.lirouti.domain.media.service.MediaService;
import com.lirouti.domain.shop.entity.MemberAvatarEquipment;
import com.lirouti.global.properties.AvatarNestProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 화면에 겹쳐 그릴 레이어를 세운다.
 *
 * <p><b>서버가 순서를 정한다.</b> 앱이 슬롯 이름을 보고 깊이를 판단하면 레이어가 하나 늘 때마다
 * 앱 배포가 붙는다 — 정렬해서 내리고, 앱은 받은 순서대로 겹치기만 한다.
 *
 * <p><b>좌표는 내리지 않는다.</b> 자산이 전부 같은 캔버스(400×400)에 그려져 있어 겹치기만 하면
 * 맞는다. 좌표를 내리기 시작하면 자산을 다시 그릴 때마다 그 값이 낡고, 앱이 그것을 믿고 배치한
 * 뒤라 자산 교체가 앱 배포를 부른다.
 */
@Service
@RequiredArgsConstructor
public class AvatarLayerAssembler {

    private final AvatarCharacterRepository avatarCharacterRepository;
    private final MemberSelectedCharacterRepository memberSelectedCharacterRepository;
    private final MemberCharacterRepository memberCharacterRepository;
    private final MemberActivityDayRepository memberActivityDayRepository;
    private final MediaService mediaService;
    private final AvatarNestProperties nestProperties;
    private final Clock clock;

    /** 한 사람의 레이어를 응답 모양으로. */
    @Transactional(readOnly = true)
    public List<CharacterResDTO.Layer> assembleAsResponse(
            Long memberId, List<MemberAvatarEquipment> equipments) {
        return toResponse(assembleAll(List.of(memberId), Map.of(memberId, equipments)).get(memberId));
    }

    /** 여러 사람의 레이어를 응답 모양으로. */
    @Transactional(readOnly = true)
    public Map<Long, List<CharacterResDTO.Layer>> assembleAllAsResponse(
            List<Long> memberIds, Map<Long, List<MemberAvatarEquipment>> equipmentsByMemberId) {
        return assembleAll(memberIds, equipmentsByMemberId).entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, entry -> toResponse(entry.getValue()),
                        (left, right) -> left, java.util.LinkedHashMap::new));
    }

    private List<CharacterResDTO.Layer> toResponse(List<Layer> layers) {
        return layers.stream()
                .map(layer -> CharacterResDTO.Layer.builder()
                        .layer(layer.layer())
                        .z(layer.z())
                        .imageUrl(layer.imageUrl())
                        .build())
                .toList();
    }

    /**
     * 여러 사람의 레이어를 한 번에.
     *
     * <p>그룹 화면이 구성원마다 아바타를 그린다 — 사람마다 따로 조회하면 N+1 이 된다.
     */
    @Transactional(readOnly = true)
    public Map<Long, List<Layer>> assembleAll(
            List<Long> memberIds,
            Map<Long, List<MemberAvatarEquipment>> equipmentsByMemberId
    ) {
        Map<Long, Long> selectedCharacterIds = new java.util.HashMap<>();
        memberSelectedCharacterRepository.findAllByMemberIdIn(memberIds).forEach(selected ->
                selectedCharacterIds.put(selected.getMemberId(), selected.getCharacterId()));

        // 선택이 비어 있어도 보유가 있으면 가장 먼저 얻은 것으로 그린다.
        //
        // 캐릭터를 주는 경로가 둘이다 — 조건 판정(선택까지 채운다)과 업적 claim(보유만
        // 넣는다). 뒤엣것으로 첫 캐릭터를 얻으면 선택이 비어 있는데, 그대로 두면 캐릭터도
        // 둥지도 안 그려져 사용자는 "얻었는데 아무것도 안 바뀌었다" 를 보게 된다.
        //
        // 여기서 쓰기까지 하지는 않는다. 조회가 쓰기를 하면 읽기 전용 트랜잭션이 깨지고,
        // 사용자가 캐릭터를 고르는 순간 어차피 행이 생긴다.
        List<Long> withoutSelection = memberIds.stream().distinct()
                .filter(memberId -> !selectedCharacterIds.containsKey(memberId))
                .toList();
        if (!withoutSelection.isEmpty()) {
            memberCharacterRepository.findFirstOwnedByMemberIds(withoutSelection)
                    .forEach(owned -> selectedCharacterIds.putIfAbsent(
                            (Long) owned[0], (Long) owned[1]));
        }

        Map<Long, AvatarCharacter> charactersById = avatarCharacterRepository
                .findAllById(selectedCharacterIds.values().stream().distinct().toList()).stream()
                .collect(java.util.stream.Collectors.toMap(AvatarCharacter::getId, character -> character));

        Map<Long, Integer> nestLevels = nestLevelsOf(memberIds);

        return memberIds.stream().distinct().collect(java.util.stream.Collectors.toMap(
                memberId -> memberId,
                memberId -> assembleOne(
                        Optional.ofNullable(selectedCharacterIds.get(memberId))
                                .map(charactersById::get)
                                .orElse(null),
                        nestLevels.getOrDefault(memberId, 1),
                        equipmentsByMemberId.getOrDefault(memberId, List.of())),
                (left, right) -> left,
                java.util.LinkedHashMap::new
        ));
    }

    private List<Layer> assembleOne(AvatarCharacter character,
                                    int nestLevel,
                                    List<MemberAvatarEquipment> equipments) {
        // 캐릭터가 없으면 아무것도 그리지 않는다.
        //
        // 둥지만 두면 빈 둥지가 남고, 아이템만 두면 허공에 모자가 뜬다 — 둘 다 "무언가
        // 잘못됐다" 로 보이는 화면이라 차라리 비우는 편이 낫다. 캐릭터는 가입 시점과 백필로
        // 모두에게 들어가므로 여기 걸리는 사람은 없어야 하고, 걸린다면 그것이 신호다.
        if (character == null) {
            return List.of();
        }

        List<Layer> layers = new ArrayList<>();
        layers.add(layer(AvatarLayer.NEST_BACK, nestBackKey(nestLevel)));
        layers.add(layer(AvatarLayer.CHARACTER, character.getAdultImageKey()));
        layers.add(layer(AvatarLayer.NEST_FRONT, nestFrontKey(nestLevel)));

        equipments.forEach(equipment -> layers.add(layer(
                AvatarLayer.of(equipment.getSlot()),
                equipment.getAvatarItem().getImageKey())));

        // 정렬은 서버가 한다. 클라이언트가 순서 규칙을 따로 갖지 않게 한다.
        layers.sort(Comparator.comparingInt(Layer::z));
        return layers;
    }

    /**
     * 둥지 레벨. <b>저장하지 않고 지금 기록으로 계산한다.</b>
     *
     * <p>강등이 있어 상태로 들고 다니면 "기록" 과 "카운터" 라는 진실이 둘이 된다. 창 길이와
     * 임계값이 같으므로 창이 가득 찼다는 것이 곧 연속이라는 뜻이고, 끊김을 따로 판정하지 않는다.
     *
     * <p><b>창은 어제까지다.</b> 오늘을 창에 넣으면 오늘 몫을 아직 못 끝낸 아침마다 하나가
     * 모자라, 레벨 2 를 유지하던 사람의 둥지가 매일 자정에 쪼그라들었다가 저녁에 돌아온다.
     * 어제까지만 보면 어제 시점의 판정이 하루 동안 그대로 유지된다. 대신 15일째를 끝낸 그날
     * 저녁이 아니라 <b>다음 날부터</b> 레벨 2 가 된다 — 둥지가 하룻밤 사이에 자란다.
     *
     * <p>기준일을 애플리케이션이 넘긴다 — {@code CURRENT_DATE} 는 DB 세션 시간대를 따르는데
     * 활동일은 KST 로 찍혀서 자정 언저리에 하루가 밀린다.
     */
    private Map<Long, Integer> nestLevelsOf(List<Long> memberIds) {
        if (memberIds.isEmpty()) {
            return Map.of();
        }
        int windowDays = nestProperties.getLevel2Days();
        LocalDate yesterday = LocalDate.now(clock).minusDays(1);
        LocalDate inclusiveFrom = yesterday.minusDays(windowDays - 1L);

        Map<Long, Integer> levels = new java.util.HashMap<>();
        memberActivityDayRepository
                .countCompletedDaysByMemberIds(memberIds.stream().distinct().toList(), inclusiveFrom, yesterday)
                .forEach(row -> levels.put(
                        (Long) row[0],
                        ((Number) row[1]).longValue() >= windowDays ? 2 : 1));
        return levels;
    }

    private String nestBackKey(int level) {
        return level == 2 ? nestProperties.getLevel2BackKey() : nestProperties.getLevel1BackKey();
    }

    private String nestFrontKey(int level) {
        return level == 2 ? nestProperties.getLevel2FrontKey() : nestProperties.getLevel1FrontKey();
    }

    private Layer layer(AvatarLayer avatarLayer, String imageKey) {
        return new Layer(avatarLayer, mediaService.resolveAvatarAssetUrl(imageKey));
    }

    /** 그릴 것 하나. */
    public record Layer(AvatarLayer layer, String imageUrl) {

        public int z() {
            return layer.getZ();
        }
    }
}
