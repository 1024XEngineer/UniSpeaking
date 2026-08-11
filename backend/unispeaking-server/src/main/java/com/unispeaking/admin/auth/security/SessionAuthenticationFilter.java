package com.unispeaking.admin.auth.security;

import com.unispeaking.admin.auth.application.AuthService;
import com.unispeaking.admin.auth.application.InvalidSessionException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class SessionAuthenticationFilter extends OncePerRequestFilter {
    public static final String COOKIE_NAME = "us-admin-session";

    private final AuthService authService;

    public SessionAuthenticationFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (!isAdminRoute(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }
        findCookie(request).ifPresent(rawToken -> {
            try {
                var administrator = authService.authenticate(rawToken);
                var authentication = new UsernamePasswordAuthenticationToken(
                        administrator,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + administrator.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (InvalidSessionException ignored) {
                SecurityContextHolder.clearContext();
            }
        });
        filterChain.doFilter(request, response);
    }

    public static boolean isAdminRoute(String requestUri) {
        return "/api/admin".equals(requestUri) || requestUri.startsWith("/api/admin/");
    }

    public static java.util.Optional<String> findCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return java.util.Optional.empty();
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }
}
