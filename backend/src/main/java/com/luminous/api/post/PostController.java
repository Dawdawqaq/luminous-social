package com.luminous.api.post;

import com.luminous.api.common.ApiResponse;
import com.luminous.api.common.PageResponse;
import com.luminous.api.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class PostController {
    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @Operation(summary = "分页获取帖子")
    @GetMapping("/posts")
    public ApiResponse<PageResponse<PostDtos.PostDto>> posts(
            @RequestParam(defaultValue = "hot") String filter,
            @RequestParam(required = false) String community,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(postService.list(filter, community, search, page, size, currentUser));
    }

    @Operation(summary = "获取帖子详情")
    @GetMapping("/posts/{id}")
    public ApiResponse<PostDtos.PostDto> detail(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.detail(id, currentUser));
    }

    @Operation(summary = "创建帖子")
    @PostMapping("/posts")
    public ApiResponse<PostDtos.PostDto> create(@Valid @RequestBody PostDtos.CreatePostRequest request,
                                                @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.create(request, currentUser));
    }

    @Operation(summary = "帖子投票")
    @PostMapping("/posts/{id}/vote")
    public ApiResponse<PostDtos.PostDto> votePost(@PathVariable Long id,
                                                  @Valid @RequestBody PostDtos.VoteRequest request,
                                                  @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.votePost(id, request.value(), currentUser));
    }

    @Operation(summary = "收藏帖子")
    @PostMapping("/posts/{id}/bookmark")
    public ApiResponse<PostDtos.PostDto> bookmark(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.bookmark(id, currentUser));
    }

    @Operation(summary = "取消收藏帖子")
    @DeleteMapping("/posts/{id}/bookmark")
    public ApiResponse<PostDtos.PostDto> removeBookmark(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.removeBookmark(id, currentUser));
    }

    @Operation(summary = "我的收藏列表")
    @GetMapping("/bookmarks")
    public ApiResponse<PageResponse<PostDtos.PostDto>> bookmarks(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        return ApiResponse.ok(postService.bookmarks(page, size, currentUser));
    }

    @Operation(summary = "获取帖子评论")
    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<PostDtos.CommentDto>> comments(@PathVariable Long postId, @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.comments(postId, currentUser));
    }

    @Operation(summary = "发表评论")
    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<PostDtos.CommentDto> addComment(@PathVariable Long postId,
                                                       @Valid @RequestBody PostDtos.CreateCommentRequest request,
                                                       @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.addComment(postId, request, currentUser));
    }

    @Operation(summary = "评论投票")
    @PostMapping("/comments/{id}/vote")
    public ApiResponse<PostDtos.CommentDto> voteComment(@PathVariable Long id,
                                                        @Valid @RequestBody PostDtos.VoteRequest request,
                                                        @AuthenticationPrincipal CurrentUser currentUser) {
        return ApiResponse.ok(postService.voteComment(id, request.value(), currentUser));
    }

    @Operation(summary = "删除帖子（仅作者）")
    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePost(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        postService.deletePost(id, currentUser);
    }

    @Operation(summary = "删除评论（仅作者）")
    @DeleteMapping("/comments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long id, @AuthenticationPrincipal CurrentUser currentUser) {
        postService.deleteComment(id, currentUser);
    }
}
