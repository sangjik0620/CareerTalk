package com.careertalk.ai.stt;

import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class FfmpegConvertService {

    /**
     * webm -> wav (16kHz, mono)
     */
    public Path toWav16kMono(Path input) throws Exception {
        Path output = Files.createTempFile("careertalk-stt-", ".wav");

        ProcessBuilder pb = new ProcessBuilder(
                "ffmpeg",
                "-y",
                "-i", input.toAbsolutePath().toString(),
                "-ar", "16000",
                "-ac", "1",
                output.toAbsolutePath().toString()
        );

        pb.redirectErrorStream(true);
        Process p = pb.start();
        int code = p.waitFor();

        if (code != 0) {
            throw new IllegalStateException("ffmpeg convert failed. exit=" + code);
        }
        return output;
    }
}