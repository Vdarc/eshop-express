package com.traslation.backend.api;

import com.traslation.backend.common.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class UploadController {
    private static final Set<String> ALLOWED = Set.of("png", "jpg", "jpeg", "gif", "webp");
    private final Path root;
    private final JdbcTemplate db;

    public UploadController(
            @Value("${app.upload-dir:../backend/uploads}") String directory, JdbcTemplate db) {
        root = Path.of(directory).toAbsolutePath().normalize();
        this.db = db;
    }

    @PostMapping("/product/upload-image")
    public Map<String, Object> product(
            @RequestHeader("X-User-Id") long userId, @RequestParam("image") MultipartFile file) {
        return Map.of("code", 200, "data", upload(file, "product", "image_url"));
    }

    @PostMapping("/user/upload-avatar")
    public Map<String, Object> avatar(
            @RequestHeader("X-User-Id") long userId, @RequestParam("avatar") MultipartFile file) {
        Map<String, Object> result = upload(file, "avatar", "avatar_url");
        db.update(
                "update sys_users set avatar_url=? where user_id=?",
                result.get("avatar_url"),
                userId);
        return Map.of("code", 200, "data", result);
    }

    private Map<String, Object> upload(MultipartFile file, String folder, String field) {
        try {
            if (file == null || file.isEmpty())
                throw new BusinessException(HttpStatus.BAD_REQUEST, "没有上传图片");
            String original = Objects.toString(file.getOriginalFilename(), "");
            String ext =
                    original.contains(".")
                            ? original.substring(original.lastIndexOf('.') + 1)
                                    .toLowerCase(Locale.ROOT)
                            : "";
            if (!ALLOWED.contains(ext))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "仅支持 PNG、JPG、GIF、WEBP 图片");
            byte[] bytes = file.getBytes();
            if (!valid(bytes, ext))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "图片内容与文件格式不匹配");
            Path dir = root.resolve(folder).normalize();
            if (!dir.startsWith(root))
                throw new BusinessException(HttpStatus.BAD_REQUEST, "非法上传路径");
            Files.createDirectories(dir);
            String name = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Files.write(dir.resolve(name), bytes, StandardOpenOption.CREATE_NEW);
            String url = "/uploads/" + folder + "/" + name;
            return Map.of(field, url);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "图片上传失败");
        }
    }

    private boolean valid(byte[] b, String ext) {
        if (b.length < 12) return false;
        return switch (ext) {
            case "png" -> (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71;
            case "jpg", "jpeg" -> (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255;
            case "gif" -> b[0] == 'G' && b[1] == 'I' && b[2] == 'F';
            case "webp" ->
                    b[0] == 'R'
                            && b[1] == 'I'
                            && b[2] == 'F'
                            && b[3] == 'F'
                            && b[8] == 'W'
                            && b[9] == 'E'
                            && b[10] == 'B'
                            && b[11] == 'P';
            default -> false;
        };
    }
}
