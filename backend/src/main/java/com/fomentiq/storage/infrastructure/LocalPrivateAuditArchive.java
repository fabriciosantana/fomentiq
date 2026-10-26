package com.fomentiq.storage.infrastructure;

import com.fomentiq.storage.application.PrivateAuditArchive;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.GroupPrincipal;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.regex.Pattern;

/** Filesystem-backed private archive. It never deletes archived artifacts. */
public final class LocalPrivateAuditArchive implements PrivateAuditArchive {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]{1,180}");
    private static final String ROOT_MARKER = ".fomentiq-audit-archive-v1";
    private static final String ROOT_MARKER_CONTENT = "fomentiq-private-audit-archive-v1";
    private static final Set<PosixFilePermission> DIRECTORY_MODE = PosixFilePermissions.fromString("rwxr-x---");
    private static final Set<PosixFilePermission> OWNER_ONLY_DIRECTORY_MODE = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_MODE = PosixFilePermissions.fromString("rw-r-----");

    private final String configuredDirectory;
    private final String readerGroup;

    public LocalPrivateAuditArchive(String configuredDirectory, String readerGroup) {
        this.configuredDirectory = configuredDirectory == null ? "" : configuredDirectory.strip();
        this.readerGroup = readerGroup == null ? "" : readerGroup.strip();
    }

    @Override
    public String putImmutable(String relativeKey, byte[] content) {
        if (content == null) throw new IllegalArgumentException("Audit artifact content is required");
        Path root = requireRoot();
        Path target = resolveSafe(root, relativeKey);
        try {
            createPrivateDirectories(root, target.getParent());
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                requireRegularFile(target);
                if (!java.util.Arrays.equals(Files.readAllBytes(target), content)) {
                    throw new AuditArchiveException("immutable_artifact_conflict");
                }
                return relativeKey;
            }
            Path staging = Files.createTempFile(target.getParent(), ".audit-", ".tmp");
            try {
                setGroup(staging);
                Files.setPosixFilePermissions(staging, FILE_MODE);
                try (var channel = java.nio.channels.FileChannel.open(staging, StandardOpenOption.WRITE,
                        StandardOpenOption.TRUNCATE_EXISTING)) {
                    var buffer = java.nio.ByteBuffer.wrap(content);
                    while (buffer.hasRemaining()) channel.write(buffer);
                    channel.force(true);
                }
                try {
                    Files.createLink(target, staging);
                } catch (FileAlreadyExistsException raced) {
                    requireRegularFile(target);
                    if (!java.util.Arrays.equals(Files.readAllBytes(target), content)) {
                        throw new AuditArchiveException("immutable_artifact_conflict");
                    }
                }
            } finally {
                Files.deleteIfExists(staging);
            }
            setGroup(target);
            Files.setPosixFilePermissions(target, FILE_MODE);
            try (var directory = java.nio.channels.FileChannel.open(target.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
            return relativeKey;
        } catch (IOException | UnsupportedOperationException failure) {
            throw new AuditArchiveException("audit_archive_write_failed");
        }
    }

    @Override
    public byte[] read(String relativeKey) {
        Path root = requireRoot();
        Path target = resolveSafe(root, relativeKey);
        try {
            requireNoSymlinks(root, target);
            requireRegularFile(target);
            return Files.readAllBytes(target);
        } catch (IOException | UnsupportedOperationException failure) {
            throw new AuditArchiveException("audit_archive_read_failed");
        }
    }

    @Override
    public boolean contains(String relativeKey) {
        Path root = requireRoot();
        Path target = resolveSafe(root, relativeKey);
        try {
            requireNoSymlinks(root, target);
            return Files.exists(target, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException failure) {
            throw new AuditArchiveException("audit_archive_read_failed");
        }
    }

    private Path requireRoot() {
        if (configuredDirectory.isBlank() || readerGroup.isBlank()) {
            throw new AuditArchiveException("audit_archive_configuration_missing");
        }
        try {
            Path requested = Path.of(configuredDirectory).toAbsolutePath().normalize();
            Path workspace = Path.of("").toRealPath();
            if (requested.startsWith(workspace) || requested.getParent() == null) {
                throw new AuditArchiveException("audit_archive_path_not_private");
            }
            requireNoSymlinkComponents(requested);
            if (!Files.exists(requested, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isDirectory(requested.getParent(), LinkOption.NOFOLLOW_LINKS)) {
                    throw new AuditArchiveException("audit_archive_parent_missing");
                }
                Files.createDirectory(requested, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_MODE));
                initializeRoot(requested);
            }
            requireNoSymlinkComponents(requested);
            Path root = requested.toRealPath(LinkOption.NOFOLLOW_LINKS);
            if (root.startsWith(workspace) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                    || !isDedicatedRoot(root)) {
                throw new AuditArchiveException("audit_archive_path_not_private");
            }
            return root;
        } catch (IOException | UnsupportedOperationException failure) {
            throw new AuditArchiveException("audit_archive_configuration_invalid");
        }
    }

    private void initializeRoot(Path root) throws IOException {
        setGroup(root);
        Files.setPosixFilePermissions(root, DIRECTORY_MODE);
        Path marker = root.resolve(ROOT_MARKER);
        Files.writeString(marker, ROOT_MARKER_CONTENT, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        setGroup(marker);
        Files.setPosixFilePermissions(marker, FILE_MODE);
        try (var file = java.nio.channels.FileChannel.open(marker, StandardOpenOption.WRITE)) { file.force(true); }
        try (var directory = java.nio.channels.FileChannel.open(root, StandardOpenOption.READ)) { directory.force(true); }
    }

    private boolean isDedicatedRoot(Path root) throws IOException {
        Path marker = root.resolve(ROOT_MARKER);
        if (Files.isSymbolicLink(marker) || !Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) return false;
        String markerText = Files.readString(marker);
        if (!ROOT_MARKER_CONTENT.equals(markerText)) return false;
        GroupPrincipal actualGroup = Files.readAttributes(root, java.nio.file.attribute.PosixFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).group();
        GroupPrincipal configuredGroup = root.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByGroupName(readerGroup);
        GroupPrincipal markerGroup = Files.readAttributes(marker, java.nio.file.attribute.PosixFileAttributes.class,
                LinkOption.NOFOLLOW_LINKS).group();
        return actualGroup.equals(configuredGroup)
                && markerGroup.equals(configuredGroup)
                && DIRECTORY_MODE.equals(Files.getPosixFilePermissions(root, LinkOption.NOFOLLOW_LINKS))
                && FILE_MODE.equals(Files.getPosixFilePermissions(marker, LinkOption.NOFOLLOW_LINKS));
    }

    private Path resolveSafe(Path root, String relativeKey) {
        if (relativeKey == null || relativeKey.isBlank()) throw new IllegalArgumentException("Audit key is required");
        Path relative = Path.of(relativeKey);
        if (relative.isAbsolute()) throw new IllegalArgumentException("Audit key must be relative");
        for (Path segment : relative) {
            if (!SAFE_SEGMENT.matcher(segment.toString()).matches() || segment.toString().equals(".")) {
                throw new IllegalArgumentException("Audit key contains an invalid segment");
            }
        }
        Path target = root.resolve(relative).normalize();
        if (!target.startsWith(root) || target.equals(root)) throw new IllegalArgumentException("Audit key escapes root");
        return target;
    }

    private void createPrivateDirectories(Path root, Path directory) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                    throw new AuditArchiveException("audit_archive_path_not_private");
                }
            } else {
                Files.createDirectory(current, PosixFilePermissions.asFileAttribute(OWNER_ONLY_DIRECTORY_MODE));
            }
            setGroup(current);
            Files.setPosixFilePermissions(current, DIRECTORY_MODE);
        }
    }

    private void requireNoSymlinkComponents(Path path) throws IOException {
        Path current = path.getRoot();
        for (Path segment : path) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new AuditArchiveException("audit_archive_path_not_private");
            }
        }
    }

    private void requireNoSymlinks(Path root, Path target) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(target)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) throw new AuditArchiveException("audit_archive_path_not_private");
        }
    }

    private void requireRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new AuditArchiveException("audit_archive_file_invalid");
        }
    }

    private void setGroup(Path path) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class,
                LinkOption.NOFOLLOW_LINKS);
        if (view == null) throw new UnsupportedOperationException("POSIX permissions are required");
        GroupPrincipal group = path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByGroupName(readerGroup);
        view.setGroup(group);
    }

    public static final class AuditArchiveException extends RuntimeException {
        public AuditArchiveException(String code) { super(code); }
    }
}
