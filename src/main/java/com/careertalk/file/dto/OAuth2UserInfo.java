package com.careertalk.file.dto;

import java.util.Map;

public interface OAuth2UserInfo {
    String getProviderId();  // 고유 식별자 (sub 또는 id)
    String getProvider();    // google 또는 naver
    String getEmail();
    String getName();
    String getPhone();
    String getBirthDate();
    Map<String, Object> getAttributes();
}