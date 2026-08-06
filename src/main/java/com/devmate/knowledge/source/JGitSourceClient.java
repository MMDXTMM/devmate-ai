package com.devmate.knowledge.source;

import com.devmate.knowledge.config.SourceImportProperties;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class JGitSourceClient implements GitSourceClient {

    private final SourceImportProperties properties;
    private final GitCredentialsProviderFactory credentialsProviderFactory;

    public JGitSourceClient(
            SourceImportProperties properties,
            GitCredentialsProviderFactory credentialsProviderFactory
    ) {
        this.properties = properties;
        this.credentialsProviderFactory = credentialsProviderFactory;
    }

    @Override
    public GitCloneResult cloneRepository(
            String repositoryUrl,
            String branch,
            Path targetDirectory
    ) {
        String branchRef = Constants.R_HEADS + branch;
        CloneCommand command = Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(targetDirectory.toFile())
                .setBranch(branchRef)
                .setBranchesToClone(List.of(branchRef))
                .setDepth(properties.getCloneDepth())
                .setTimeout(properties.getCloneTimeoutSeconds());
        credentialsProviderFactory.create().ifPresent(command::setCredentialsProvider);

        try (Git git = command.call()) {
            ObjectId head = git.getRepository().resolve(Constants.HEAD);
            if (head == null) {
                throw new SourceImportException("Git仓库没有可读取的提交");
            }
            return new GitCloneResult(targetDirectory, head.name());
        } catch (GitAPIException | java.io.IOException exception) {
            String message = credentialsProviderFactory.isConfigured()
                    ? "Git仓库克隆失败，请检查网络、地址、分支以及Token的仓库读取权限"
                    : "Git仓库克隆失败，请检查网络、地址和分支；私有仓库还需配置Git访问Token";
            throw new SourceImportException(message, exception);
        }
    }
}
