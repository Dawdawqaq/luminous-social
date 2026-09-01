package com.luminous.api.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class PostDtos {
    private PostDtos() {
    }

    public record AuthorDto(String username, String displayName, String avatar) {
    }

    public record CommunityRefDto(String name, String icon) {
    }

    public record PostDto(
            String id,
            AuthorDto author,
            CommunityRefDto community,
            String title,
            String content,
            String image,
            String timestamp,
            int votes,
            int comments,
            String type,
            boolean bookmarked,
            int userVote,
            long viewCount
    ) {
    }

    public record CommentDto(
            String id,
            AuthorDto author,
            String content,
            String timestamp,
            int votes,
            int userVote
    ) {
    }

    public record CreatePostRequest(
            @NotBlank String community,
            @NotBlank @Size(max = 180) String title,
            @Size(max = 8000) String content,
            String image,
            String type,
            boolean isPublic
    ) {
    }

    public record CreateCommentRequest(@NotBlank @Size(max = 1000) String content) {
    }

    public record VoteRequest(@NotNull Integer value) {
    }
}
