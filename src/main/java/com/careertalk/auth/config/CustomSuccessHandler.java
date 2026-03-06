package com.careertalk.auth.config;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String loginId = (String) oAuth2User.getAttributes().get("loginId");

        // 1. 해당 소셜 계정(loginId)으로 이미 가입된 회원인지 확인
        Optional<Member> memberByLoginId = memberRepository.findByLoginId(loginId);

        String targetUrl;

        if (memberByLoginId.isPresent()) {
            // [기존 소셜 계정 유저] -> 메인 화면으로 이동
            Member member = memberByLoginId.get();
            targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/")
                    .queryParam("loginSuccess", true)
                    .queryParam("email", member.getEmail())
                    .queryParam("nickname", member.getNickname())
                    .queryParam("name", member.getName())
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
        } else {
            // [신규 소셜 시도] 이메일이 이미 다른 계정으로 등록되어 있는지 확인
            Optional<Member> memberByEmail = memberRepository.findByEmail(email);

            if (memberByEmail.isPresent()) {
                // ⭐ 이메일 중복 발생! -> 로그인 페이지로 리다이렉트하며 에러 코드 전달
                targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/login")
                        .queryParam("error", "duplicate_email")
                        .build()
                        .encode(StandardCharsets.UTF_8)
                        .toUriString();
            } else {
                // [진짜 신규 유저] -> 회원가입 페이지로 이동
                targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/social-signup")
                        .queryParam("email", email)
                        .queryParam("loginId", loginId)
                        .queryParam("name", (String) oAuth2User.getAttributes().get("name"))
                        .queryParam("phone", (String) oAuth2User.getAttributes().get("phone"))
                        .queryParam("birthDate", (String) oAuth2User.getAttributes().get("birthDate"))
                        .build()
                        .encode(StandardCharsets.UTF_8)
                        .toUriString();
            }
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}