package com.luminous.api.post;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.auth.User;
import com.luminous.api.auth.UserMapper;
import com.luminous.api.cache.CacheConstants;
import com.luminous.api.cache.PostCacheService;
import com.luminous.api.cache.RedisLuaScriptService;
import com.luminous.api.cache.UserStatsCacheService;
import com.luminous.api.common.BusinessException;
import com.luminous.api.common.PageResponse;
import com.luminous.api.common.SnowflakeIdGenerator;
import com.luminous.api.common.TimeText;
import com.luminous.api.community.Community;
import com.luminous.api.community.CommunityService;
import com.luminous.api.feed.FeedService;
import com.luminous.api.mq.VoteMQProducer;
import com.luminous.api.mq.VoteMessage;
import com.luminous.api.security.CurrentUser;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostService {
    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostMapper postMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final BookmarkMapper bookmarkMapper;
    private final PostVoteMapper postVoteMapper;
    private final CommentVoteMapper commentVoteMapper;
    private final CommunityService communityService;
    private final ApplicationEventPublisher eventPublisher;
    private final PostCacheService postCacheService;
    private final RedissonClient redissonClient;
    private final UserStatsCacheService userStatsCacheService;
    private final FeedService feedService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisLuaScriptService redisLuaScriptService;
    private final VoteMQProducer voteMQProducer;

    public PostService(
            PostMapper postMapper,
            CommentMapper commentMapper,
            UserMapper userMapper,
            BookmarkMapper bookmarkMapper,
            PostVoteMapper postVoteMapper,
            CommentVoteMapper commentVoteMapper,
            CommunityService communityService,
            ApplicationEventPublisher eventPublisher,
            PostCacheService postCacheService,
            RedissonClient redissonClient,
            UserStatsCacheService userStatsCacheService,
            @Lazy FeedService feedService,
            StringRedisTemplate stringRedisTemplate,
            RedisLuaScriptService redisLuaScriptService,
            VoteMQProducer voteMQProducer
    ) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.bookmarkMapper = bookmarkMapper;
        this.postVoteMapper = postVoteMapper;
        this.commentVoteMapper = commentVoteMapper;
        this.communityService = communityService;
        this.eventPublisher = eventPublisher;
        this.postCacheService = postCacheService;
        this.redissonClient = redissonClient;
        this.userStatsCacheService = userStatsCacheService;
        this.feedService = feedService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisLuaScriptService = redisLuaScriptService;
        this.voteMQProducer = voteMQProducer;
    }

    public PageResponse<PostDtos.PostDto> list(String filter, String communitySlug, String search, long page, long size, CurrentUser currentUser) {
        return list(filter, communitySlug, search, null, page, size, currentUser);
    }

    public PageResponse<PostDtos.PostDto> list(String filter, String communitySlug, String search, String author, long page, long size, CurrentUser currentUser) {
        // 深分页熔断保护：超过 500 条直接截断，杜绝深度 offset 导致的慢查询与临时表排序
        if (page * size > 500) {
            return new PageResponse<>(List.of(), page, size, 0, false);
        }

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>().eq(Post::getIsPublic, true);
        if (communitySlug != null && !communitySlug.isBlank()) {
            wrapper.eq(Post::getCommunityId, communityService.requireBySlug(communitySlug).getId());
        }
        if (author != null && !author.isBlank()) {
            User authorUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, author.trim()));
            if (authorUser != null) {
                wrapper.eq(Post::getAuthorId, authorUser.getId());
            } else {
                return new PageResponse<>(List.of(), page, size, 0, false);
            }
        }
        if (search != null && !search.isBlank()) {
            String trimmed = search.trim();
            // 搜索词长度保护：小于 2 字符不触发全表索引扫描风暴
            if (trimmed.length() < 2) {
                return new PageResponse<>(List.of(), page, size, 0, false);
            }
            // 激活 V7 迁移中建立的 MATCH(title, content) AGAINST(? IN BOOLEAN MODE) ngram 全文倒排索引
            wrapper.apply("MATCH(title, content) AGAINST({0} IN BOOLEAN MODE)", trimmed);
        }
        if ("new".equals(filter)) {
            wrapper.orderByDesc(Post::getCreatedAt);
        } else {
            wrapper.orderByDesc(Post::getVotes).orderByDesc(Post::getCreatedAt);
        }
        IPage<Post> result = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<Post> records = result.getRecords();
        if (records.isEmpty()) {
            return new PageResponse<>(List.of(), result.getCurrent(), result.getSize(), result.getTotal(), false);
        }

        List<PostDtos.PostDto> items = assemblePostDtos(records, currentUser);
        return new PageResponse<>(items, result.getCurrent(), result.getSize(), result.getTotal(), result.getCurrent() < result.getPages());
    }

    public PostDtos.PostDto detail(Long id, CurrentUser currentUser) {
        Post post = requirePost(id);
        trackPostUV(id, currentUser);
        return toDto(post, currentUser);
    }

    private void trackPostUV(Long postId, CurrentUser currentUser) {
        String key = CacheConstants.POST_UV_KEY + postId;
        String member = currentUser != null ? String.valueOf(currentUser.id()) : "anonymous-" + System.nanoTime();
        try {
            stringRedisTemplate.opsForHyperLogLog().add(key, member);
        } catch (Exception e) {
            log.warn("Failed to track UV for post {}", postId, e);
        }
    }

    private long getPostUVCount(Long postId) {
        try {
            Long count = stringRedisTemplate.opsForHyperLogLog().size(CacheConstants.POST_UV_KEY + postId);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.warn("Failed to get UV count for post {}", postId, e);
            return 0;
        }
    }

    @Transactional
    public PostDtos.PostDto create(PostDtos.CreatePostRequest request, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能发布帖子");
        }
        Community community = communityService.requireBySlug(request.community());
        Post post = new Post();
        post.setId(SnowflakeIdGenerator.getNextId());
        post.setAuthorId(currentUser.id());
        post.setCommunityId(community.getId());
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setImageUrl(request.image());
        post.setPostType(request.type() != null && !request.type().isBlank() ? request.type() : "DISCUSSION");
        post.setVotes(0);
        post.setCommentsCount(0);
        post.setIsPublic(request.isPublic());
        post.setStatus("NORMAL");
        post.setCreatedAt(LocalDateTime.now());
        postMapper.insert(post);

        userStatsCacheService.incrementPostCount(currentUser.id());
        postCacheService.invalidatePost(post.getId());

        try {
            feedService.fanOutToFollowers(currentUser.id(), post.getId());
        } catch (Exception e) {
            log.error("Failed to fan out post to followers for post {}", post.getId(), e);
        }

        return toDto(post, currentUser);
    }

    public List<PostDtos.CommentDto> comments(Long postId, CurrentUser currentUser) {
        requirePost(postId);
        List<Comment> comments = commentMapper.selectList(new LambdaQueryWrapper<Comment>()
                .eq(Comment::getPostId, postId)
                .orderByAsc(Comment::getCreatedAt));

        if (comments.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = comments.stream().map(Comment::getAuthorId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Map<Long, Integer> userVotes = Collections.emptyMap();
        if (currentUser != null) {
            List<Long> commentIds = comments.stream().map(Comment::getId).toList();
            if (!commentIds.isEmpty()) {
                userVotes = commentVoteMapper.selectList(new LambdaQueryWrapper<CommentVote>()
                                .eq(CommentVote::getUserId, currentUser.id())
                                .in(CommentVote::getCommentId, commentIds))
                        .stream()
                        .collect(Collectors.toMap(CommentVote::getCommentId, CommentVote::getValue));
            }
        }

        Map<Long, Integer> finalUserVotes = userVotes;
        return comments.stream().map(c -> {
            User author = userMap.get(c.getAuthorId());
            PostDtos.AuthorDto authorDto = author != null
                    ? new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl())
                    : new PostDtos.AuthorDto("unknown", "未知用户", null);
            int uv = finalUserVotes.getOrDefault(c.getId(), 0);
            return new PostDtos.CommentDto(
                    String.valueOf(c.getId()),
                    authorDto,
                    c.getContent(),
                    TimeText.from(c.getCreatedAt()),
                    c.getVotes(),
                    uv
            );
        }).toList();
    }

    @Transactional
    public PostDtos.CommentDto addComment(Long postId, PostDtos.CreateCommentRequest request, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "您需要先登录才能发表评论");
        }
        requirePost(postId);
        Comment comment = new Comment();
        comment.setId(SnowflakeIdGenerator.getNextId());
        comment.setPostId(postId);
        comment.setAuthorId(currentUser.id());
        comment.setContent(request.content());
        comment.setVotes(0);
        comment.setCreatedAt(LocalDateTime.now());
        commentMapper.insert(comment);

        postMapper.updateCommentsCountAtomic(postId, 1);
        postCacheService.invalidatePost(postId);

        User author = userMapper.selectById(currentUser.id());
        PostDtos.AuthorDto authorDto = new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl());

        return new PostDtos.CommentDto(
                String.valueOf(comment.getId()),
                authorDto,
                comment.getContent(),
                "刚刚",
                0,
                0
        );
    }

    public PostDtos.PostDto votePost(Long postId, int value, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        if (value != 1 && value != -1 && value != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VOTE", "投票分值无效");
        }

        Post post = requirePost(postId);

        List<Long> result = redisLuaScriptService.executeVoteScript(postId, currentUser.id(), value);
        long oldVote = (result != null && !result.isEmpty()) ? result.get(0) : 0;
        long newVote = (result != null && result.size() > 1) ? result.get(1) : value;
        long totalVotes = (result != null && result.size() > 2) ? result.get(2) : post.getVotes() + (value - oldVote);

        VoteMessage message = new VoteMessage(
                postId,
                currentUser.id(),
                (int) newVote,
                (int) (newVote - oldVote),
                System.currentTimeMillis(),
                UUID.randomUUID().toString()
        );
        voteMQProducer.sendVoteMessage(message);

        postCacheService.invalidatePost(postId);

        post.setVotes((int) totalVotes);

        PostDtos.PostDto baseDto = toDto(post, currentUser);
        return new PostDtos.PostDto(
                baseDto.id(),
                baseDto.author(),
                baseDto.community(),
                baseDto.title(),
                baseDto.content(),
                baseDto.image(),
                baseDto.timestamp(),
                (int) totalVotes,
                baseDto.comments(),
                baseDto.type(),
                baseDto.bookmarked(),
                (int) newVote,
                baseDto.viewCount()
        );
    }

    @Transactional
    public PostDtos.CommentDto voteComment(Long commentId, int value, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        if (value != 1 && value != -1 && value != 0) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_VOTE", "投票分值无效");
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在");
        }

        CommentVote existing = commentVoteMapper.selectOne(new LambdaQueryWrapper<CommentVote>()
                .eq(CommentVote::getCommentId, commentId)
                .eq(CommentVote::getUserId, currentUser.id()));

        int oldVal = existing != null ? existing.getValue() : 0;
        int delta = value - oldVal;

        if (delta != 0) {
            if (value == 0 && existing != null) {
                commentVoteMapper.deleteById(existing.getId());
            } else if (existing != null) {
                existing.setValue(value);
                commentVoteMapper.updateById(existing);
            } else {
                CommentVote cv = new CommentVote();
                cv.setId(SnowflakeIdGenerator.getNextId());
                cv.setCommentId(commentId);
                cv.setUserId(currentUser.id());
                cv.setValue(value);
                commentVoteMapper.insert(cv);
            }
            commentMapper.updateVotesAtomic(commentId, delta);
            comment.setVotes(comment.getVotes() + delta);
        }

        User author = userMapper.selectById(comment.getAuthorId());
        PostDtos.AuthorDto authorDto = author != null
                ? new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl())
                : new PostDtos.AuthorDto("unknown", "未知用户", null);

        return new PostDtos.CommentDto(
                String.valueOf(comment.getId()),
                authorDto,
                comment.getContent(),
                "刚刚",
                comment.getVotes(),
                value
        );
    }

    @Transactional
    public PostDtos.PostDto bookmark(Long postId, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Post post = requirePost(postId);
        boolean exists = bookmarkMapper.exists(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getPostId, postId)
                .eq(Bookmark::getUserId, currentUser.id()));
        if (!exists) {
            Bookmark b = new Bookmark();
            b.setId(SnowflakeIdGenerator.getNextId());
            b.setPostId(postId);
            b.setUserId(currentUser.id());
            b.setCreatedAt(LocalDateTime.now());
            bookmarkMapper.insert(b);
        }
        return toDto(post, currentUser);
    }

    @Transactional
    public PostDtos.PostDto removeBookmark(Long postId, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Post post = requirePost(postId);
        bookmarkMapper.delete(new LambdaQueryWrapper<Bookmark>()
                .eq(Bookmark::getPostId, postId)
                .eq(Bookmark::getUserId, currentUser.id()));
        return toDto(post, currentUser);
    }

    public PageResponse<PostDtos.PostDto> bookmarks(long page, long size, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        IPage<Bookmark> bPage = bookmarkMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Bookmark>()
                        .eq(Bookmark::getUserId, currentUser.id())
                        .orderByDesc(Bookmark::getCreatedAt));
        List<Bookmark> bList = bPage.getRecords();
        if (bList.isEmpty()) {
            return new PageResponse<>(List.of(), bPage.getCurrent(), bPage.getSize(), bPage.getTotal(), false);
        }
        List<Long> postIds = bList.stream().map(Bookmark::getPostId).toList();
        List<Post> posts = postMapper.selectBatchIds(postIds);

        Map<Long, Post> postMap = posts.stream().collect(Collectors.toMap(Post::getId, Function.identity()));
        List<Post> orderedPosts = postIds.stream().map(postMap::get).filter(Objects::nonNull).toList();

        List<PostDtos.PostDto> items = assemblePostDtos(orderedPosts, currentUser);
        return new PageResponse<>(items, bPage.getCurrent(), bPage.getSize(), bPage.getTotal(), bPage.getCurrent() < bPage.getPages());
    }

    @Transactional
    public void deletePost(Long id, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Post post = requirePost(id);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.role());
        if (!post.getAuthorId().equals(currentUser.id()) && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "您无权删除该帖子");
        }
        postMapper.deleteById(id);
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getPostId, id));
        bookmarkMapper.delete(new LambdaQueryWrapper<Bookmark>().eq(Bookmark::getPostId, id));
        postVoteMapper.delete(new LambdaQueryWrapper<PostVote>().eq(PostVote::getPostId, id));

        userStatsCacheService.decrementPostCount(post.getAuthorId());
        postCacheService.invalidatePost(id);
    }

    @Transactional
    public void deleteComment(Long commentId, CurrentUser currentUser) {
        if (currentUser == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Comment comment = commentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在");
        }
        boolean isAdmin = "ADMIN".equalsIgnoreCase(currentUser.role());
        if (!comment.getAuthorId().equals(currentUser.id()) && !isAdmin) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "您无权删除该评论");
        }
        commentMapper.deleteById(commentId);
        commentVoteMapper.delete(new LambdaQueryWrapper<CommentVote>().eq(CommentVote::getCommentId, commentId));
        postMapper.updateCommentsCountAtomic(comment.getPostId(), -1);
        postCacheService.invalidatePost(comment.getPostId());
    }

    public Post requirePost(Long id) {
        Post post = postCacheService.queryWithPassThrough(id);
        if (post == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "POST_NOT_FOUND", "帖子不存在");
        }
        return post;
    }

    public List<PostDtos.PostDto> assemblePostDtos(List<Post> posts, CurrentUser currentUser) {
        if (posts == null || posts.isEmpty()) {
            return List.of();
        }

        Set<Long> userIds = posts.stream().map(Post::getAuthorId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        Set<Long> communityIds = posts.stream().map(Post::getCommunityId).collect(Collectors.toSet());
        Map<Long, Community> communityMap = communityService.mapByIds(communityIds);

        Map<Long, Integer> userVotes = Collections.emptyMap();
        Set<Long> bookmarkedIds = Collections.emptySet();

        if (currentUser != null) {
            List<Long> postIds = posts.stream().map(Post::getId).toList();
            userVotes = postVoteMapper.selectList(new LambdaQueryWrapper<PostVote>()
                            .eq(PostVote::getUserId, currentUser.id())
                            .in(PostVote::getPostId, postIds))
                    .stream()
                    .collect(Collectors.toMap(PostVote::getPostId, PostVote::getValue));

            bookmarkedIds = bookmarkMapper.selectList(new LambdaQueryWrapper<Bookmark>()
                            .eq(Bookmark::getUserId, currentUser.id())
                            .in(Bookmark::getPostId, postIds))
                    .stream()
                    .map(Bookmark::getPostId)
                    .collect(Collectors.toSet());
        }

        Map<Long, Integer> finalUserVotes = userVotes;
        Set<Long> finalBookmarkedIds = bookmarkedIds;

        return posts.stream().map(p -> {
            User author = userMap.get(p.getAuthorId());
            PostDtos.AuthorDto authorDto = author != null
                    ? new PostDtos.AuthorDto(author.getUsername(), author.getDisplayName(), author.getAvatarUrl())
                    : new PostDtos.AuthorDto("unknown", "未知用户", null);

            Community comm = communityMap.get(p.getCommunityId());
            PostDtos.CommunityRefDto commDto = comm != null
                    ? new PostDtos.CommunityRefDto(comm.getSlug(), comm.getName())
                    : new PostDtos.CommunityRefDto("unknown", "未知社区");

            int userVote = finalUserVotes.getOrDefault(p.getId(), 0);
            if (currentUser != null && userVote == 0) {
                try {
                    String v = stringRedisTemplate.opsForValue().get("vote:post:" + p.getId() + ":" + currentUser.id());
                    if (v != null) {
                        userVote = Integer.parseInt(v);
                    }
                } catch (Exception ignored) {}
            }

            boolean bookmarked = finalBookmarkedIds.contains(p.getId());
            long uv = getPostUVCount(p.getId());

            return new PostDtos.PostDto(
                    String.valueOf(p.getId()),
                    authorDto,
                    commDto,
                    p.getTitle(),
                    p.getContent(),
                    p.getImageUrl(),
                    TimeText.from(p.getCreatedAt()),
                    p.getVotes(),
                    p.getCommentsCount(),
                    p.getPostType(),
                    bookmarked,
                    userVote,
                    uv
            );
        }).toList();
    }

    public PostDtos.PostDto toDto(Post post, CurrentUser currentUser) {
        return assemblePostDtos(List.of(post), currentUser).get(0);
    }
}
