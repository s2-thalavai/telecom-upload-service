package com.telecom.util;

import com.telecom.support.TestFiles;
import org.junit.jupiter.api.Test;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class FileTypeDetectorTest {

    private static BufferedInputStream stream(byte[] bytes) {
        return new BufferedInputStream(new ByteArrayInputStream(bytes));
    }

    @Test
    void detectsPdf() {
        assertThat(FileTypeDetector.detect(stream(TestFiles.pdf(100)))).contains("application/pdf");
    }

    @Test
    void detectsPng() {
        assertThat(FileTypeDetector.detect(stream(TestFiles.png(100)))).contains("image/png");
    }

    @Test
    void detectsJpeg() {
        assertThat(FileTypeDetector.detect(stream(TestFiles.jpeg(100)))).contains("image/jpeg");
    }

    @Test
    void unknownSignatureIsEmpty() {
        assertThat(FileTypeDetector.detect(stream(TestFiles.exe(100)))).isEmpty();
    }

    @Test
    void inputShorterThanSignatureIsEmpty() {
        assertThat(FileTypeDetector.detect(stream(new byte[]{0x25, 0x50}))).isEmpty();
        assertThat(FileTypeDetector.detect(stream(new byte[0]))).isEmpty();
    }

    @Test
    void detectionDoesNotConsumeTheStream() throws IOException {
        byte[] bytes = TestFiles.pdf(50);
        BufferedInputStream in = stream(bytes);

        FileTypeDetector.detect(in);

        assertThat(in.readAllBytes()).isEqualTo(bytes);
    }

    @Test
    void extensionsMatchTypes() {
        assertThat(FileTypeDetector.extensionFor("application/pdf")).isEqualTo(".pdf");
        assertThat(FileTypeDetector.extensionFor("image/png")).isEqualTo(".png");
        assertThat(FileTypeDetector.extensionFor("image/jpeg")).isEqualTo(".jpg");
        assertThat(FileTypeDetector.extensionFor("text/plain")).isEqualTo(".bin");
    }
}
