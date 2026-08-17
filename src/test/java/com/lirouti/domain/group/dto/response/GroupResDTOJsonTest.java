package com.lirouti.domain.group.dto.response;

import com.lirouti.domain.shop.enums.AvatarSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GroupResDTO JSON 테스트")
class GroupResDTOJsonTest {

    @Test
    @DisplayName("그룹 Preview 아바타는 slot과 imageUrl만 노출한다")
    void joinPreviewAvatar_ExcludesShopItemFields() throws Exception {
        GroupResDTO.JoinPreviewMember member = new GroupResDTO.JoinPreviewMember(
                new GroupResDTO.Avatar(List.of(
                        new GroupResDTO.Equipped(AvatarSlot.HEAD, "https://img/hat.png"))));

        String json = new ObjectMapper().writeValueAsString(member);

        assertThat(json)
                .contains("\"slot\":\"HEAD\"", "\"imageUrl\":\"https://img/hat.png\"")
                .doesNotContain("itemId", "name");
    }
}
