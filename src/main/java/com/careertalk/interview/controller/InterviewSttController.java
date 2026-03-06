package com.careertalk.interview.controller;

import com.careertalk.interview.service.InterviewSttFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/interview")
public class InterviewSttController {

    private final InterviewSttFacade sttFacade;

    @PostMapping("/stt")
    public String stt(@RequestParam String s3Key,
                      @RequestParam(required = false) String filename,
                      @RequestParam(defaultValue = "true") boolean toWav) throws Exception {
        // 반환값: transcript 텍스트(혹은 JSON으로 감싸서 반환해도 됨)
        return sttFacade.sttFromS3(s3Key, filename, toWav);
    }
}