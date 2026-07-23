package com.lirouti.domain.group.controller;

import com.lirouti.domain.group.controller.docs.GroupControllerDocs;
import com.lirouti.domain.group.dto.request.GroupReqDTO;
import com.lirouti.domain.group.dto.response.GroupResDTO;
import com.lirouti.domain.group.exception.code.success.GroupSuccessCode;
import com.lirouti.domain.group.service.command.GroupCommandService;
import com.lirouti.global.apiPayload.ApiResponse;
import com.lirouti.global.auth.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/groups")
public class GroupController implements GroupControllerDocs {
    private final GroupCommandService groupCommandService;

    @Override
    @PostMapping("/{groupId}/routines")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GroupResDTO.RoutineCreateResult> createRoutine(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable Long groupId,
            @Valid @RequestBody GroupReqDTO.CreateRoutine request
    ) {
        GroupResDTO.RoutineCreateResult result = groupCommandService.createRoutine(
                groupId,
                userDetails.getMemberId(),
                request
        );
        return ApiResponse.onSuccess(GroupSuccessCode.GROUP_ROUTINE_CREATE_SUCCESS, result);
    }
}
