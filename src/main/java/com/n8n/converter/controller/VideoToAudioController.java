package com.n8n.converter.controller;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

@RestController
public class VideoToAudioController {

    private static final Logger log = LoggerFactory.getLogger(VideoToAudioController.class);

    @Value("${converter.temp-dir}")
    private String tempDir;

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(new File(tempDir).toPath());
    }


    @PostMapping("/file-to-text")
    public ResponseEntity<String> convertToText(@RequestParam("file") MultipartFile file) {
        try {
            // Read file content
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            return ResponseEntity.ok(content);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Error reading file");
        }
    }

    @PostMapping("/video-to-audio")
    public ResponseEntity<Resource> convert(@RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        String id = UUID.randomUUID().toString();
        File videoFile = new File(tempDir, id + "-video.mp4");
        File audioFile = new File(tempDir, id + "-audio.mp3");

        try {

            file.transferTo(videoFile);

            int[] bitrates = {32}; // fallback ladder 64,48
            boolean success = false;

            for (int bitrate : bitrates) {

                ProcessBuilder pb = new ProcessBuilder(
                        "ffmpeg",

                        "-ss", "45",                         // remove otter watermark intro
                        "-i", videoFile.getAbsolutePath(),

                        "-map", "a",                         // extract only audio
                        "-vn",

                        "-af", "loudnorm",                   // normalize speech

                        "-ac", "1",                          // mono
                        "-ar", "16000",                      // whisper preferred

                        "-c:a", "libmp3lame",
                        "-b:a", bitrate + "k",

                        "-y",
                        audioFile.getAbsolutePath()
                );

                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (InputStream is = process.getInputStream()) {
                    is.transferTo(OutputStream.nullOutputStream());
                }

                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    log.error("ffmpeg failed with bitrate {}", bitrate);
                    continue;
                }

                if (!audioFile.exists() || audioFile.length() == 0) {
                    continue;
                }

                long sizeMB = audioFile.length() / (1024 * 1024);

                if (sizeMB <= 25) {
                    success = true;
                    log.info("Audio generated successfully with bitrate {}k ({} MB)", bitrate, sizeMB);
                    break;
                }

                log.info("Audio {} MB too large. Retrying with lower bitrate.", sizeMB);
            }

            if (!success) {
                log.error("Unable to compress audio below 25MB");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            videoFile.delete();

            FileInputStream audioStream = new FileInputStream(audioFile) {
                @Override
                public void close() throws IOException {
                    super.close();
                    audioFile.delete();
                }
            };

            InputStreamResource resource = new InputStreamResource(audioStream);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=audio.mp3")
                    .contentLength(audioFile.length())
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (IOException | InterruptedException e) {

            log.error("Conversion failed for request {}", id, e);

            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();

        } finally {

            if (videoFile.exists()) {
                videoFile.delete();
            }
        }
    }
}
