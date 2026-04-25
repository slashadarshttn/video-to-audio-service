# video-to-audio-service

Spring Boot service used in an n8n pipeline for file conversion tasks:

- convert an uploaded video file to an MP3 audio file (via `ffmpeg`)
- convert HTML to a PDF (via `openhtmltopdf`)
- read an uploaded file and return its UTF-8 text content

## Requirements

- **Java**: 17
- **Maven**: `mvn`
- **ffmpeg**: installed and available on `PATH` (required for `/video-to-audio`)

## Configuration

Configuration lives in `src/main/resources/application.properties`.

- **HTTP server**
  - `server.port` (default `8080`)
- **Upload limits**
  - `spring.servlet.multipart.max-file-size` (default `600MB`)
  - `spring.servlet.multipart.max-request-size` (default `600MB`)
- **Temp working directory**
  - `converter.temp-dir` (default `${java.io.tmpdir}/video-to-audio`)

## Run locally

```bash
mvn spring-boot:run
```

Service starts on `http://localhost:8080`.

## HTTP API

### `POST /video-to-audio`

- **Request**: `multipart/form-data` with field `file`
- **Response**: binary download `audio.mp3`

Conversion behavior:

- trims the first **45 seconds** of the input (`-ss 45`)
- extracts audio only (`-map a -vn`)
- normalizes audio (`-af loudnorm`)
- outputs **mono**, **16kHz** MP3 (`libmp3lame`)
- attempts to keep output size **≤ 25MB** (returns 500 if unable)

Example:

```bash
curl -X POST "http://localhost:8080/video-to-audio" \
  -F "file=@/path/to/video.mp4" \
  --output audio.mp3
```

### `POST /html-to-pdf`

- **Request**: JSON body containing an `html` field
- **Response**: PDF download `notes.pdf`

Example:

```bash
curl -X POST "http://localhost:8080/html-to-pdf" \
  -H "Content-Type: application/json" \
  -d '{"html":"<html><body><h1>Hello</h1></body></html>"}' \
  --output notes.pdf
```

### `POST /file-to-text`

- **Request**: `multipart/form-data` with field `file`
- **Response**: raw text (assumes UTF-8)

Example:

```bash
curl -X POST "http://localhost:8080/file-to-text" \
  -F "file=@/path/to/file.txt"
```

## Tests

```bash
mvn test
```
