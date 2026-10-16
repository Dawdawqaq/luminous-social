package com.luminous.api.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.luminous.api.common.BusinessException;
import com.luminous.api.post.PostMapper;
import com.luminous.api.security.CurrentUser;
import com.luminous.api.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Test void botCannotLoginEvenWithMatchingPasswordAndCannotBeFollowed() {
        sampleUser.setAccountType("BOT");when(userMapper.selectOne(any())).thenReturn(sampleUser);
        assertThatThrownBy(()->authService.login(new AuthDtos.LoginRequest("Lumi","[E2E_TEST]"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->authService.follow("Lumi",new CurrentUser(42L,"[E2E_TEST]"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(jwtService,passwordEncoder,followPolicy,eventPublisher);
    }
    @Test void reservedBotUsernameCannotBeRegistered() {
        assertThatThrownBy(()->authService.register(new AuthDtos.RegisterRequest("lUMi","[E2E_TEST]","Password123!"))).isInstanceOf(BusinessException.class);
        verifyNoInteractions(userMapper,jwtService);
    }

    @Mock
    private UserMapper userMapper;

    @Mock
    private PostMapper postMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserFollowMapper userFollowMapper;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.luminous.api.cache.UserStatsCacheService userStatsCacheService;

    @InjectMocks
    private AuthService authService;
    @Mock private com.luminous.api.growth.GrowthQueryService growthQueries;
    @Mock private FollowActionPolicy followPolicy;
    @Mock private com.luminous.api.feed.FeedService feedService;

    @Test
    void repeatedFollowReturnsDesiredStateWithoutPublishingOrUpdatingCounts() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        when(followPolicy.change(42L,100L,true)).thenReturn(false);
        assertThat(authService.follow("test_user",new CurrentUser(42L,"[E2E_TEST] actor")).isFollowing()).isTrue();
        verifyNoInteractions(eventPublisher,feedService);
        verify(userStatsCacheService,never()).incrementFollowerCount(any());
        verify(followPolicy,never()).shouldNotify(anyLong(),anyLong());
    }

    @Test
    void repeatedUnfollowReturnsDesiredStateWithoutNewQuotaOrEffects() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        assertThat(authService.unfollow("test_user",new CurrentUser(42L,"[E2E_TEST] actor")).isFollowing()).isFalse();
        verifyNoInteractions(eventPublisher,feedService);
        verify(userStatsCacheService,never()).decrementFollowerCount(any());
    }

    @Test
    void newFollowPublishesOnlyWhenNotificationWindowAllows() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        when(followPolicy.change(42L,100L,true)).thenReturn(true);
        when(followPolicy.shouldNotify(42L,100L)).thenReturn(true);
        authService.follow("test_user",new CurrentUser(42L,"[E2E_TEST] actor"));
        verify(userStatsCacheService).incrementFollowerCount(100L);
        verify(userStatsCacheService).incrementFollowingCount(42L);
        verify(eventPublisher).publishEvent(any(com.luminous.api.notification.NotificationEvent.class));
    }

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(100L);
        sampleUser.setUsername("test_user");
        sampleUser.setDisplayName("Test Display");
        sampleUser.setPasswordHash("hashed_password");
        sampleUser.setRole("USER");
        sampleUser.setIsBanned(false);
        sampleUser.setJoinDate("2026-08");

        lenient().when(userStatsCacheService.getUserStats(any()))
                .thenReturn(new com.luminous.api.cache.UserStatsCacheService.Stats(5, 10, 8));
    }

    @Test
    void login_Success_DoesNotGrantAdminBackdoor() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        when(passwordEncoder.matches("raw_password", "hashed_password")).thenReturn(true);
        when(jwtService.createToken(100L, "test_user", "USER")).thenReturn("jwt_token_123");

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("test_user", "raw_password");
        AuthDtos.AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("jwt_token_123");
        assertThat(response.user().role()).isEqualTo("USER");
        // Verify user role was never updated to ADMIN
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void login_Fails_WhenUserIsBanned() {
        sampleUser.setIsBanned(true);
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        when(passwordEncoder.matches("raw_password", "hashed_password")).thenReturn(true);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("test_user", "raw_password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(be.getCode()).isEqualTo("USER_BANNED");
                });
    }

    @Test
    void login_Fails_WhenWrongPassword() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);
        when(passwordEncoder.matches("wrong_password", "hashed_password")).thenReturn(false);

        AuthDtos.LoginRequest request = new AuthDtos.LoginRequest("test_user", "wrong_password");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(be.getCode()).isEqualTo("BAD_CREDENTIALS");
                });
    }

    @Test
    void register_FirstUserRemainsRegularUser() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("encoded_secret");
        when(jwtService.createToken(any(), eq("admin_user"), eq("USER"))).thenReturn("admin_token");

        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest("admin_user", "Admin User", "secret");
        AuthDtos.AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("admin_token");
        assertThat(response.user().username()).isEqualTo("admin_user");
        assertThat(response.user().role()).isEqualTo("USER");
        assertThat(ProfilePresetCatalog.catalog().avatars()).extracting(ProfilePresetCatalog.Preset::url).contains(response.user().avatar());
        assertThat(ProfilePresetCatalog.catalog().banners()).extracting(ProfilePresetCatalog.Preset::url).contains(response.user().banner());
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_SubsequentUserBecomesRegularUser() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("encoded_secret");
        when(jwtService.createToken(any(), eq("regular_user"), eq("USER"))).thenReturn("regular_token");

        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest("regular_user", "Regular User", "secret");
        AuthDtos.AuthResponse response = authService.register(request);

        assertThat(response.token()).isEqualTo("regular_token");
        assertThat(response.user().username()).isEqualTo("regular_user");
        assertThat(response.user().role()).isEqualTo("USER");
        verify(userMapper).insert(any(User.class));
    }

    @Test
    void register_Fails_WhenUsernameExists() {
        when(userMapper.selectOne(any())).thenReturn(sampleUser);

        AuthDtos.RegisterRequest request = new AuthDtos.RegisterRequest("test_user", "Test Display", "secret");

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    @Test
    void register_PersistsChosenAvatar() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("encoded_secret");
        var response = authService.register(new AuthDtos.RegisterRequest("chosen_cat", "[E2E_TEST] 选头像", "secret", "pharaoh"));
        assertThat(response.user().avatar()).isEqualTo("/profile-presets/avatars/pharaoh.webp");
        var inserted = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getAvatarUrl()).isEqualTo(response.user().avatar());
    }

    @Test
    void register_RejectsUnlistedAvatarBeforeInsertingUser() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("encoded_secret");
        assertThatThrownBy(() -> authService.register(new AuthDtos.RegisterRequest("bad_cat", "[E2E_TEST] 非法头像", "secret", "/uploads/custom.png")))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("INVALID_AVATAR_PRESET");
                });
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    void follow_CannotFollowSelf() {
        CurrentUser currentUser = new CurrentUser(100L, "test_user", "USER");
        when(userMapper.selectOne(any())).thenReturn(sampleUser);

        assertThatThrownBy(() -> authService.follow("test_user", currentUser))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getCode()).isEqualTo("CANNOT_FOLLOW_SELF");
                });
    }

    @Test
    void searchUsers_EmptyOrBlank_ReturnsEmptyList() {
        assertThat(authService.searchUsers(null, null)).isEmpty();
        assertThat(authService.searchUsers("", null)).isEmpty();
        assertThat(authService.searchUsers("   ", null)).isEmpty();
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void searchUsers_ShortQueryLessThanTwoChars_ReturnsEmptyList() {
        assertThat(authService.searchUsers("a", null)).isEmpty();
        verify(userMapper, never()).selectList(any());
    }

    @Test
    void searchUsers_ValidQuery_ReturnsUsers() {
        when(userMapper.selectList(any())).thenReturn(List.of(sampleUser));
        when(userStatsCacheService.getUserStats(100L)).thenReturn(new com.luminous.api.cache.UserStatsCacheService.Stats(0, 0, 0));

        var results = authService.searchUsers("test", null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).username()).isEqualTo("test_user");
        verify(userMapper, times(1)).selectList(any());
    }
}
