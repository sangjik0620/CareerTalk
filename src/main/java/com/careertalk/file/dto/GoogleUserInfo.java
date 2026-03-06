package com.careertalk.file.dto;

import java.util.Map;

public class GoogleUserInfo implements OAuth2UserInfo {
    private Map<String, Object> attributes;

    public GoogleUserInfo(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String getProviderId() { return (String) attributes.get("sub"); }
    @Override
    public String getProvider() { return "google"; }
    @Override
    public String getEmail() { return (String) attributes.get("email"); }
    @Override
    public String getName() {
        return null;
    }
    // test
    @Override
    public String getPhone() { return (String) attributes.get("phone"); } // People API에서 넣은 값
    @Override
    public String getBirthDate() { return (String) attributes.get("birthDate"); } // People API에서 넣은 값
    @Override
    public Map<String, Object> getAttributes() { return attributes; }
}