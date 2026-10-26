package com.fomentiq.storage.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPrivateAuditArchiveTest {
    @TempDir Path temporaryDirectory;

    @Test
    void writesImmutableArtifactsWithOwnerAndReviewerGroupOnlyPermissions() throws Exception {
        String group = Files.getAttribute(temporaryDirectory, "posix:group").toString();
        LocalPrivateAuditArchive archive = archive(temporaryDirectory.resolve("private-archive"), group);
        byte[] expected = "sanitized-audit-record".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        assertThat(archive.putImmutable("inputs/run/input.json", expected)).isEqualTo("inputs/run/input.json");
        assertThat(archive.read("inputs/run/input.json")).isEqualTo(expected);
        assertThat(archive(temporaryDirectory.resolve("private-archive"), group).read("inputs/run/input.json"))
                .isEqualTo(expected);
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                temporaryDirectory.resolve("private-archive")))).isEqualTo("rwxr-x---");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                temporaryDirectory.resolve("private-archive/inputs/run/input.json")))).isEqualTo("rw-r-----");
        assertThatThrownBy(() -> archive.putImmutable("inputs/run/input.json", "changed".getBytes()))
                .hasMessage("immutable_artifact_conflict");
    }

    @Test
    void refusesMissingConfigurationRepositoryPathsSymlinksAndTraversal() throws Exception {
        assertThatThrownBy(() -> new LocalPrivateAuditArchive("", "").putImmutable("a.bin", new byte[0]))
                .hasMessage("audit_archive_configuration_missing");

        String group = Files.getAttribute(temporaryDirectory, "posix:group").toString();
        assertThatThrownBy(() -> archive(Path.of("").toRealPath().resolve(".audit-test"), group)
                .putImmutable("a.bin", new byte[0])).hasMessage("audit_archive_path_not_private");
        assertThatThrownBy(() -> archive(temporaryDirectory.resolve("private-archive"), group)
                .putImmutable("../escape.bin", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);

        Path target = temporaryDirectory.resolve("real-directory");
        Files.createDirectory(target);
        var originalPermissions = Files.getPosixFilePermissions(target);
        assertThatThrownBy(() -> archive(target, group).putImmutable("a.bin", new byte[0]))
                .hasMessage("audit_archive_path_not_private");
        assertThat(Files.getPosixFilePermissions(target)).isEqualTo(originalPermissions);
        Path symlink = temporaryDirectory.resolve("archive-link");
        Files.createSymbolicLink(symlink, target);
        assertThatThrownBy(() -> archive(symlink, group).putImmutable("a.bin", new byte[0]))
                .hasMessage("audit_archive_path_not_private");
    }

    private LocalPrivateAuditArchive archive(Path directory, String group) {
        return new LocalPrivateAuditArchive(directory.toString(), group);
    }
}
