package com.letchat.security;

import com.letchat.entity.dto.TokenUserInfoDto;
import com.letchat.redis.RedisComponent;
import com.letchat.utils.StringTools;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class TokenAuthenticationFilter extends OncePerRequestFilter {

    private final RedisComponent redisComponent;

    public TokenAuthenticationFilter(RedisComponent redisComponent) {
        this.redisComponent = redisComponent;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = request.getHeader("token");
        if (!StringTools.isEmpty(token) && SecurityContextHolder.getContext().getAuthentication() == null) {
            TokenUserInfoDto user = redisComponent.getTokenUserInfoDto(token);
            if (user != null) {
                List<SimpleGrantedAuthority> authorities = Boolean.TRUE.equals(user.getAdmin())
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(user, token, authorities)
                );
            }
        }
        filterChain.doFilter(request, response);
    }
}
