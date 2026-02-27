package com.careertalk.ai.audio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@Component
public class AudioFeatureExtractor {

    private final ObjectMapper om = new ObjectMapper();

    // ffprobe로 duration seconds 추출
    public double getDurationSeconds(Path audioPath) throws Exception {
        List<String> cmd = List.of(
                "ffprobe",
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "json",
                audioPath.toAbsolutePath().toString()
        );

        String out = run(cmd);
        JsonNode root = om.readTree(out);
        String durStr = root.path("format").path("duration").asText(null);
        if (durStr == null) return 0.0;
        return Double.parseDouble(durStr);
    }

    // ffmpeg volumedetect로 평균 음량(dB) 추출 (에너지 지표)
    public Double getMeanVolumeDb(Path audioPath) throws Exception {
        // volumedetect는 stderr로 출력됨
        List<String> cmd = List.of(
                "ffmpeg",
                "-i", audioPath.toAbsolutePath().toString(),
                "-af", "volumedetect",
                "-f", "null",
                "-"
        );

        String stderr = runWithStderr(cmd);

        // 예: "mean_volume: -18.4 dB"
        String key = "mean_volume:";
        int idx = stderr.lastIndexOf(key);
        if (idx < 0) return null;

        String tail = stderr.substring(idx + key.length()).trim(); // "-18.4 dB ..."
        String num = tail.split("\\s+")[0]; // "-18.4"
        return Double.parseDouble(num);
    }

    private String run(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (InputStream is = p.getInputStream()) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            is.transferTo(baos);
            int code = p.waitFor();
            String s = baos.toString(StandardCharsets.UTF_8);
            if (code != 0) throw new IllegalStateException("Command failed: " + String.join(" ", cmd) + "\n" + s);
            return s;
        }
    }

    private String runWithStderr(List<String> cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).start();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        try (InputStream is = p.getInputStream(); InputStream es = p.getErrorStream()) {
            is.transferTo(stdout);
            es.transferTo(stderr);
        }

        int code = p.waitFor();
        String err = stderr.toString(StandardCharsets.UTF_8);
        if (code != 0 && err.isBlank()) {
            throw new IllegalStateException("Command failed: " + String.join(" ", cmd));
        }
        return err;
    }

    public Double getSilenceRatio(Path audioPath) throws Exception {
        // dB 기준은 일단 -35dB, 최소 침묵 길이 0.3초(너무 민감하지 않게)
        // 필요하면 -30dB / 0.2s 등으로 조절
        List<String> cmd = List.of(
                "ffmpeg",
                "-i", audioPath.toAbsolutePath().toString(),
                "-af", "silencedetect=noise=-35dB:d=0.3",
                "-f", "null",
                "-"
        );

        String stderr = runWithStderr(cmd);

        // silence_duration: 1.234 형태들을 모두 찾아 합산
        double silenceTotal = 0.0;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("silence_duration:\\s*([0-9\\.]+)")
                .matcher(stderr);

        while (m.find()) {
            silenceTotal += Double.parseDouble(m.group(1));
        }

        double duration = getDurationSeconds(audioPath);
        if (duration <= 0) return null;

        double ratio = silenceTotal / duration;
        // 0~1 범위로 클램프
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return ratio;
    }
}