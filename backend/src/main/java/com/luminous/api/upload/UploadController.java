package com.luminous.api.upload;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @Operation(summary = "上传帖子图片")
    @PostMapping("/images")
    public ApiResponse<UploadDtos.UploadResponse> uploadImage(@RequestParam("file") MultipartFile file,
                                                              @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(uploadService.uploadImage(file, currentUser));
    }
}
