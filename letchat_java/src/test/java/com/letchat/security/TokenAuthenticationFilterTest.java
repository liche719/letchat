package com.letchat.security;

import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.redis.RedisComponent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenAuthenticationFilterTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesValidOpaqueToken() throws Exception {
        RedisComponent redis = mock(RedisComponent.class);
        TokenUserInfoDto user = new TokenUserInfoDto();
        user.setUserId("U1001");
        user.setAdmin(false);
        when(redis.getTokenUserInfoDto("valid-token")).thenReturn(user);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", "valid-token");

        new TokenAuthenticationFilter(redis).doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> {
                }
        );

        assertEquals(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        assertEquals("ROLE_USER", SecurityContextHolder.getContext().getAuthentication()
                .getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void leavesRequestAnonymousForUnknownToken() throws Exception {
        RedisComponent redis = mock(RedisComponent.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("token", "unknown-token");

        new TokenAuthenticationFilter(redis).doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> {
                }
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
