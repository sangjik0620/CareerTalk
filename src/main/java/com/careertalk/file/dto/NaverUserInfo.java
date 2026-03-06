package com.careertalk.file.dto;

import java.util.Map;

public class NaverUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;

    public NaverUserInfo(Map<String, Object> attributes) {
        // 네이버는 "response" 안에 실제 정보가 들어있음
        this.attributes = (Map<String, Object>) attributes.get("response");
    }

    @Override
    public String getProviderId() { return (String) attributes.get("id"); }
    @Override
    public String getProvider() { return "naver"; }
    @Override
    public String getEmail() { return (String) attributes.get("email"); }
    @Override
    public String getName() { return (String) attributes.get("name"); }
    @Override
    public String getPhone() { return (String) attributes.get("mobile"); }
    @Override
    public String getBirthDate() {
        String year = (String) attributes.get("birthyear");
        String day = (String) attributes.get("birthday"); // MM-DD
        return year + "-" + day;
    }
    @Override
    public Map<String, Object> getAttributes() { return attributes; }
}