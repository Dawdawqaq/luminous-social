package com.luminous.api.upload;

public final class UploadDtos {
    private UploadDtos() {
    }

    public record UploadResponse(String id, String url, String originalName, long sizeBytes) {
    }
}
