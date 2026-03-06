package com.careertalk.interview.service;

import com.careertalk.ai.stt.FfmpegConvertService;
import com.careertalk.ai.stt.OpenAiWhisperService;
import com.careertalk.file.s3.S3DownloadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
@RequiredArgsConstructor
public class InterviewSttFacade {

    private final S3DownloadService s3DownloadService;
    private final FfmpegConvertService ffmpegConvertService; // 필요 없으면 제거 가능
    private final OpenAiWhisperService openAiWhisperService;

    /**
     * @param s3Key S3에 저장된 webm 키
     * @param originalFilename 원본 파일명(확장자 판단용)
     * @param convertToWav true면 wav로 변환 후 STT
     */
    public String sttFromS3(String s3Key, String originalFilename, boolean convertToWav) throws Exception {
        Path tempWebm = null;
        Path tempWav = null;

        try {
            tempWebm = s3DownloadService.downloadToTempFile(s3Key, originalFilename);

            Path target = tempWebm;
            if (convertToWav) {
                tempWav = ffmpegConvertService.toWav16kMono(tempWebm);
                target = tempWav;
            }

            return openAiWhisperService.transcribe(target);

        } finally {
            // 임시파일 정리 (실무 필수)
            safeDelete(tempWav);
            safeDelete(tempWebm);
        }
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
