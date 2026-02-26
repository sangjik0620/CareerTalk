package com.careertalk.file.service;

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

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        // 1. 구글의 기본 정보(이메일, 이름 등)를 가져옵니다.
        OAuth2User oAuth2User = super.loadUser(userRequest);

        // 2. 가공을 위해 기존 attributes를 새로운 Map에 복사합니다.
        Map<String, Object> attributes = new HashMap<>(oAuth2User.getAttributes());

        // 3. 구글 액세스 토큰을 추출하여 People API 호출 준비를 합니다.
        String accessToken = userRequest.getAccessToken().getTokenValue();

        // 4. 1번 파일(GooglePeopleService)을 사용하여 전화번호와 생년월일을 가져옵니다.
        Map<String, String> extraInfo = googlePeopleService.getExtraInfo(accessToken);

        // 5. 가져온 추가 정보가 있다면 attributes 맵에 추가합니다.
        if (extraInfo.containsKey("phone")) {
            attributes.put("phone", extraInfo.get("phone"));
        }
        if (extraInfo.containsKey("birthDate")) {
            attributes.put("birthDate", extraInfo.get("birthDate"));
        }

        // 6. ⭐ 핵심: [Column 'login_id' cannot be null] 에러 해결 로직
        // DB의 login_id 컬럼은 필수이므로, 구글 고유 식별자(sub)를 활용해 자동으로 채워줍니다.
        String sub = (String) attributes.get("sub");
        attributes.put("loginId", "google_" + sub); // DB에 들어갈 login_id 값 생성

        // 7. 최종적으로 권한과 가공된 정보를 담은 OAuth2User 객체를 반환합니다.
        return new DefaultOAuth2User(
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "email" // 고유 식별 키를 email로 설정
        );
    }
}