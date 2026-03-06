package com.careertalk.file.service;

import com.careertalk.file.dto.GoogleUserInfo;
import com.careertalk.file.dto.NaverUserInfo;
import com.careertalk.file.dto.KakaoUserInfo;
import com.careertalk.file.dto.OAuth2UserInfo;
import com.careertalk.file.entity.Member;
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
    private String loginId;

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
            userInfo = new NaverUserInfo(oAuth2User.getAttributes());
        } else if (provider.equals("kakao")) {
            userInfo = new KakaoUserInfo(oAuth2User.getAttributes());
        }

        // 3. SocialSignupRequestDTO와 DB의 loginId를 위한 공통 값 설정
        String loginId = userInfo.getProvider() + "_" + userInfo.getProviderId();
        attributes.put("email", userInfo.getEmail());
        attributes.put("name", userInfo.getName());
        attributes.put("phone", userInfo.getPhone());
        attributes.put("birthDate", userInfo.getBirthDate());
        attributes.put("loginId", loginId);

        // 4. 이메일이 아닌 loginId로 기존 회원 여부 판단
        Member member = memberService.findByLoginId(loginId);

        if (member == null) {
            // DB에 해당 loginId가 없으면 신규 유저
            attributes.put("isNewUser", true);
        } else {
            // DB에 있으면 기존 유저 (이메일이 같아도 loginId가 다르면 여기 안 들어옴)
            attributes.put("isNewUser", false);
            // 기존 유저의 경우 DB에 저장된 실제 정보를 attributes에 덮어씌울 수도 있습니다.
            attributes.put("nickname", member.getNickname());
        }

        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                userNameAttributeName
        );
    }
}