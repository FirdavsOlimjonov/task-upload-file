package org.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.file.Paths;

import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE;

@Slf4j
@RestController
@RequestMapping("file")
public class FileController {
    @Value("${file.upload-dir}")
    private String uploadDir;
    @Value("${file.baseUrl}")
    private String baseUrl;

    @PostMapping("upload")
    public Mono<String> upload(@RequestPart("file") FilePart filePart) {
        String fileName = System.nanoTime() + "-" + BlockingUtils.generateFileName(filePart.filename());
        return Mono.fromCallable(() ->
                        BlockingUtils.createFile(filePart, uploadDir, fileName))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(createFileState -> {
                    Object resp = false;
                    if (createFileState)
                        resp = baseUrl + "/file/download/" + fileName;
                    return Mono.just(resp.toString());
                });
    }

    @GetMapping(value = "/download/{fileName}", produces = APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<Resource>> downloadCsv(@PathVariable("fileName") String fileName) {
        return Mono.<Resource>fromCallable(() -> {
                    String fileLocation = uploadDir + "/" + fileName;
                    String path = Paths.get(fileLocation).toAbsolutePath().normalize().toString();
                    return new FileSystemResource(path);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(resource -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentDispositionFormData(fileName, fileName);
                    return Mono.just(ResponseEntity
                            .ok().cacheControl(CacheControl.noCache())
                            .headers(headers)
                            .body(resource));
                });
    }
}
