package com.devmate.generation.service;

import com.devmate.generation.entity.GenerationRun;
import com.devmate.generation.model.ProjectBundle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class GeneratedProjectStoreTest {
    @TempDir Path temporary;
    ProjectBundle bundle(String path, String content) { return new ProjectBundle("sample", "实际小项目", "阅读README运行", List.of(new ProjectBundle.ProjectFile("README.md","运行说明"),new ProjectBundle.ProjectFile(path,content)),List.of()); }
    GenerationRun run() { var run=new GenerationRun();run.setId(2L);run.setSessionId(1L);run.setAttemptKey("test-attempt-000001");return run; }
    @Test void rejectsTraversalAbsoluteSecretAndDependencyPathsBeforeWriting() {
        var store=new GeneratedProjectStore(temporary.toString());
        for(String path:List.of("../outside.py","/absolute.py","C:/test.py","a\\test.py","a/../../out.py",".env",".git/config","node_modules/a.js","a//b.py")) {
            assertThatThrownBy(() -> store.write(run(),bundle(path,"unsafe"))).hasMessageContaining("路径");
        }
        assertThat(temporary.toFile().list()).isEmpty();
    }
    @Test void rejectsDuplicatePathsOversizedFilesAndDocumentOnlyResults() {
        var store=new GeneratedProjectStore(temporary.toString());
        assertThatThrownBy(() -> store.validate(bundle("readme.MD","duplicate"))).hasMessageContaining("重复");
        assertThatThrownBy(() -> store.validate(bundle("main.py","x".repeat(50001)))).hasMessageContaining("大小");
        assertThatThrownBy(() -> store.validate(bundle("notes.md","only docs"))).hasMessageContaining("源码");
    }
    @Test void refusesSymbolicLinksAndAllowsOrdinaryMultilanguageSource() throws Exception {
        Path real=temporary.toRealPath(); Path elsewhere=Files.createDirectory(real.resolve("elsewhere"));
        Path root=real.resolve("linked"); Files.createSymbolicLink(root,elsewhere);
        var linked=new GeneratedProjectStore(root.toString());
        assertThatThrownBy(() -> linked.write(run(),bundle("main.py","print('hi')"))).hasMessageContaining("符号链接");
        var store=new GeneratedProjectStore(real.resolve("safe").toString());
        assertThat(store.write(run(),bundle("src/main.ts","console.log('hi')"))).hasSize(3);
        assertThat(store.read(run(),"src/main.ts")).isEqualTo("console.log('hi')");
    }
}
