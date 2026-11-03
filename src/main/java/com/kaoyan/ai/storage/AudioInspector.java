package com.kaoyan.ai.storage;

import static com.kaoyan.ai.common.Jsons.*;

import com.kaoyan.ai.common.ApiException;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** WAV 使用 JDK 解码；其他格式可通过本机配置的 ffprobe 校验真实时长。 */
@Component
public class AudioInspector {
  private final String ffprobe;

  public AudioInspector(@Value("${app.ffprobe-executable:}") String ffprobe) {
    this.ffprobe = ffprobe;
  }

  public double duration(byte[] bytes, String mime) {
    if (mime.equals("audio/wav")) {
      try (AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
        double seconds = (double) in.getFrameLength() / in.getFormat().getFrameRate();
        require(Double.isFinite(seconds) && seconds > 0 && seconds <= 600, "音频时长须为0至600秒");
        return seconds;
      } catch (IOException | UnsupportedAudioFileException e) {
        throw new ApiException(422, "VALIDATION_FAILED", "音频解码失败");
      }
    }
    if (ffprobe.isBlank())
      throw new ApiException(
          503,
          "DEPENDENCY_UNAVAILABLE",
          "请配置 APP_FFPROBE_EXECUTABLE 以校验 MP3、WebM 和 OGG 时长，或使用 WAV 音频");
    Path file = null;
    Process process = null;
    try {
      file = Files.createTempFile("study-audio-", ".bin");
      Files.write(file, bytes);
      process =
          new ProcessBuilder(
                  ffprobe,
                  "-v",
                  "error",
                  "-show_entries",
                  "format=duration",
                  "-of",
                  "json",
                  file.toString())
              .redirectErrorStream(true)
              .start();
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new ApiException(504, "PROVIDER_TIMEOUT", "音频检查超时");
      }
      byte[] output = process.getInputStream().readNBytes(4096);
      require(process.exitValue() == 0, "音频格式无法解码");
      String value =
          parse(new String(output, java.nio.charset.StandardCharsets.UTF_8))
              .path("format")
              .path("duration")
              .asText();
      double seconds = Double.parseDouble(value);
      require(Double.isFinite(seconds) && seconds > 0 && seconds <= 600, "音频时长无法核实或超过600秒");
      return seconds;
    } catch (ApiException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "音频检查已停止");
    } catch (Exception e) {
      throw new ApiException(503, "DEPENDENCY_UNAVAILABLE", "音频工具不可用或未提供可核实时长");
    } finally {
      if (process != null && process.isAlive()) process.destroyForcibly();
      if (file != null)
        try {
          Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
  }
}
