package com.careertalk.file.config;

import com.careertalk.file.entity.Member;
import com.careertalk.file.repository.MemberRepository; // 본인의 Repository 경로
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

@Component
@RequiredArgsConstructor
public class CustomSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final MemberRepository memberRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");

        // 1. DB에서 해당 이메일로 가입된 회원이 있는지 확인
        boolean isExist = memberRepository.findByEmail(email).isPresent();

        String targetUrl;
        if (isExist) {
            // 2-A. 기존 회원인 경우 -> 메인 화면으로 이동하며 정보 전달
            Member member = memberRepository.findByEmail(email).get(); // DB에서 회원 정보 가져오기

            targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/")
                    .queryParam("loginSuccess", true)
                    .queryParam("email", member.getEmail())
                    .queryParam("nickname", member.getNickname()) // 리액트가 환영 메시지에 쓸 닉네임
                    .queryParam("targetJob", member.getTargetJob())
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
        } else {
            // 2-B. 신규 회원인 경우 -> 추가 정보 입력 페이지로 이동 (전화번호 포함)
            targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/social-signup")
                    .queryParam("email", email)
                    .queryParam("name", (String) oAuth2User.getAttributes().get("name"))
                    .queryParam("loginId", (String) oAuth2User.getAttributes().get("loginId"))
                    .queryParam("phone", (String) oAuth2User.getAttributes().get("phone")) // 전화번호 전달
                    .queryParam("birthDate", (String) oAuth2User.getAttributes().get("birthDate"))
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
        }

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}