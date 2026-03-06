package com.careertalk.auth.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import java.util.Random;

@Service
@RequiredArgsConstructor // final 필드인 mailSender를 스프링이 자동으로 넣어줍니다.
public class EmailService {

    private final JavaMailSender mailSender; // static 제거, null 대입 제거

    // 6자리 난수 생성 (static 제거)
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
        helper.setText("인증 번호: <b>" + code + "</b>", true);

        mailSender.send(message);
    }
}