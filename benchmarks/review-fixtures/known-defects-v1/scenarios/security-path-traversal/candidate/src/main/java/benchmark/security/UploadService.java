package benchmark.security;

import java.nio.file.Path;

public class UploadService {
    private final Path uploadRoot;

    public UploadService(Path uploadRoot) {
        this.uploadRoot = uploadRoot;
    }

    public Path destination(String fileName) {
        return uploadRoot.resolve(fileName);
    }
}
