package com.luminous.api.common;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileValidationUtilTest {

    @Test
    void detectMimeType_AcceptsValidPng() {
        // PNG Header magic bytes: 89 50 4E 47 0D 0A 1A 0A
        byte[] pngHeader = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", pngHeader);

        String mime = FileValidationUtil.detectMimeType(file);
        assertThat(mime).isEqualTo("image/png");
        assertThat(FileValidationUtil.getExtensionForMimeType(mime)).isEqualTo(".png");
    }

    @Test
    void detectMimeType_AcceptsValidJpeg() {
        // JPEG Header magic bytes: FF D8 FF
        byte[] jpegHeader = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegHeader);

        String mime = FileValidationUtil.detectMimeType(file);
        assertThat(mime).isEqualTo("image/jpeg");
        assertThat(FileValidationUtil.getExtensionForMimeType(mime)).isEqualTo(".jpg");
    }

    @Test
    void detectMimeType_RejectsSpoofedScriptFile() {
        // A malicious shell script pretending to be a JPG
        byte[] scriptBytes = "<% Runtime.getRuntime().exec(request.getParameter(\"cmd\")); %>".getBytes();
        MockMultipartFile fakeJpg = new MockMultipartFile("file", "webshell.jpg", "image/jpeg", scriptBytes);

        assertThatThrownBy(() -> FileValidationUtil.detectMimeType(fakeJpg))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("INVALID_FILE_TYPE");
                });
    }

    @Test
    void detectMimeType_RejectsEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> FileValidationUtil.detectMimeType(emptyFile))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("EMPTY_FILE");
                });
    }
}
