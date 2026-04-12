package com.java_template.common.auth;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * ABOUTME: Verifies OBO encryption key resolution logic: env-var property takes precedence
 * over file, file is used as fallback, and both-absent disables OBO.
 */
class OboPropertiesTest {

    @Test
    void resolveEncryptionKey_prefersPropertyOverFile(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "file-key-value");

        OboProperties props = new OboProperties();
        props.setEncryptionKey("property-key-value");
        props.setEncryptionKeyFile(keyFile.toString());

        assertThat(props.resolveEncryptionKey()).isEqualTo("property-key-value");
    }

    @Test
    void resolveEncryptionKey_fallsBackToFile(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "  file-key-value  \n");

        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile(keyFile.toString());

        assertThat(props.resolveEncryptionKey()).isEqualTo("file-key-value");
    }

    @Test
    void resolveEncryptionKey_returnsNullWhenBothAbsent() {
        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile("/nonexistent/path/key.txt");

        assertThat(props.resolveEncryptionKey()).isNull();
    }

    @Test
    void resolveEncryptionKey_returnsNullWhenFileIsEmpty(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "   ");

        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile(keyFile.toString());

        assertThat(props.resolveEncryptionKey()).isNull();
    }

    @Test
    void isEnabled_returnsTrueWhenKeyResolvable(@TempDir Path tempDir) throws IOException {
        Path keyFile = tempDir.resolve("key.txt");
        Files.writeString(keyFile, "some-key");

        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile(keyFile.toString());

        assertThat(props.isEnabled()).isTrue();
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    void resolveEncryptionKey_returnsNullWhenFileIsUnreadable(@TempDir Path tempDir) throws IOException {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
                "POSIX file permissions not supported on this filesystem");

        Path keyFile = tempDir.resolve("unreadable.txt");
        Files.writeString(keyFile, "secret-key");
        Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("---------"));

        // Tests running as root can read any file regardless of permissions; skip in that case.
        assumeTrue(!Files.isReadable(keyFile), "Test cannot run as root — file is still readable");

        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile(keyFile.toString());

        assertThat(props.resolveEncryptionKey()).isNull();
    }

    @Test
    void isEnabled_returnsFalseWhenNoKeyAvailable() {
        OboProperties props = new OboProperties();
        props.setEncryptionKey("");
        props.setEncryptionKeyFile("/nonexistent/path");

        assertThat(props.isEnabled()).isFalse();
    }
}
