package com.luminous.api.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.common.TimeText;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityService;
import com.luminous.api.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PostService {
    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final BookmarkMapper bookmarkMapper;
    private final PostVoteMapper postVoteMapper;
    private final CommentVoteMapper commentVoteMapper;
    private final CommunityService communityService;

    public PostService(
            PostMapper postMapper,
            CommentMapper commentMapper,
            UserMapper userMapper,
            BookmarkMapper bookmarkMapper,
            PostVoteMapper postVoteMapper,
            CommentVoteMapper commentVoteMapper,
            CommunityService communityService
    ) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.postVoteMapper = postVoteMapper;
        this.commentVoteMapper = commentVoteMapper;
        this.communityService = communityService;
    }

    public PageResponse<PostDtos.PostDto> list(String filter, String communitySlug, String search, long page, long size, CurrentUser currentUser) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>().eq(Post::getIsPublic, true);
        if (communitySlug != null && !communitySlug.isBlank()) {
            wrapper.eq(Post::getCommunityId, communityService.requireBySlug(communitySlug).getId());
        }
        if (search != null && !search.isBlank()) {
            wrapper.and(w -> w.like(Post::getTitle, search).or().like(Post::getContent, search));
        }
        if ("new".equals(filter)) {
            wrapper.orderByDesc(Post::getCreatedAt);
        } else {
            wrapper.orderByDesc(Post::getVotes).orderByDesc(Post::getCreatedAt);
        }
        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<PostDtos.PostDto> items = result.getRecords().stream().map(post -> toDto(post, currentUser)).toList();
        return new PageResponse<>(items, result.getCurrent(), result.getSize(), result.getTotal(), result.getCurrent() < result.getPages());
    }

    public PostDtos.PostDto detail(Long id, CurrentUser currentUser) {
        return toDto(requirePost(id), currentUser);
    }

    @Transactional
    public PostDtos.PostDto create(PostDtos.CreatePostRequest request, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能发布帖子");
        }
        Community community = communityService.requireBySlug(request.community());
        Post post = new Post();
        post.setId(IdWorker.getId());
        post.setAuthorId(currentUser.id());
        post.setCommunityId(community.getId());
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setImageUrl(request.image());
        post.setPostType("standard");
        post.setVotes(0);
        post.setCommentsCount(0);
        post.setIsPublic(request.isPublic());
        post.setCreatedAt(java.time.LocalDateTime.now());
        postMapper.insert(post);
        return toDto(post, currentUser);
    }

    public List<PostDtos.CommentDto> comments(Long postId, CurrentUser currentUser) {
        requirePost(postId);
        return commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                        .eq(Comment::getPostId, postId)
                        .orderByAsc(Comment::getCreatedAt))
                .stream()
                .map(c -> toDto(c, currentUser))
                .toList();
    }

    @Transactional
    public PostDtos.CommentDto addComment(Long postId, PostDtos.CreateCommentRequest request, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能发表评论");
        }
        Post post = requirePost(postId);
        Comment comment = new Comment();
        comment.setId(IdWorker.getId());
        comment.setPostId(postId);
        comment.setAuthorId(currentUser.id());
        comment.setContent(request.content());
        comment.setVotes(0);
        comment.setCreatedAt(java.time.LocalDateTime.now());
        commentMapper.insert(comment);
        post.setCommentsCount(post.getCommentsCount() + 1);
        postMapper.updateById(post);
        return toDto(comment, currentUser);
    }

    @Transactional
    public PostDtos.PostDto votePost(Long postId, int value, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能进行投票操作");
        }
        if (value != -1 && value != 0 && value != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VOTE", "投票值只能是 -1、0 或 1");
        }
        Post post = requirePost(postId);
        PostVote existing = postVoteMapper.selectOne(new LambdaQueryWrapper<PostVote>()
                .eq(PostVote::getPostId, postId)
                .eq(PostVote::getUserId, currentUser.id()));
        int oldValue = existing == null ? 0 : existing.getValue();
        if (value == 0 && existing != null) {
            postVoteMapper.deleteById(existing.getId());
        } else if (existing == null && value != 0) {
            PostVote vote = new PostVote();
            vote.setId(IdWorker.getId());
            vote.setPostId(postId);
            vote.setUserId(currentUser.id());
            vote.setValue(value);
            postVoteMapper.insert(vote);
        } else if (existing != null) {
            existing.setValue(value);
            postVoteMapper.updateById(existing);
        }
        post.setVotes(post.getVotes() + value - oldValue);
        postMapper.updateById(post);
        return toDto(post, currentUser);
    }

    @Transactional
    public PostDtos.CommentDto voteComment(Long commentId, int value, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能对评论进行投票操作");
        }
        if (value != -1 && value != 0 && value != 1) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VOTE", "投票值只能是 -1、0 或 1");
        }
        Comment comment = requireComment(commentId);
        CommentVote existing = commentVoteMapper.selectOne(new LambdaQueryWrapper<CommentVote>()
                .eq(CommentVote::getCommentId, commentId)
                .eq(CommentVote::getUserId, currentUser.id()));
        int oldValue = existing == null ? 0 : existing.getValue();
        if (value == 0 && existing != null) {
            commentVoteMapper.deleteById(existing.getId());
        } else if (existing == null && value != 0) {
            CommentVote vote = new CommentVote();
            vote.setId(IdWorker.getId());
            vote.setCommentId(commentId);
            vote.setUserId(currentUser.id());
            vote.setValue(value);
            commentVoteMapper.insert(vote);
        } else if (existing != null) {
            existing.setValue(value);
            commentVoteMapper.updateById(existing);
        }
        comment.setVotes(comment.getVotes() + value - oldValue);
        commentMapper.updateById(comment);
        return toDto(comment, currentUser);
    }

    @Transactional
    public PostDtos.PostDto bookmark(Long postId, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能收藏帖子");
        }
        Post post = requirePost(postId);
        if (!isBookmarked(postId, currentUser.id())) {
            Bookmark bookmark = new Bookmark();
            bookmark.setId(IdWorker.getId());
            bookmark.setPostId(postId);
            bookmark.setUserId(currentUser.id());
            bookmarkMapper.insert(bookmark);
        }
        return toDto(post, currentUser);
    }

    @Transactional
    public PostDtos.PostDto removeBookmark(Long postId, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能取消收藏帖子");
        }
        Post post = requirePost(postId);
        bookmarkMapper.delete(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getPostId, postId)
                .eq(Bookmark::getUserId, currentUser.id()));
        return toDto(post, currentUser);
    }

    public PageResponse<PostDtos.PostDto> bookmarks(long page, long size, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能查看收藏列表");
        }
        List<Long> postIds = bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, currentUser.id())
                        .orderByDesc(Bookmark::getCreatedAt))
                .stream()
                .map(Bookmark::getPostId)
                .toList();
        if (postIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, false);
        }
        long from = Math.min((page - 1) * size, postIds.size());
        long to = Math.min(from + size, postIds.size());
        List<PostDtos.PostDto> items = postIds.subList((int) from, (int) to)
                .stream()
                .map(postMapper::selectById)
                .map(post -> toDto(post, currentUser))
                .toList();
        return new PageResponse<>(items, page, size, postIds.size(), to < postIds.size());
    }

    private Post requirePost(Long id) {
        Post post = postMapper.selectById(id);
        if (post == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        }
        return post;
    }

    private Comment requireComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在");
        }
        return comment;
    }

    private PostDtos.PostDto toDto(Post post, CurrentUser currentUser) {
        User author = userMapper.selectById(post.getAuthorId());
        Community community = communityService.detailById(post.getCommunityId());
        int userVote = 0;
        if (currentUser != null) {
            PostVote vote = postVoteMapper.selectOne(new LambdaQueryWrapper<PostVote>()
                    .eq(PostVote::getPostId, post.getId())
                    .eq(PostVote::getUserId, currentUser.id()));
            if (vote != null) userVote = vote.getValue();
        }
        return new PostDtos.PostDto(
                String.valueOf(post.getId()),
                new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl()),
                new PostDtos.CommunityRefDto("c/" + community.getSlug(), community.getIconUrl()),
                post.getTitle(),
                post.getContent(),
                post.getImageUrl(),
                TimeText.from(post.getCreatedAt()),
                post.getVotes(),
                post.getCommentsCount(),
                post.getPostType(),
                currentUser != null && isBookmarked(post.getId(), currentUser.id()),
                userVote
        );
    }

    private PostDtos.CommentDto toDto(Comment comment, CurrentUser currentUser) {
        User author = userMapper.selectById(comment.getAuthorId());
        int userVote = 0;
        if (currentUser != null) {
            CommentVote vote = commentVoteMapper.selectOne(new LambdaQueryWrapper<CommentVote>()
                    .eq(CommentVote::getCommentId, comment.getId())
                    .eq(CommentVote::getUserId, currentUser.id()));
            if (vote != null) userVote = vote.getValue();
        }
        return new PostDtos.CommentDto(
                String.valueOf(comment.getId()),
                new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl()),
                comment.getContent(),
                TimeText.from(comment.getCreatedAt()),
                comment.getVotes(),
                userVote
        );
    }

    private boolean isBookmarked(Long postId, Long userId) {
        return bookmarkMapper.selectCount(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getPostId, postId)
                .eq(Bookmark::getUserId, userId)) > 0;
    }
}
