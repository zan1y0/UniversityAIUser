package com.kaoyan.ai.storage;

import static com.kaoyan.ai.common.Jsons.*;

import com.fasterxml.jackson.databind.node.*;
import com.kaoyan.ai.common.*;
import com.kaoyan.ai.persistence.RecordRepository;
import com.kaoyan.ai.security.SessionService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.sound.sampled.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  private final RecordRepository db;
  private final SessionService sessions;
  private final Path directory;
  private final com.kaoyan.ai.contract.ContractRegistry contract;
  private final AudioInspector audio;

  public FileService(
      RecordRepository db,
      SessionService sessions,
      com.kaoyan.ai.contract.ContractRegistry contract,
      AudioInspector audio,
      @Value("${app.files-directory}") String directory)
      throws IOException {
    this.db = db;
    this.sessions = sessions;
    this.contract = contract;
    this.audio = audio;
    this.directory = Path.of(directory).toAbsolutePath().normalize();
    Files.createDirectories(this.directory);
  }

  private Path location(String id) {
    try {
      UUID.fromString(id);
    } catch (Exception e) {
      throw ApiException.missing();
    }
    Path p = directory.resolve(id).normalize();
    if (!p.startsWith(directory)) throw ApiException.missing();
    return p;
  }

  public ObjectNode metadata(String id, String user, String purpose) {
    ObjectNode f = db.get("files", id, user);
    require(purpose == null || f.path("purpose").asText().equals(purpose), "文件用途不匹配");
    return f;
  }

  public byte[] bytes(String id, String user, String purpose) {
    metadata(id, user, purpose);
    try {
      return Files.readAllBytes(location(id));
    } catch (IOException e) {
      throw ApiException.missing();
    }
  }

  @Transactional
  public ResponseEntity<?> upload(MultipartFile file, String purpose, HttpServletRequest r) {
    String user = sessions.user(r);
    require(Set.of("qaImage", "avatarAudio", "interviewAudio").contains(purpose), "文件用途不合法");
    require(file.getSize() > 0 && file.getSize() <= 10485760, "文件不能为空且不能超过10MiB");
    try {
      byte[] bytes = file.getBytes();
      String actual = detect(bytes);
      require(actual != null && actual.equals(file.getContentType()), "文件内容与声明的 MIME 类型不匹配");
      require(purpose.equals("qaImage") == actual.startsWith("image/"), "文件类型不符合当前用途");
      Double duration = null;
      if (actual.startsWith("audio/")) duration = audio.duration(bytes, actual);
      String fid = id();
      ObjectNode n =
          obj(
              "id",
              fid,
              "purpose",
              purpose,
              "mimeType",
              actual,
              "sizeBytes",
              bytes.length,
              "durationSeconds",
              duration,
              "createdAt",
              now());
      db.create("files", fid, user, n);
      Files.write(location(fid), bytes, StandardOpenOption.CREATE_NEW);
      return ResponseEntity.status(201)
          .body(
              obj(
                  "code",
                  "OK",
                  "message",
                  "上传成功",
                  "data",
                  n,
                  "traceId",
                  r.getAttribute("traceId")));
    } catch (IOException e) {
      throw new ApiException(500, "INTERNAL_ERROR", "文件保存失败");
    }
  }

  public ResponseEntity<?> download(String id, HttpServletRequest r) {
    contract.query("op007", r);
    contract.request(
        "op007", com.fasterxml.jackson.databind.node.NullNode.instance, Map.of("id", id), r);
    String user = sessions.user(r);
    ObjectNode meta = metadata(id, user, null);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(meta.path("mimeType").asText()))
        .header("Content-Disposition", "attachment; filename=\"" + id + "\"")
        .body(bytes(id, user, null));
  }

  public void delete(String id, String user) {
    metadata(id, user, null);
    for (ObjectNode c : db.list("job_commands", user))
      if (c.path("body").toString().contains(id)) {
        ObjectNode job = db.get("jobs", c.path("jobId").asText(), user);
        if (Set.of("queued", "running").contains(job.path("status").asText()))
          throw ApiException.state("文件仍在任务中使用，请先停止任务");
      }
    try {
      Files.deleteIfExists(location(id));
      db.delete("files", id, user);
    } catch (IOException e) {
      throw new ApiException(500, "INTERNAL_ERROR", "文件删除失败");
    }
  }

  private String detect(byte[] b) {
    if (b.length < 12) return null;
    if ((b[0] & 255) == 0xff && (b[1] & 255) == 0xd8 && (b[2] & 255) == 0xff) return "image/jpeg";
    if (Arrays.equals(Arrays.copyOf(b, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10}))
      return "image/png";
    String h = new String(b, 0, 12, java.nio.charset.StandardCharsets.ISO_8859_1);
    if (h.startsWith("RIFF") && h.endsWith("WEBP")) return "image/webp";
    if (h.startsWith("RIFF") && h.endsWith("WAVE")) return "audio/wav";
    if (h.startsWith("OggS")) return "audio/ogg";
    if (h.startsWith("ID3") || ((b[0] & 255) == 255 && (b[1] & 224) == 224)) return "audio/mpeg";
    if ((b[0] & 255) == 26 && (b[1] & 255) == 69 && (b[2] & 255) == 223 && (b[3] & 255) == 163)
      return "audio/webm";
    return null;
  }
}
