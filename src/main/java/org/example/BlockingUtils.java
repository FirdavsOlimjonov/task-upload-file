package org.example;

import io.netty.channel.ChannelException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;


@Slf4j
public class BlockingUtils {
//    public static String baseUrl(ServerHttpRequest request) {
//        URI uri = request.getURI();
//        String port = "";
//        if (uri.getPort() != 80)
//            port = String.format(":%s", uri.getPort());
//
//        return String.format("%s://%s%s", uri.getScheme(), uri.getHost(), port);
//    }

    public static Boolean createFile(FilePart filePart, String folder, String fileName) {
        try {
            String fullPath = folder + "/" + fileName;
            Path path = Files.createFile(Paths.get(fullPath).toAbsolutePath().normalize());
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(path, StandardOpenOption.WRITE);

            DataBufferUtils
                    .write(filePart.content(), channel, 0)
                    .publishOn(Schedulers.boundedElastic())
                    .retryWhen(Retry.backoff(5,Duration.ofSeconds(5)))
                    .doOnComplete(() -> {
                        try {
                            channel.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    })
                    .doOnError(e -> !(e instanceof ChannelException), e -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exc) {
                            exc.printStackTrace();
                        }
                    })
                    .doOnError(ChannelException.class, e -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exc) {
                            exc.printStackTrace();
                        }
                    })
                    .doOnTerminate(() -> {
                        try {
                            channel.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    })
                    .subscribe();
            return true;
        } catch (IOException e) {
            log.error("ERROR CreateFile: {}", e.getMessage());
        }
        return false;
    }

    public static String generateFileName(String fileName) {
        StringBuilder str = new StringBuilder();

        for (int i = 0; i < fileName.length(); i++)
            if (fileName.charAt(i) == ' ') str.append('_');
            else str.append(fileName.charAt(i));

        return str.toString();
    }
}
