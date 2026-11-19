package com.mystores.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class MediaStorageService {
    private static final Map<String, String> IMAGE_TYPES = Map.of(
            "image/jpeg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif");
    private static final Map<String, String> VIDEO_TYPES = Map.of(
            "video/mp4", "mp4", "video/webm", "webm");
    private final Path directory;

    public MediaStorageService(@Value("${app.media.directory:./data/uploads}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String kind) {
        if (file == null || file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a file to upload.");
        boolean video = "video".equals(kind);
        long max = video ? 25L * 1024 * 1024 : 8L * 1024 * 1024;
        if (file.getSize() > max) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                video ? "Video must be 25 MB or smaller." : "Image must be 8 MB or smaller.");
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        String extension = (video ? VIDEO_TYPES : IMAGE_TYPES).get(contentType);
        if (extension == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                video ? "Upload an MP4 or WebM video." : "Upload a JPG, PNG, WebP, or GIF image.");
        String filename = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(filename));
            return "/api/media/" + filename;
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save uploaded media.", e);
        }
    }

    public Path resolve(String filename) {
        if (filename == null || !filename.matches("[0-9a-fA-F-]{36}\\.(jpg|png|webp|gif|mp4|webm)"))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        Path path = directory.resolve(filename).normalize();
        if (!path.getParent().equals(directory) || !Files.isRegularFile(path))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Media not found.");
        return path;
    }
}
