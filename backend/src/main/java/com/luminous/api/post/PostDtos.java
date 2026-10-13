package com.luminous.api.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;

public final class PostDtos {
    public record CommentContext(CommentFloorDto floor, CommentDto target) {}
    public static final int MAX_TITLE_LENGTH = 15;
    public static final int MAX_CONTENT_LENGTH = 1000;
    public static final int MAX_COMMENT_LENGTH = 300;
    public static final int MAX_REPLY_LENGTH = 150;
    private PostDtos() {
    }

    public record AuthorDto(String username, String displayName, String avatar,
                            com.luminous.api.growth.GrowthDtos.Progress personalGrowth,
                            com.luminous.api.growth.GrowthDtos.Progress communityGrowth) {
        public AuthorDto(String username, String displayName, String avatar) { this(username, displayName, avatar, null, null); }
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
            String image,
            String timestamp,
            int votes,
            int userVote,
            String parentId,
            String replyToCommentId,
            AuthorDto replyTo
    ) {
        public CommentDto(String id, AuthorDto author, String content, String image, String timestamp, int votes, int userVote) {
            this(id, author, content, image, timestamp, votes, userVote, null, null, null);
        }
    }

    public record CommentFloorDto(String id, CommentDto root, long replyCount) {}

    public record CommentSlice<T>(java.util.List<T> items, String nextCursor, boolean hasNext, long total) {}

    public record CreatePostRequest(
            @NotBlank String community,
            @NotBlank @Size(max = MAX_TITLE_LENGTH, message = "标题最多 15 个字符") String title,
            @Size(max = MAX_CONTENT_LENGTH, message = "正文最多 1000 个字符") String content,
            String image,
            String type,
            boolean isPublic
    ) {
    }

    public record CreateCommentRequest(@Size(max = MAX_COMMENT_LENGTH, message = "评论最多 300 个字符") String content,
                                       @Size(max = 512) String image,
                                       @Positive Long replyToCommentId) {
        public CreateCommentRequest {
            content = content == null ? "" : content.trim();
            image = image == null || image.isBlank() ? null : image.trim();
        }

        public CreateCommentRequest(String content) { this(content, null, null); }
        public CreateCommentRequest(String content, String image) { this(content, image, null); }

        public int maxContentLength() {
            return replyToCommentId == null ? MAX_COMMENT_LENGTH : MAX_REPLY_LENGTH;
        }

        @AssertTrue(message = "楼中楼回复最多 150 个字符")
        public boolean isReplyLengthValid() {
            return replyToCommentId == null || content.length() <= MAX_REPLY_LENGTH;
        }

        @AssertTrue(message = "请填写评论文字或添加一张图片")
        public boolean isContentPresent() { return !content.isBlank() || image != null; }

        @AssertTrue(message = "楼中楼只支持文字回复，不能添加图片")
        public boolean isReplyTextOnly() { return replyToCommentId == null || image == null; }
    }

    public record VoteRequest(@NotNull Integer value) {
    }
}
