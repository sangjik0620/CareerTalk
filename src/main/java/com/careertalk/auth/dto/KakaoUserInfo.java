package com.careertalk.auth.dto;

import java.util.Map;

public class KakaoUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;
    private Map<String, Object> kakaoAccount;

    public KakaoUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
        // 카카오는 유저 정보가 kakao_account라는 키 안에 맵으로 들어있습니다.
        this.kakaoAccount = (Map<String, Object>) attributes.get("kakao_account");
    }

    @Override
    public String getProviderId() {
        return String.valueOf(attributes.get("id")); // 카카오의 고유 번호(Long 타입을 String으로)
    }

    @Override
    public String getProvider() {
        return "kakao";
    }

    @Override
    public String getEmail() {
        return (String) kakaoAccount.get("email");
    }

    @Override
    public String getName() {
        // 비즈 앱이 아닐 경우 실명을 가져올 수 없으므로 null을 반환하여
        // 프론트엔드에서 직접 입력받게 유도합니다.
        return null;
    }

    @Override
    public String getPhone() {
        return null; // 개인 개발자는 가져올 수 없으므로 null
    }

    @Override
    public String getBirthDate() {
        return null; // 개인 개발자는 가져올 수 없으므로 null
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }
}