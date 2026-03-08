package com.careertalk.auth.service;

import com.careertalk.auth.dto.GoogleUserInfo;
import com.careertalk.auth.dto.NaverUserInfo;
import com.careertalk.auth.dto.KakaoUserInfo;
import com.careertalk.auth.dto.OAuth2UserInfo;
import com.careertalk.auth.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final GooglePeopleService googlePeopleService;
    private final MemberService memberService;

    // 🚨 절대 금지: private String loginId; (전역 변수 삭제 완료)

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String provider = userRequest.getClientRegistration().getRegistrationId();

        String userNameAttributeName = userRequest.getClientRegistration()
                .getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName();

        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());

        // 1. 구글일 경우에만 People API 호출 (추가 정보 획득)
        if (provider.equals("google")) {
            String accessToken = userRequest.getAccessToken().getTokenValue();
            Map<String, String> extraInfo = googlePeopleService.getExtraInfo(accessToken);
            if (extraInfo.get("phone") != null) attributes.put("phone", extraInfo.get("phone"));
            if (extraInfo.get("birthDate") != null) attributes.put("birthDate", extraInfo.get("birthDate"));
        }

        // 2. 소셜별 userInfo 객체 생성
        OAuth2UserInfo userInfo = null;
        if (provider.equals("google")) {
            userInfo = new GoogleUserInfo(attributes);
        } else if (provider.equals("naver")) {
            userInfo = new NaverUserInfo(attributes); // 원본 oAuth2User.getAttributes() 대신 수정 가능한 attributes 사용 권장
        } else if (provider.equals("kakao")) {
            userInfo = new KakaoUserInfo(attributes);
        }

        // 3. 지역 변수(Local Variable)로 loginId 안전하게 생성
        String loginId = userInfo.getProvider() + "_" + userInfo.getProviderId();
        attributes.put("email", userInfo.getEmail());
        attributes.put("name", userInfo.getName());
        attributes.put("phone", userInfo.getPhone());
        attributes.put("birthDate", userInfo.getBirthDate());
        attributes.put("loginId", loginId);

        // 4. 이메일이 아닌 loginId로 기존 회원 여부 판단
        Member member = memberService.findByLoginId(loginId);

        if (member == null) {
            attributes.put("isNewUser", true);
        } else {
            attributes.put("isNewUser", false);
            attributes.put("nickname", member.getNickname());
        }

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                userNameAttributeName
        );
    }
}