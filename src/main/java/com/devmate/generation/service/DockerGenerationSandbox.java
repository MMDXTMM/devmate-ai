package com.devmate.generation.service;

import com.devmate.generation.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** No shell, host mounts, network, repository commands or model-selected images. */
@Component
public class DockerGenerationSandbox implements GenerationSandbox {
    private final String context;
    private final boolean enabled;
    private final ObjectMapper json;
    private static final int OUTPUT_LIMIT=16000;
    public DockerGenerationSandbox(ObjectMapper json,
            @Value("${devmate.generation.sandbox.enabled:false}") boolean enabled,
            @Value("${devmate.generation.sandbox.docker-context:default}") String context) {
        this.json=json; this.enabled=enabled; this.context=context;
    }
    public record Plan(String profile,String image,List<String> command,boolean tests,String blocked) { }
    public Plan plan(ProjectBundle b) {
        var paths=b.files().stream().map(ProjectBundle.ProjectFile::path).toList();
        if(paths.stream().anyMatch(p->p.endsWith(".py"))) {
            boolean external=b.files().stream().anyMatch(f->f.path().equals("pyproject.toml") || f.path().equals("Pipfile") ||
                    f.path().equals("requirements.txt") && f.content().lines().anyMatch(l->!l.isBlank()&&!l.strip().startsWith("#")));
            if(external) return blocked("PYTHON","存在外部Python依赖；当前离线容器不自动下载依赖，请按运行向导在本机验证");
            if(paths.stream().noneMatch(p->p.startsWith("tests/") && p.endsWith(".py"))) return blocked("PYTHON","未发现tests/下的Python测试，不能声明测试通过");
            return new Plan("PYTHON_UNITTEST","python:3.13.2-alpine3.21",List.of("python","-m","unittest","discover","-s","tests","-v"),true,null);
        }
        if(paths.stream().anyMatch(p->p.endsWith(".js") || p.endsWith(".mjs") || p.endsWith(".cjs"))) {
            try {
                var pkg=b.files().stream().filter(f->f.path().equals("package.json")).findFirst();
                if(pkg.isPresent()) { var value=json.readTree(pkg.get().content());
                    if(value.path("dependencies").size()>0 || value.path("devDependencies").size()>0) return blocked("NODE","项目需要npm依赖；当前仅支持无外部依赖的Node测试"); }
            } catch(Exception e) { return blocked("NODE","package.json无法解析，请先修正配置"); }
            var tests=paths.stream().filter(p->p.matches(".*(?:test|spec)\\.(?:js|mjs|cjs)$")).sorted().toList();
            if(tests.isEmpty()) return blocked("NODE","未发现Node原生测试文件（*.test.js / *.spec.mjs等）");
            var cmd=new ArrayList<>(List.of("node","--test")); cmd.add("--"); cmd.addAll(tests);
            return new Plan("NODE_TEST","node:22.14.0-alpine3.21",cmd,true,null);
        }
        if(paths.stream().anyMatch(p->p.endsWith(".java"))) {
            if(paths.contains("pom.xml") || paths.stream().anyMatch(p->p.endsWith(".gradle") || p.endsWith(".gradle.kts"))) return blocked("JAVA","Maven/Gradle项目需要依赖环境；当前离线验证仅支持无外部依赖Java源码编译");
            var cmd=new ArrayList<>(List.of("javac","-d","/tmp/classes")); paths.stream().filter(p->p.endsWith(".java")).sorted().map(path->"./"+path).forEach(cmd::add);
            return new Plan("JAVA_COMPILE","eclipse-temurin:21.0.6_7-jdk-alpine",cmd,false,null);
        }
        return blocked("UNSUPPORTED","此技术栈尚无受控执行配置；请使用运行向导验证，源码仍可下载");
    }
    private Plan blocked(String profile,String reason) { return new Plan(profile,"",List.of(),false,reason); }
    @Override public VerificationResult verify(ProjectBundle bundle) {
        var p=plan(bundle); long started=System.nanoTime();
        if(p.blocked()!=null) return result("BLOCKED",p,null,p.blocked(),started);
        if(!enabled) return result("BLOCKED",p,null,"隔离验证尚未启用；请配置Docker运行环境后重试",started);
        String name="devmate-check-"+UUID.randomUUID();
        // Each image uses its native runtime to unpack a validated JSON input.
        List<String> cmd=new ArrayList<>(List.of("docker","--context",context,"run","--rm","--pull=never","--name",name,
                "--network=none","--read-only","--cap-drop=ALL","--security-opt=no-new-privileges","--user=65534:65534",
                "--pids-limit=64","--memory=512m","--cpus=1","--ulimit","nofile=256:256","--log-driver=none",
                "--tmpfs","/work:rw,exec,nosuid,nodev,size=24m,mode=1777","--tmpfs","/tmp:rw,exec,nosuid,nodev,size=64m,mode=1777","--workdir=/work","-i",p.image()));
        // Java uses a trusted JShell harness; no generated build scripts or shell commands are accepted.
        if(p.profile().startsWith("PYTHON")) cmd.addAll(List.of("python","-I","-c",PYTHON_HARNESS));
        else if(p.profile().startsWith("NODE")) cmd.addAll(List.of("node","-e",NODE_HARNESS));
        else cmd.addAll(List.of("jshell","--execution","local","-"));
        Process process=null;
        try {
            process=new ProcessBuilder(cmd).redirectErrorStream(true).start();
            var capture=new ByteArrayOutputStream(); Process target=process;
            Thread drain=Thread.ofPlatform().daemon().start(()-> {
                try(var in=target.getInputStream()) { byte[] buf=new byte[2048];int n;while((n=in.read(buf))!=-1) synchronized(capture) { if(capture.size()<OUTPUT_LIMIT) capture.write(buf,0,Math.min(n,OUTPUT_LIMIT-capture.size())); } } catch(IOException ignored) { }
            });
            byte[] payload=p.profile().startsWith("JAVA")?javaHarness(bundle,p).getBytes(StandardCharsets.UTF_8):json.writeValueAsBytes(Map.of("files",bundle.files(),"command",p.command()));
            Thread.ofPlatform().daemon().start(()-> { try(var input=target.getOutputStream()) { input.write(payload); } catch(IOException ignored) { } });
            boolean done=process.waitFor(90,TimeUnit.SECONDS);
            if(!done) { process.destroyForcibly(); cleanup(name); drain.join(1000); return result("FAILED",p,null,"验证超过90秒，容器已停止",started); }
            drain.join(1000); String output; synchronized(capture) { output=capture.toString(StandardCharsets.UTF_8); }
            int code=process.exitValue();
            if(code==125 || code==126 || code==127 || output.contains("Cannot connect to the Docker daemon") || output.contains("failed to connect to the docker API")) return result("BLOCKED",p,code,"Docker不可用或白名单镜像未预装；请检查隔离环境",started);
            // Output cannot change process status. Empty/no-test success is never reported as passed tests.
            if(p.tests() && (output.contains("Ran 0 tests") || output.contains("# tests 0"))) return result("BLOCKED",p,code,"未执行任何测试；请补充有效测试",started);
            return result(code==0?"PASSED":"FAILED",p,code,output,started);
        } catch(Exception e) {
            if(process!=null) process.destroyForcibly(); cleanup(name);
            if(e instanceof InterruptedException) Thread.currentThread().interrupt();
            return result("BLOCKED",p,null,"无法启动隔离验证，请检查Docker与运行器配置",started);
        }
    }
    private void cleanup(String name) { try { new ProcessBuilder("docker","--context",context,"rm","-f",name).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start().waitFor(5,TimeUnit.SECONDS); } catch(Exception ignored) { } }
    private VerificationResult result(String status,Plan p,Integer code,String output,long started) {
        return new VerificationResult(status,p.profile(),String.join(" ",p.command()),code,output,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-started),p.tests() && !status.equals("BLOCKED") && code!=null);
    }
    private String javaHarness(ProjectBundle b,Plan p) {
        var s=new StringBuilder("import java.nio.file.*;\nimport java.util.*;\n");
        for(var f:b.files()) {
            String path=Base64.getEncoder().encodeToString(f.path().getBytes(StandardCharsets.UTF_8)); String data=Base64.getEncoder().encodeToString(f.content().getBytes(StandardCharsets.UTF_8));
            s.append("{ var p=Path.of(new String(Base64.getDecoder().decode(\"").append(path).append("\"))); if(p.getParent()!=null) Files.createDirectories(p.getParent()); Files.write(p,Base64.getDecoder().decode(\"").append(data).append("\")); }\n");
        }
        s.append("Files.createDirectories(Path.of(\"/tmp/classes\"));\n");
        s.append("int code=javax.tools.ToolProvider.getSystemJavaCompiler().run(null,System.out,System.err,");
        s.append(p.command().stream().skip(1).map(v->"\""+v.replace("\\","\\\\").replace("\"","\\\"")+"\"").collect(java.util.stream.Collectors.joining(","))).append(");\nSystem.exit(code);\n");
        return s.toString();
    }
    private static final String PYTHON_HARNESS="""
import json, pathlib, subprocess, sys
value=json.load(sys.stdin)
for item in value['files']:
    p=pathlib.Path(item['path']); p.parent.mkdir(parents=True,exist_ok=True); p.write_text(item['content'],encoding='utf-8')
sys.exit(subprocess.call(value['command']))
""";
    private static final String NODE_HARNESS="""
const fs=require('node:fs'), path=require('node:path'), cp=require('node:child_process');
const value=JSON.parse(fs.readFileSync(0,'utf8'));
for(const item of value.files) { fs.mkdirSync(path.dirname(item.path),{recursive:true}); fs.writeFileSync(item.path,item.content); }
const result=cp.spawnSync(value.command[0],value.command.slice(1),{stdio:'inherit'}); process.exit(result.status ?? 1);
""";
}
