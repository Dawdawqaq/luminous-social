package com.luminous.api.upload;

import com.luminous.api.common.BusinessException;
import com.luminous.api.security.CurrentUser;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class CommentImageOwnershipTest {
    @Test
    void allowsOwnUploadedImageAndRejectsMissingForeignOrNonImageFiles() {
        UploadedFileMapper files = mock(UploadedFileMapper.class);
        UploadService service = new UploadService(files, null, null);
        CurrentUser user = new CurrentUser(1L, "E2E_TEST_image", "USER");
        String url = "/api/files/image.png?token=test";
        assertThatThrownBy(() -> service.requireOwnedImage(url, user)).isInstanceOf(BusinessException.class);
        UploadedFile file = new UploadedFile();
        file.setUserId(2L); file.setUrl(url); file.setContentType("image/png");
        when(files.selectOne(any())).thenReturn(file);
        assertThatThrownBy(() -> service.requireOwnedImage(url, user)).isInstanceOf(BusinessException.class);
        file.setUserId(1L); file.setContentType("image/svg+xml");
        assertThatThrownBy(() -> service.requireOwnedImage(url, user)).isInstanceOf(BusinessException.class);
        file.setContentType("image/png");
        assertThat(service.requireOwnedImage(url, user)).isEqualTo(url);
    }
}
