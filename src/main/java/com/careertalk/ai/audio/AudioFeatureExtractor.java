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

    public Double getMeanVolumeDb(Path audioPath) throws Exception {
        List<String> cmd = List.of(
                "ffmpeg",
                "-i", audioPath.toAbsolutePath().toString(),
                "-af", "volumedetect",
                "-f", "null",
                "-"
        );

        String stderr = runWithStderr(cmd);

        String key = "mean_volume:";
        int idx = stderr.lastIndexOf(key);
        if (idx < 0) return null;

        String tail = stderr.substring(idx + key.length()).trim();
        String num = tail.split("\\s+")[0];
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

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        Process p = pb.start();

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (InputStream is = p.getInputStream()) {
            is.transferTo(output);
        }

        int code = p.waitFor();
        String out = output.toString(StandardCharsets.UTF_8);

        if (code != 0 && out.isBlank()) {
            throw new IllegalStateException("Command failed: " + String.join(" ", cmd));
        }

        return out;
    }

    public Double getSilenceRatio(Path audioPath) throws Exception {
        List<String> cmd = List.of(
                "ffmpeg",
                "-hide_banner",
                "-i", audioPath.toAbsolutePath().toString(),
                "-af", "silencedetect=noise=-35dB:d=0.3",
                "-f", "null",
                "-"
        );

        String stderr = runWithStderr(cmd);

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
        if (ratio < 0) ratio = 0;
        if (ratio > 1) ratio = 1;
        return ratio;
    }
}