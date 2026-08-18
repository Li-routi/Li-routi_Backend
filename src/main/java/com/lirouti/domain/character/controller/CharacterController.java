package com.lirouti.domain.character.controller;

import com.lirouti.domain.character.controller.docs.CharacterControllerDocs;
import com.lirouti.domain.character.dto.request.CharacterReqDTO;
import com.lirouti.domain.character.dto.response.CharacterResDTO;
import com.lirouti.domain.character.exception.code.success.CharacterSuccessCode;
import com.lirouti.domain.character.service.command.CharacterSelectionCommandService;
import com.lirouti.domain.character.service.query.CharacterQueryService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/characters")
public class CharacterController implements CharacterControllerDocs {

    private final CharacterQueryService characterQueryService;
    private final CharacterSelectionCommandService characterSelectionCommandService;

    @Override
    @GetMapping
    public ApiResponse<CharacterResDTO.Characters> getCharacters(
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        CharacterResDTO.Characters result =
                characterQueryService.getCharacters(userDetails.getMemberId());
        return ApiResponse.onSuccess(CharacterSuccessCode.CHARACTER_LIST_FETCH_SUCCESS, result);
    }

    @Override
    @PutMapping("/selection")
    public ApiResponse<Void> select(
            @Valid @RequestBody CharacterReqDTO.Select request,
            @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        characterSelectionCommandService.select(userDetails.getMemberId(), request.characterId());
        return ApiResponse.onSuccess(CharacterSuccessCode.CHARACTER_SELECT_SUCCESS, null);
    }
}
