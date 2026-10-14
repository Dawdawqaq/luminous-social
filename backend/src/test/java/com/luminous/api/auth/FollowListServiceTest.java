package com.luminous.api.auth;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.luminous.api.common.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FollowListServiceTest {
    @Mock private UserMapper userMapper;
    @Mock private UserFollowMapper followMapper;
    @Mock private com.luminous.api.growth.GrowthQueryService growthQueries;
    @InjectMocks private AuthService service;
    private User owner;
    private User person;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(100L);
        owner.setUsername("e2e_owner");
        person = new User();
        person.setId(9007199254740993L);
        person.setUsername("e2e_peer");
        person.setDisplayName("[E2E_TEST] 列表用户");
        person.setAvatarUrl("/uploads/avatar.png");
        person.setBio("公开简介");
    }

    @Test
    void followersUsesCorrectDirectionAndPreservesStringIds() {
        when(userMapper.selectOne(any())).thenReturn(owner);
        Page<User> result = new Page<>(1, 20, 21);
        result.setRecords(List.of(person));
        when(followMapper.selectFollowers(any(), eq(100L))).thenReturn(result);

        var response = service.listFollowers("e2e_owner", 1, 20);

        assertThat(response.items()).containsExactly(new AuthDtos.FollowUserDto(
                "9007199254740993", "e2e_peer", "[E2E_TEST] 列表用户", "/uploads/avatar.png", "公开简介"));
        assertThat(response.total()).isEqualTo(21);
        assertThat(response.hasNext()).isTrue();
        verify(followMapper, never()).selectFollowing(any(), any());
    }

    @Test
    void viewerRelationshipIsBatchLoadedForCurrentPage() {
        when(userMapper.selectOne(any())).thenReturn(owner);
        Page<User> result=new Page<>(1,20,1); result.setRecords(List.of(person));
        when(followMapper.selectFollowers(any(),eq(100L))).thenReturn(result);
        UserFollow relation=new UserFollow(); relation.setFollowerId(42L); relation.setFollowingId(person.getId());
        when(followMapper.selectList(any())).thenReturn(List.of(relation));
        var response=service.listFollowers("e2e_owner",1,20,new com.luminous.api.security.CurrentUser(42L,"[E2E_TEST] viewer"));
        assertThat(response.items().getFirst().isFollowing()).isTrue();
        verify(followMapper,times(1)).selectList(any());
    }

    @Test
    void followingPreservesPageAndDetectsLastPage() {
        when(userMapper.selectList(any())).thenReturn(List.of(owner));
        when(followMapper.selectFollowing(any(), eq(100L))).thenAnswer(invocation -> {
            Page<User> request = invocation.getArgument(0);
            assertThat(request.getCurrent()).isEqualTo(2);
            assertThat(request.getSize()).isEqualTo(20);
            request.setTotal(21);
            request.setRecords(List.of(person));
            return request;
        });

        var response = service.listFollowing("100", 2, 20);

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        verify(followMapper, never()).selectFollowers(any(), any());
    }

    @Test
    void emptyAndOutOfRangePagesReturnEmptyResults() {
        when(userMapper.selectOne(any())).thenReturn(owner);
        when(followMapper.selectFollowers(any(), any())).thenReturn(new Page<User>(1, 20, 0));
        when(followMapper.selectFollowing(any(), any())).thenReturn(new Page<User>(3, 20, 21));

        assertThat(service.listFollowers("e2e_owner", 1, 20).items()).isEmpty();
        var response = service.listFollowing("e2e_owner", 3, 20);
        assertThat(response.items()).isEmpty();
        assertThat(response.hasNext()).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"0,20", "-1,20", "1,0", "1,51", "9223372036854775807,50"})
    void invalidPaginationIsRejectedBeforeQuery(long page, long size) {
        assertThatThrownBy(() -> service.listFollowers("e2e_owner", page, size))
                .isInstanceOfSatisfying(BusinessException.class, error -> {
                    assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo("INVALID_PAGINATION");
                });
        verifyNoInteractions(userMapper, followMapper);
    }

    @Test
    void missingUserReturnsNotFoundInsteadOfEmptyList() {
        when(userMapper.selectOne(any())).thenReturn(null);
        assertThatThrownBy(() -> service.listFollowing("missing", 1, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(followMapper);
    }
}
