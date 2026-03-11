package com.careertalk.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.Random;
import java.io.UnsupportedEncodingException;

@Service
@RequiredArgsConstructor // final 필드인 mailSender를 스프링이 자동으로 넣어줍니다.
public class EmailService {

    private final JavaMailSender mailSender; // static 제거, null 대입 제거

    // 6자리 난수 생성 (static 제거) test
    public String createCode() {
        Random random = new Random();
        return String.valueOf(random.nextInt(888888) + 111111);
    }

    // 이메일 발송 (static 제거)
    public void sendEmail(String toEmail, String code) throws MessagingException {
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        helper.setTo(toEmail);
        helper.setSubject("[CareerTalk] 회원가입 인증 번호입니다.");

        try {
            helper.setFrom(new InternetAddress("unducklife@gmail.com", "CareerTalk", "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            helper.setFrom("unducklife@gmail.com");
        }

        String content = "<div style='margin:20px; padding:20px; border:1px solid #e2e8f0; border-radius:15px; font-family:sans-serif;'>"
                + "<h2 style='color:#2563eb;'>CareerTalk 인증 번호</h2>"
                + "<p>안녕하세요! CareerTalk 가입을 위한 인증 번호입니다.</p>"
                + "<div style='background:#f8fafc; padding:15px; border-radius:10px; text-align:center; font-size:24px; font-weight:bold; letter-spacing:5px; color:#1e293b;'>"
                + code + "</div>"
                + "<p style='font-size:12px; color:#94a3b8; margin-top:20px;'>본 메일은 발신 전용입니다.</p>"
                + "</div>";

        helper.setText(content, true);

        mailSender.send(message);
    }
}