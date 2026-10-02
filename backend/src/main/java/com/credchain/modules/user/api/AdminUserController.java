package com.credchain.modules.user.api;

import com.credchain.common.api.PageResponse;
import com.credchain.modules.user.api.dto.AdminUserResponse;
import com.credchain.modules.user.application.UserQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")   // every method in this controller is admin-only
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Admin - Users", description = "User management (SUPER_ADMIN only)")
public class AdminUserController {

    private final UserQueryService userQueryService;

    @GetMapping
    @Operation(summary = "List all users, newest first")
    public PageResponse<AdminUserResponse> listUsers(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {

        return userQueryService.listUsers(page, size);
    }
}