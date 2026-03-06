package com.careertalk.auth.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.people.v1.PeopleService;
import com.google.api.services.people.v1.model.Date;
import com.google.api.services.people.v1.model.Person;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
public class GooglePeopleService {

    // application.properties에 등록한 API Key를 가져옵니다.
    @Value("${google.api.key}")
    private String apiKey;

    /**
     * 구글 액세스 토큰을 사용하여 추가 정보(전화번호, 생년월일)를 가져옵니다.
     */
    public Map<String, String> getExtraInfo(String accessToken) {
        Map<String, String> extraInfo = new HashMap<>();

        try {
            // 1. 구글 People API 서비스 객체 생성
            PeopleService peopleService = new PeopleService.Builder(
                    GoogleNetHttpTransport.newTrustedTransport(),
                    GsonFactory.getDefaultInstance(),
                    null)
                    .setApplicationName("CareerTalk")
                    .build();

            // 2. 'people/me' 경로로 전화번호와 생년월일 필드를 요청합니다.
            Person profile = peopleService.people().get("people/me")
                    .setPersonFields("phoneNumbers,birthdays")
                    .setAccessToken(accessToken) // 인증 토큰 세팅
                    .setKey(apiKey)              // API 키 세팅
                    .execute();

            // 3. 전화번호 추출 (여러 개가 있을 수 있어 첫 번째 것을 가져옵니다)
            if (profile.getPhoneNumbers() != null && !profile.getPhoneNumbers().isEmpty()) {
                extraInfo.put("phone", profile.getPhoneNumbers().get(0).getValue());
            }

            // 4. 생년월일 추출 (YYYY-MM-DD 형식으로 변환)
            if (profile.getBirthdays() != null && !profile.getBirthdays().isEmpty()) {
                Date date = profile.getBirthdays().get(0).getDate();
                if (date != null) {
                    // 월과 일이 1자리일 경우 앞에 0을 붙여 포맷팅 (예: 1995-05-01)
                    String birthDate = String.format("%d-%02d-%02d",
                            date.getYear(), date.getMonth(), date.getDay());
                    extraInfo.put("birthDate", birthDate);
                }
            }

        } catch (Exception e) {
            // 에러 발생 시 로그를 출력하고 빈 맵을 반환합니다.
            System.err.println("Google People API 호출 중 오류 발생: " + e.getMessage());
            e.printStackTrace();
        }

        return extraInfo;
    }
}