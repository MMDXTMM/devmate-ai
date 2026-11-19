package com.devmate.generation.service;
import com.devmate.generation.dto.RuntimeGuideResponse;
import com.devmate.generation.dto.RuntimeGuideResponse.Step;
import com.devmate.generation.model.ProjectBundle;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class GenerationRuntimeGuideService {
    private final GenerationRevisionService revisions;private final ObjectMapper json;
    public GenerationRuntimeGuideService(GenerationRevisionService revisions,ObjectMapper json){this.revisions=revisions;this.json=json;}
    public RuntimeGuideResponse guide(Long sid,Long revisionId){return describe(revisions.bundle(revisions.ready(sid,revisionId)));}
    RuntimeGuideResponse describe(ProjectBundle project) {
        var files=new LinkedHashMap<String,String>();project.files().forEach(f->files.put(f.path(),f.content()));var paths=files.keySet();var steps=new ArrayList<Step>();var evidence=new ArrayList<String>();String language="其他技术栈";
        steps.add(new Step("1. 保存完整项目","下载当前选用版本ZIP并解压；在解压后的项目根目录打开终端。保留README和配置示例。",List.of()));
        if(paths.stream().anyMatch(p->p.endsWith(".py"))){language="Python";evidence.add(paths.stream().filter(p->p.endsWith(".py")).findFirst().orElseThrow());
            steps.add(new Step("2. 准备Python环境","在自己的电脑确认Python版本；macOS/Linux示例使用python3，Windows通常使用py -3。以README的版本要求为准。",List.of("python3 --version")));
            var install=new ArrayList<String>();if(paths.contains("requirements.txt")&&files.get("requirements.txt").lines().anyMatch(l->!l.isBlank()&&!l.strip().startsWith("#")))install.add("python3 -m pip install -r requirements.txt");else if(paths.contains("pyproject.toml"))install.add("python3 -m pip install .");
            steps.add(new Step("3. 安装与配置","需要第三方依赖时，以下按macOS/Linux创建并激活虚拟环境；Windows PowerShell使用 .\\.venv\\Scripts\\Activate.ps1 激活。只用标准库时可跳过安装。配置项按README填写。",install.isEmpty()?List.of():concat(List.of("python3 -m venv .venv","source .venv/bin/activate"),install)));
            String entry=paths.contains("main.py")?"python3 main.py --help":paths.contains("app.py")?"python3 app.py":"";
            steps.add(new Step("4. 启动与试用",entry.isEmpty()?"未识别固定入口，请打开README查看启动方式。":"先查看帮助，再按README完成一次核心操作。不要把--help成功当作业务测试通过。",entry.isEmpty()?List.of():List.of(entry)));
            steps.add(new Step("5. 验证结果","核对正常操作、错误输入以及关闭后数据保存。若测试使用pytest，请按README执行；下方命令仅适用于标准库unittest。",paths.stream().anyMatch(p->p.startsWith("tests/")&&p.endsWith(".py"))?List.of("python3 -m unittest discover -s tests -v"):List.of()));
        }else if(paths.contains("package.json")){language="Node.js / 前端";evidence.add("package.json");
            steps.add(new Step("2. 准备Node.js","在自己的电脑确认Node和npm版本，与package.json的engines及README要求核对。",List.of("node --version","npm --version")));
            steps.add(new Step("3. 安装与配置","确认依赖来源后，在项目目录安装依赖；按示例配置环境变量，不把密钥写进源码。",List.of(paths.contains("package-lock.json")?"npm ci":"npm install")));
            try{var scripts=json.readTree(files.get("package.json")).path("scripts");var start=new ArrayList<String>();if(scripts.has("dev"))start.add("npm run dev");else if(scripts.has("start"))start.add("npm start");
                steps.add(new Step("4. 启动与试用",start.isEmpty()?"未发现dev/start脚本，请查看README。":"执行项目已有启动脚本，访问终端实际显示的地址。",start));
                var test=new ArrayList<String>();if(scripts.has("test"))test.add("npm test");if(scripts.has("build"))test.add("npm run build");steps.add(new Step("5. 验证结果","运行现有测试/构建，并手动完成一次核心业务。",test));
            }catch(Exception e){steps.add(new Step("4. 检查配置","package.json无法解析，请先按README修复JSON格式。",List.of()));}
        }else if(paths.stream().anyMatch(p->p.endsWith(".js")||p.endsWith(".mjs")||p.endsWith(".cjs"))){language="JavaScript / Node.js";evidence.add(paths.stream().filter(p->p.endsWith(".js")||p.endsWith(".mjs")||p.endsWith(".cjs")).findFirst().orElseThrow());steps.add(new Step("2. 准备Node.js","确认本机Node版本，并按README找到入口。",List.of("node --version")));steps.add(new Step("3. 启动与验证","无package.json时不猜测安装依赖或入口。按README执行，原生测试可在项目根运行。",List.of("node --test")));
        }else if(paths.contains("pom.xml")){language="Java / Maven";evidence.add("pom.xml");steps.add(new Step("2. 准备JDK与Maven","JDK版本以pom.xml和README为准；以下命令检测自己的环境。",List.of("java -version","mvn -version")));steps.add(new Step("3. 数据和配置","按README准备MySQL等实际依赖，使用独立开发数据库，执行项目提供的迁移方式。不要把已有业务数据库当作演示环境。",List.of()));steps.add(new Step("4. 构建与测试","首次执行可能下载依赖；编译通过仍需核对业务。",List.of("mvn test","mvn package")));steps.add(new Step("5. 启动项目","入口与启动方式请核对README；只有Spring Boot项目使用下方命令。",files.get("pom.xml").contains("spring-boot")?List.of("mvn spring-boot:run"):List.of()));
        }else if(paths.stream().anyMatch(p->p.endsWith(".java"))){language="Java";evidence.add(paths.stream().filter(p->p.endsWith(".java")).findFirst().orElseThrow());steps.add(new Step("2. 准备JDK","确认本机JDK版本，按README编译和运行入口类。",List.of("java -version","javac -version")));
        }else{steps.add(new Step("2. 查看README","当前栈没有固定运行向导；按README准备对应语言和依赖。系统不会猜测执行命令。",List.of()));}
        var config=paths.stream().filter(p->p.endsWith(".env.example")||p.endsWith("application.yml")||p.endsWith("application.yaml")||p.endsWith("application.properties")).toList();
        if(!config.isEmpty()){evidence.addAll(config);steps.add(new Step("配置文件提示","请查看这些配置文件并按README填写本机环境；本页面不回显配置值："+String.join("、",config),List.of()));}
        return new RuntimeGuideResponse(language,List.copyOf(evidence),List.copyOf(steps),List.of("向导根据当前版本文件识别，没有检测你电脑是否安装成功。","命令由你在自己的环境执行；隔离验证的状态单独显示。","更改版本后请重新核对运行说明和数据迁移风险。"));
    }
    private List<String> concat(List<String> a,List<String>b){var v=new ArrayList<>(a);v.addAll(b);return v;}
}
