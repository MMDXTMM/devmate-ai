package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JGitSourceClientTest {
    @TempDir
    Path directory;

    @Test
    void followsRemoteHeadForBlankBranchAndHonorsExplicitBranch() throws Exception {
        Path origin = directory.resolve("origin");
        String defaultRevision;
        String featureRevision;
        try (Git git = Git.init().setDirectory(origin.toFile()).setInitialBranch("trunk").call()) {
            Files.writeString(origin.resolve("README.md"), "default branch");
            git.add().addFilepattern(".").call();
            defaultRevision = git.commit().setMessage("default").setAuthor("Test", "test@example.com").call().name();
            git.checkout().setCreateBranch(true).setName("feature").call();
            Files.writeString(origin.resolve("README.md"), "feature branch");
            git.add().addFilepattern(".").call();
            featureRevision = git.commit().setMessage("feature").setAuthor("Test", "test@example.com").call().name();
            git.checkout().setName("trunk").call();
        }
        SourceImportProperties properties = new SourceImportProperties();
        JGitSourceClient client = new JGitSourceClient(properties, new GitCredentialsProviderFactory(properties));
        assertThat(client.cloneRepository(origin.toUri().toString(), null, directory.resolve("default")).revision())
                .isEqualTo(defaultRevision);
        assertThat(client.cloneRepository(origin.toUri().toString(), "feature", directory.resolve("explicit")).revision())
                .isEqualTo(featureRevision);
        assertThatThrownBy(() -> client.cloneRepository(origin.toUri().toString(), "missing", directory.resolve("missing")))
                .isInstanceOf(SourceImportException.class).hasMessageContaining("分支");
    }
}
