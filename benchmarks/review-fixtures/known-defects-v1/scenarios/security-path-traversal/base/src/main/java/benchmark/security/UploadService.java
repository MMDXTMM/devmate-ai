package benchmark.security;

import java.nio.file.Path;

public class UploadService {
    private final Path uploadRoot;

    public UploadService(Path uploadRoot) {
        this.uploadRoot = uploadRoot.toAbsolutePath().normalize();
    }

    public Path destination(String fileName) {
        Path destination = uploadRoot.resolve(fileName).normalize();
        if (!destination.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("invalid file name");
        }
        return destination;
    }
}
