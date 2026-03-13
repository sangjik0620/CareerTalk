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
    private final FfmpegConvertService ffmpegConvertService;
    private final OpenAiWhisperService openAiWhisperService;

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
            safeDelete(tempWav);
            safeDelete(tempWebm);
        }
    }

    private void safeDelete(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
    }
}
