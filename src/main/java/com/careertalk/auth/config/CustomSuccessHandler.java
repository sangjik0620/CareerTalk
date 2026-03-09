package com.careertalk.auth.config;

import com.careertalk.auth.entity.Member;
import com.careertalk.auth.jwt.JwtUtil;
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
    private final JwtUtil jwtUtil;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = (String) oAuth2User.getAttributes().get("email");
        String loginId = (String) oAuth2User.getAttributes().get("loginId");

        Optional<Member> memberByLoginId = memberRepository.findByLoginId(loginId);

        String targetUrl;

        if (memberByLoginId.isPresent()) {
            // [1] 이미 가입된 소셜 계정이 있는 경우 -> 로그인 성공 처리
            Member member = memberByLoginId.get();
            String token = jwtUtil.createToken(member.getLoginId(), "ROLE_USER");

            targetUrl = UriComponentsBuilder.fromUriString("http://localhost:5173/")
                    .queryParam("loginSuccess", true)
                    .queryParam("token", token)
                    .queryParam("loginId", member.getLoginId())
                    .queryParam("email", member.getEmail())
                    .queryParam("nickname", member.getNickname())
                    .queryParam("name", member.getName())
                    .build()
                    .encode(StandardCharsets.UTF_8)
                    .toUriString();
        } else {
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

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}