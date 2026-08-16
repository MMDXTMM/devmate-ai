package com.devmate.knowledge.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import com.devmate.knowledge.dto.BusinessCodeEvidenceResponse;
import com.devmate.knowledge.dto.BusinessFeatureDetailResponse;
import com.devmate.knowledge.dto.BusinessFeatureResponse;
import com.devmate.knowledge.dto.BusinessJourneyResponse;
import com.devmate.knowledge.dto.BusinessModuleResponse;
import com.devmate.knowledge.dto.ProjectBusinessMapResponse;
import com.devmate.knowledge.dto.ProjectDataAssetResponse;
import com.devmate.knowledge.dto.ProjectOnboardingResponse;
import com.devmate.knowledge.dto.ProjectReadingStepResponse;
import com.devmate.knowledge.dto.ProjectStateModelResponse;
import com.devmate.knowledge.entity.CodeReference;
import com.devmate.knowledge.entity.KnowledgeChunk;
import com.devmate.knowledge.entity.KnowledgeDocument;
import com.devmate.knowledge.mapper.CodeReferenceMapper;
import com.devmate.knowledge.mapper.KnowledgeChunkMapper;
import com.devmate.knowledge.mapper.KnowledgeDocumentMapper;
import com.devmate.project.entity.Project;
import com.devmate.project.mapper.ProjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ProjectBusinessMapService {

    private static final String ANALYSIS_MODE = "STATIC_CODE_EVIDENCE_V2";
    private static final int MAX_EVIDENCE_STEPS = 12;
    private static final int MAX_CALL_DEPTH = 3;
    private static final int MAX_CODE_CHARACTERS = 6_000;
    private static final Pattern MAPPING_ANNOTATION = Pattern.compile(
            "@(?:[\\w.]+\\.)?(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)"
                    + "\\s*(?:\\((.*?)\\))?",
            Pattern.DOTALL
    );
    private static final Pattern REQUEST_METHOD = Pattern.compile(
            "RequestMethod\\.(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)"
    );
    private static final Pattern QUOTED_VALUE = Pattern.compile("\\\"([^\\\"]*)\\\"|'([^']*)'");
    private static final Pattern ENUM_VALUES = Pattern.compile(
            "\\benum\\s+[\\w$]+[^\\{]*\\{([^;}]+)", Pattern.DOTALL
    );
    private static final Pattern ENUM_CONSTANT = Pattern.compile("\\b([A-Z][A-Z0-9_]*)\\b");
    private static final Map<String, String> MODULE_NAMES = Map.ofEntries(
            Map.entry("Auth", "用户认证"),
            Map.entry("User", "用户账号、登录与签到"),
            Map.entry("Blog", "探店笔记与互动"),
            Map.entry("Shop", "商铺查询与维护"),
            Map.entry("ShopType", "商铺分类查询"),
            Map.entry("Upload", "探店图片管理"),
            Map.entry("Voucher", "优惠券管理"),
            Map.entry("VoucherOrder", "优惠券秒杀下单"),
            Map.entry("Follow", "用户关注关系"),
            Map.entry("Project", "项目管理"),
            Map.entry("SourceImport", "源码导入"),
            Map.entry("SourceStructure", "源码结构"),
            Map.entry("ProjectBusinessMap", "项目业务地图"),
            Map.entry("Retrieval", "代码知识检索"),
            Map.entry("RetrievalEvaluation", "检索效果评测"),
            Map.entry("EmbeddingIndex", "向量索引"),
            Map.entry("ReviewWorkflow", "一键代码审查流程"),
            Map.entry("ReviewDiff", "代码变更分析"),
            Map.entry("StaticAnalysis", "静态代码检查"),
            Map.entry("AiReview", "AI代码审查"),
            Map.entry("ReviewFeedback", "审查反馈"),
            Map.entry("ReviewEvaluation", "审查效果评测"),
            Map.entry("Conversation", "对话管理"),
            Map.entry("GenerationSession", "项目生成需求")
    );
    private static final Map<String, String> MODULE_GOALS = Map.ofEntries(
            Map.entry("User", "用户可以获取验证码并登录，查看个人信息和完成每日签到。"),
            Map.entry("Blog", "用户可以发布探店笔记、浏览热门或关注内容、点赞并查看互动用户。"),
            Map.entry("Shop", "用户可以查询商铺详情或按分类浏览商铺，管理端可以维护商铺信息。"),
            Map.entry("ShopType", "查询商铺分类，供首页展示和商铺筛选使用。"),
            Map.entry("Upload", "为探店笔记上传图片，并删除不再使用的图片。"),
            Map.entry("Voucher", "查询商铺优惠券，并创建普通优惠券或秒杀优惠券。"),
            Map.entry("VoucherOrder", "校验秒杀资格、扣减库存并创建优惠券订单。"),
            Map.entry("Follow", "关注或取消关注其他用户，并查询双方共同关注的人。")
    );
    private static final Map<String, String> BUSINESS_ACTION_NAMES = Map.ofEntries(
            Map.entry("Blog#saveBlog", "发布探店笔记"),
            Map.entry("Blog#queryHotBlog", "浏览热门探店笔记"),
            Map.entry("Blog#likeBlog", "点赞或取消点赞探店笔记"),
            Map.entry("Blog#queryBlogLikes", "查看笔记点赞用户"),
            Map.entry("Blog#queryBlogOfFollow", "查看关注用户的新笔记"),
            Map.entry("Blog#queryBlogById", "查看探店笔记详情"),
            Map.entry("Blog#queryBlogOfMe", "查看我发布的探店笔记"),
            Map.entry("Blog#queryBlogByUserId", "查看指定用户的探店笔记"),
            Map.entry("User#sendCode", "发送登录验证码"),
            Map.entry("User#login", "用户登录"),
            Map.entry("User#logout", "退出登录"),
            Map.entry("User#me", "查看当前用户信息"),
            Map.entry("User#queryUserById", "查看其他用户信息"),
            Map.entry("User#sign", "完成每日签到"),
            Map.entry("User#signCount", "统计连续签到天数"),
            Map.entry("Shop#queryById", "查看商铺详情"),
            Map.entry("Shop#queryShopById", "查看商铺详情"),
            Map.entry("Shop#updateShop", "更新商铺信息"),
            Map.entry("Shop#queryShopByType", "按分类或距离浏览商铺"),
            Map.entry("ShopType#queryTypeList", "查询商铺分类"),
            Map.entry("Upload#uploadImage", "上传探店图片"),
            Map.entry("Upload#deleteBlogImg", "删除探店图片"),
            Map.entry("Voucher#queryVoucherOfShop", "查询商铺优惠券"),
            Map.entry("Voucher#addVoucher", "创建普通优惠券"),
            Map.entry("Voucher#addSeckillVoucher", "创建秒杀优惠券"),
            Map.entry("VoucherOrder#seckillVoucher", "抢购优惠券并创建订单"),
            Map.entry("Follow#follow", "关注或取消关注用户"),
            Map.entry("Follow#isFollow", "查看是否已关注用户"),
            Map.entry("Follow#followCommons", "查看共同关注")
    );

    private final ProjectMapper projectMapper;
    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeChunkMapper chunkMapper;
    private final CodeReferenceMapper referenceMapper;
    private final ObjectMapper objectMapper;

    public ProjectBusinessMapService(
            ProjectMapper projectMapper,
            KnowledgeDocumentMapper documentMapper,
            KnowledgeChunkMapper chunkMapper,
            CodeReferenceMapper referenceMapper,
            ObjectMapper objectMapper
    ) {
        this.projectMapper = projectMapper;
        this.documentMapper = documentMapper;
        this.chunkMapper = chunkMapper;
        this.referenceMapper = referenceMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProjectBusinessMapResponse getBusinessMap(Long projectId) {
        BusinessMapContext context = loadContext(projectId);
        List<BusinessModuleResponse> modules = buildModules(context);
        int endpointCount = modules.stream().mapToInt(module -> module.features().size()).sum();
        String summary = endpointCount == 0
                ? "当前源码中没有识别到 Spring Web 接口入口，可以继续使用文件结构查看非 Web 模块。"
                : "系统从源码中识别到 " + modules.size() + " 个业务模块和 " + endpointCount
                        + " 个接口入口。建议先按业务模块理解功能，再沿接口调用链阅读实现代码。";
        return new ProjectBusinessMapResponse(
                context.project().getCurrentRevision(),
                ANALYSIS_MODE,
                summary,
                modules.size(),
                endpointCount,
                buildOnboarding(context, modules),
                modules,
                List.of(
                        "模块和功能来自 Controller、接口注解与调用关系，是可验证的静态推断。",
                        "动态路由、反射调用和未解析的跨模块调用可能不会出现在当前业务链路中。",
                        "业务目的和规则仍需结合 README、数据库及真实运行结果进一步确认。"
                )
        );
    }

    private ProjectOnboardingResponse buildOnboarding(
            BusinessMapContext context,
            List<BusinessModuleResponse> modules
    ) {
        String projectDescription = value(context.project().getDescription()).trim();
        List<BusinessJourneyResponse> journeys = modules.stream()
                .filter(module -> !simpleSymbol(module.controllerSymbol()).equals("HealthController"))
                .sorted(Comparator.comparingInt(this::journeyPriority).reversed())
                .limit(8)
                .map(module -> businessJourney(context, module))
                .toList();
        String journeyScope = journeys.stream().map(BusinessJourneyResponse::name).limit(6)
                .collect(Collectors.joining("、"));
        String purpose = projectPurpose(context, modules, projectDescription, journeyScope);
        List<ProjectStateModelResponse> stateModels = stateModels(context);
        List<ProjectDataAssetResponse> dataAssets = dataAssets(context);
        List<String> capabilities = detectedCapabilities(context, modules, dataAssets, stateModels);
        String architectureSummary = architectureSummary(context, modules, dataAssets);
        return new ProjectOnboardingResponse(
                purpose,
                architectureSummary,
                capabilities,
                journeys,
                stateModels,
                dataAssets,
                readingOrder(context, modules, journeys, dataAssets),
                List.of(
                        "项目描述只能视为维护者声明，业务流程仍以接口、调用链和数据证据为准。",
                        "静态分析无法完整还原反射、消息驱动、定时任务和运行时条件分支。",
                        "状态枚举只证明状态存在，不代表系统已经识别出所有合法状态转换。"
                )
        );
    }

    private String projectPurpose(
            BusinessMapContext context,
            List<BusinessModuleResponse> modules,
            String projectDescription,
            String journeyScope
    ) {
        Set<String> moduleKeys = modules.stream()
                .map(module -> moduleKey(module.controllerSymbol()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<String> userActions = new ArrayList<>();
        if (moduleKeys.contains("User")) userActions.add("登录、查看个人信息和签到");
        if (moduleKeys.contains("Shop") || moduleKeys.contains("ShopType")) userActions.add("浏览商铺及分类");
        if (moduleKeys.contains("Blog")) userActions.add("发布、浏览和点赞探店笔记");
        if (moduleKeys.contains("Follow")) userActions.add("关注其他用户");
        if (moduleKeys.contains("Voucher")) userActions.add("查询和管理优惠券");
        if (moduleKeys.contains("VoucherOrder")) userActions.add("参与优惠券秒杀并下单");
        if (userActions.size() >= 3) {
            String title = projectDescription.isBlank() ? context.project().getName() : projectDescription;
            return title + "。从接口证据可以还原出本地生活探店主线：用户可以"
                    + String.join("、", userActions) + "。";
        }
        if (!projectDescription.isBlank()) {
            return projectDescription
                    + (journeyScope.isBlank() ? "" : "。源码证据显示核心流程覆盖 " + journeyScope + " 等能力。");
        }
        return journeyScope.isBlank()
                ? context.project().getName() + " 的业务目标尚未在项目描述或可解析接口中明确。"
                : context.project().getName() + " 主要覆盖 " + journeyScope + " 等能力。";
    }

    private int journeyPriority(BusinessModuleResponse module) {
        int score = module.features().stream()
                .mapToInt(feature -> feature.implementationSteps() * 3 + (feature.accessesData() ? 10 : 0))
                .max().orElse(0) + module.features().size();
        String controller = simpleSymbol(module.controllerSymbol());
        score += switch (controller) {
            case "ProjectController" -> 200;
            case "SourceImportController" -> 180;
            case "EmbeddingIndexController" -> 160;
            case "ReviewDiffController" -> 140;
            case "StaticAnalysisController" -> 120;
            case "AiReviewController" -> 100;
            case "ReviewFeedbackController" -> 80;
            case "AuthController" -> 60;
            default -> 0;
        };
        if (controller.matches(".*(Project|SourceImport|ReviewWorkflow|AiReview|Auth)Controller")) score += 25;
        if (controller.matches(".*(ReviewDiff|StaticAnalysis|EmbeddingIndex)Controller")) score += 15;
        if (controller.matches(".*(Evaluation|Structure|Context|BusinessMap)Controller")) score -= 15;
        return score;
    }

    private BusinessJourneyResponse businessJourney(BusinessMapContext context, BusinessModuleResponse module) {
        BusinessFeatureResponse representative = module.features().stream()
                .max(Comparator.comparing(BusinessFeatureResponse::accessesData)
                        .thenComparingInt(BusinessFeatureResponse::implementationSteps))
                .orElse(null);
        List<KnowledgeChunk> implementation = representative == null
                ? List.of()
                : collectImplementation(context, context.chunksById().get(representative.id()));
        List<String> dataOperations = implementation.stream()
                .flatMap(chunk -> context.referencesBySource().getOrDefault(chunk.getId(), List.of()).stream())
                .filter(reference -> "DATA_ACCESS".equals(reference.getReferenceKind()))
                .map(this::dataOperation)
                .distinct()
                .limit(8)
                .toList();
        List<String> implementationFlow = implementation.stream()
                .map(chunk -> layerLabel(layer(chunk, context.documentsById().get(chunk.getDocumentId())))
                        + "：" + flowLabel(chunk.getSymbolName()))
                .toList();
        List<String> failureSignals = failureSignals(implementation);
        List<String> featureNames = module.features().stream().map(BusinessFeatureResponse::name).limit(4).toList();
        String goal = MODULE_GOALS.getOrDefault(moduleKey(module.controllerSymbol()), featureNames.isEmpty()
                ? "当前只识别到模块入口，具体业务目标仍需确认。"
                : "用户可以" + String.join("、", featureNames) + "。具体规则可继续查看接口与实现代码。");
        List<String> files = implementation.stream()
                .map(chunk -> context.documentsById().get(chunk.getDocumentId()))
                .filter(Objects::nonNull)
                .map(KnowledgeDocument::getFilePath)
                .distinct()
                .toList();
        return new BusinessJourneyResponse(
                module.id(),
                module.name(),
                goal,
                module.features().stream()
                        .map(feature -> String.join("/", feature.httpMethods()) + " " + feature.path()
                                + " · " + feature.name())
                        .toList(),
                implementationFlow,
                dataOperations,
                failureSignals,
                files
        );
    }

    private List<String> failureSignals(List<KnowledgeChunk> implementation) {
        String content = implementation.stream().map(KnowledgeChunk::getContent)
                .filter(Objects::nonNull).collect(Collectors.joining("\n"));
        List<String> signals = new ArrayList<>();
        if (content.contains("throw new ") || content.contains("orElseThrow(")) {
            signals.add("存在显式异常分支，条件不满足时会中断当前流程。");
        }
        if (content.contains("catch (")) {
            signals.add("存在异常捕获逻辑，需要结合实现确认是恢复、转换还是记录失败状态。");
        }
        if (Pattern.compile("(?i)(setStatus|updateStatus|status\\s*[=!]=)").matcher(content).find()) {
            signals.add("实现涉及状态读取或更新，修改时需要核对允许的状态转换。");
        }
        return signals.isEmpty()
                ? List.of("当前证据链未识别到显式失败信号，仍需结合测试和运行日志确认边界。")
                : List.copyOf(signals);
    }

    private List<ProjectStateModelResponse> stateModels(BusinessMapContext context) {
        return context.chunks().stream()
                .filter(chunk -> "CLASS".equals(chunk.getChunkType()))
                .filter(chunk -> simpleSymbol(chunk.getSymbolName()).matches(".*(Status|State|Stage)$"))
                .map(chunk -> {
                    KnowledgeDocument document = context.documentsById().get(chunk.getDocumentId());
                    return new ProjectStateModelResponse(
                            chunk.getId(),
                            simpleSymbol(chunk.getSymbolName()),
                            enumValues(chunk.getContent()),
                            document == null ? null : document.getFilePath(),
                            chunk.getStartLine(),
                            chunk.getEndLine()
                    );
                })
                .filter(state -> !state.values().isEmpty())
                .sorted(Comparator.comparing(ProjectStateModelResponse::name))
                .limit(20)
                .toList();
    }

    private List<String> enumValues(String content) {
        Matcher body = ENUM_VALUES.matcher(value(content));
        if (!body.find()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String candidate : body.group(1).split(",")) {
            Matcher constant = ENUM_CONSTANT.matcher(candidate);
            if (constant.find()) values.add(constant.group(1));
        }
        return List.copyOf(values);
    }

    private List<ProjectDataAssetResponse> dataAssets(BusinessMapContext context) {
        return context.chunks().stream()
                .filter(chunk -> "DATABASE_TABLE".equals(chunk.getChunkType()))
                .map(chunk -> {
                    KnowledgeDocument document = context.documentsById().get(chunk.getDocumentId());
                    return new ProjectDataAssetResponse(
                            chunk.getId(), simpleSymbol(chunk.getSymbolName()),
                            document == null ? null : document.getFilePath(),
                            chunk.getStartLine(), chunk.getEndLine()
                    );
                })
                .filter(asset -> asset.name() != null && !asset.name().isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toMap(ProjectDataAssetResponse::name, asset -> asset, (left, right) -> left,
                                LinkedHashMap::new),
                        values -> values.values().stream().limit(30).toList()
                ));
    }

    private List<String> detectedCapabilities(
            BusinessMapContext context,
            List<BusinessModuleResponse> modules,
            List<ProjectDataAssetResponse> dataAssets,
            List<ProjectStateModelResponse> stateModels
    ) {
        LinkedHashSet<String> capabilities = new LinkedHashSet<>();
        if (!modules.isEmpty()) capabilities.add("Spring Web REST 接口");
        if (context.chunks().stream().anyMatch(chunk -> layer(chunk,
                context.documentsById().get(chunk.getDocumentId())).equals("SERVICE"))) {
            capabilities.add("Service 业务编排");
        }
        if (!dataAssets.isEmpty() || context.referencesBySource().values().stream().flatMap(List::stream)
                .anyMatch(reference -> "DATA_ACCESS".equals(reference.getReferenceKind()))) {
            capabilities.add("数据库持久化");
        }
        if (!stateModels.isEmpty()) capabilities.add("显式业务状态模型");
        String searchable = context.chunks().stream().map(KnowledgeChunk::getSymbolName)
                .filter(Objects::nonNull).collect(Collectors.joining(" ")).toLowerCase(Locale.ROOT);
        if (searchable.contains("security") || searchable.contains("jwt") || searchable.contains("auth")) {
            capabilities.add("认证与访问控制");
        }
        if (searchable.contains("embedding") || searchable.contains("retrieval") || searchable.contains("vector")) {
            capabilities.add("RAG 检索与向量索引");
        }
        if (searchable.contains("agent") || searchable.contains("aireview")) {
            capabilities.add("LLM Agent 与受控工具调用");
        }
        return List.copyOf(capabilities);
    }

    private String architectureSummary(
            BusinessMapContext context,
            List<BusinessModuleResponse> modules,
            List<ProjectDataAssetResponse> dataAssets
    ) {
        long serviceMethods = context.chunks().stream()
                .filter(chunk -> "METHOD".equals(chunk.getChunkType()))
                .filter(chunk -> layer(chunk, context.documentsById().get(chunk.getDocumentId())).equals("SERVICE"))
                .count();
        return "外部请求由 " + modules.size() + " 个 Controller 模块接收，当前证据中包含 "
                + serviceMethods + " 个 Service 方法和 " + dataAssets.size()
                + " 个数据库表定义。建议按“业务入口 → 服务编排 → 状态与数据 → 失败路径”理解项目。";
    }

    private List<ProjectReadingStepResponse> readingOrder(
            BusinessMapContext context,
            List<BusinessModuleResponse> modules,
            List<BusinessJourneyResponse> journeys,
            List<ProjectDataAssetResponse> dataAssets
    ) {
        List<ProjectReadingStepResponse> result = new ArrayList<>();
        Set<String> visitedFiles = new HashSet<>();
        context.chunks().stream()
                .filter(chunk -> "CLASS".equals(chunk.getChunkType()))
                .filter(chunk -> simpleSymbol(chunk.getSymbolName()).endsWith("Application"))
                .findFirst()
                .ifPresent(chunk -> addReadingStep(result, visitedFiles, context, chunk, "启动入口",
                        "先确认应用如何启动、扫描哪些组件。"));
        journeys.stream().map(journey -> journey.moduleId()).limit(4).forEach(moduleId -> {
            BusinessModuleResponse module = modules.stream()
                    .filter(candidate -> candidate.id().equals(moduleId)).findFirst().orElse(null);
            if (module == null) return;
            KnowledgeChunk controller = context.classesByName().get(module.controllerSymbol());
            if (controller != null) addReadingStep(result, visitedFiles, context, controller, "业务入口",
                    "从 " + module.name() + " 的 HTTP 接口理解用户可以执行什么操作。");
        });
        journeys.stream().flatMap(journey -> journey.evidenceFiles().stream()).limit(4).forEach(path -> {
            context.documentsById().values().stream().filter(document -> path.equals(document.getFilePath()))
                    .findFirst().ifPresent(document -> context.chunks().stream()
                            .filter(chunk -> Objects.equals(chunk.getDocumentId(), document.getId()))
                            .filter(chunk -> "METHOD".equals(chunk.getChunkType()))
                            .filter(chunk -> layer(chunk, document).equals("SERVICE"))
                            .findFirst().ifPresent(chunk -> addReadingStep(
                                    result, visitedFiles, context, chunk, "业务实现",
                                    "沿 Service 代码确认规则、状态变化和跨组件协作。"
                            )));
        });
        dataAssets.stream().limit(3).forEach(asset -> {
            KnowledgeChunk chunk = context.chunksById().get(asset.chunkId());
            if (chunk != null) addReadingStep(result, visitedFiles, context, chunk, "数据模型",
                    "最后结合表结构确认业务数据、约束和持久化边界。");
        });
        context.chunks().stream()
                .filter(chunk -> "CLASS".equals(chunk.getChunkType()))
                .filter(chunk -> simpleSymbol(chunk.getSymbolName()).matches(".*(Configuration|Config|Properties)$"))
                .limit(2)
                .forEach(chunk -> addReadingStep(result, visitedFiles, context, chunk, "运行配置",
                        "完成业务主线后，再理解外部依赖、开关和运行参数。"));
        return List.copyOf(result);
    }

    private void addReadingStep(
            List<ProjectReadingStepResponse> result,
            Set<String> visitedFiles,
            BusinessMapContext context,
            KnowledgeChunk chunk,
            String category,
            String reason
    ) {
        if (result.size() >= 12) return;
        KnowledgeDocument document = context.documentsById().get(chunk.getDocumentId());
        if (document == null || !visitedFiles.add(document.getFilePath())) return;
        result.add(new ProjectReadingStepResponse(
                result.size() + 1,
                category,
                simpleSymbol(chunk.getSymbolName()),
                reason,
                document.getFilePath(),
                chunk.getSymbolName(),
                chunk.getStartLine()
        ));
    }

    private String layerLabel(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> "接口入口";
            case "SERVICE" -> "业务服务";
            case "DATA_ACCESS" -> "数据访问";
            default -> "关联实现";
        };
    }

    @Transactional(readOnly = true)
    public BusinessFeatureDetailResponse getFeatureDetail(Long projectId, Long featureId) {
        BusinessMapContext context = loadContext(projectId);
        KnowledgeChunk featureChunk = context.chunksById().get(featureId);
        if (featureChunk == null || !"METHOD".equals(featureChunk.getChunkType())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "业务功能入口不存在");
        }
        ControllerEndpoint endpoint = endpoint(context, featureChunk);
        if (endpoint == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "所选方法不是可识别的Web接口入口");
        }
        List<KnowledgeChunk> implementation = collectImplementation(context, featureChunk);
        List<BusinessCodeEvidenceResponse> evidence = implementation.stream()
                .map(chunk -> evidence(context, chunk))
                .toList();
        List<String> dataOperations = implementation.stream()
                .flatMap(chunk -> context.referencesBySource().getOrDefault(chunk.getId(), List.of()).stream())
                .filter(reference -> "DATA_ACCESS".equals(reference.getReferenceKind()))
                .map(this::dataOperation)
                .distinct()
                .toList();
        BusinessFeatureResponse feature = feature(context, endpoint, implementation, !dataOperations.isEmpty());
        String flow = evidence.stream()
                .map(item -> flowLabel(item.symbolName()))
                .collect(Collectors.joining(" → "));
        return new BusinessFeatureDetailResponse(
                feature,
                flow.isBlank() ? "当前只识别到接口入口，尚未解析出后续调用。" : flow,
                dataOperations,
                evidence
        );
    }

    private List<BusinessModuleResponse> buildModules(BusinessMapContext context) {
        Map<String, List<ControllerEndpoint>> grouped = new LinkedHashMap<>();
        context.chunks().stream()
                .filter(chunk -> "METHOD".equals(chunk.getChunkType()))
                .map(chunk -> endpoint(context, chunk))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(endpoint -> endpoint.document().getFilePath()
                        + endpoint.mapping().path()))
                .forEach(endpoint -> grouped.computeIfAbsent(
                        endpoint.controller().getSymbolName(), ignored -> new ArrayList<>()
                ).add(endpoint));

        List<BusinessModuleResponse> modules = new ArrayList<>();
        for (List<ControllerEndpoint> endpoints : grouped.values()) {
            ControllerEndpoint first = endpoints.getFirst();
            String moduleName = moduleName(first.controller().getSymbolName());
            String moduleKey = moduleKey(first.controller().getSymbolName());
            List<BusinessFeatureResponse> features = endpoints.stream()
                    .map(endpoint -> {
                        List<KnowledgeChunk> implementation = collectImplementation(context, endpoint.method());
                        boolean accessesData = implementation.stream().anyMatch(chunk -> context.referencesBySource()
                                .getOrDefault(chunk.getId(), List.of()).stream()
                                .anyMatch(reference -> "DATA_ACCESS".equals(reference.getReferenceKind())));
                        return feature(context, endpoint, implementation, accessesData);
                    })
                    .toList();
            modules.add(new BusinessModuleResponse(
                    String.valueOf(first.controller().getId()),
                    moduleName,
                    MODULE_GOALS.getOrDefault(moduleKey,
                            moduleName + "包含 " + features.size() + " 个可访问接口入口。"),
                    first.controller().getSymbolName(),
                    first.document().getFilePath(),
                    first.controller().getStartLine(),
                    first.controller().getEndLine(),
                    features
            ));
        }
        return List.copyOf(modules);
    }

    private BusinessFeatureResponse feature(
            BusinessMapContext context,
            ControllerEndpoint endpoint,
            List<KnowledgeChunk> implementation,
            boolean accessesData
    ) {
        String moduleName = moduleName(endpoint.controller().getSymbolName());
        String moduleKey = moduleKey(endpoint.controller().getSymbolName());
        String methodName = methodName(endpoint.method().getSymbolName());
        return new BusinessFeatureResponse(
                endpoint.method().getId(),
                featureName(moduleKey, moduleName, methodName, endpoint.mapping().methods()),
                "通过 " + String.join("/", endpoint.mapping().methods()) + " " + endpoint.mapping().path()
                        + " 进入 " + simpleSymbol(endpoint.method().getSymbolName()) + "。",
                endpoint.mapping().methods(),
                endpoint.mapping().path(),
                endpoint.method().getSymbolName(),
                endpoint.document().getFilePath(),
                endpoint.method().getStartLine(),
                endpoint.method().getEndLine(),
                implementation.size(),
                accessesData
        );
    }

    private ControllerEndpoint endpoint(BusinessMapContext context, KnowledgeChunk method) {
        String owner = ownerType(method.getSymbolName());
        KnowledgeChunk controller = context.classesByName().get(owner);
        KnowledgeDocument document = context.documentsById().get(method.getDocumentId());
        if (controller == null || document == null || !isController(controller, document)) {
            return null;
        }
        RequestMappingInfo methodMapping = requestMapping(method.getContent());
        if (methodMapping == null) {
            return null;
        }
        String classHeader = classHeader(controller.getContent(), simpleSymbol(controller.getSymbolName()));
        RequestMappingInfo classMapping = requestMapping(classHeader);
        String path = combinePaths(classMapping == null ? "" : classMapping.path(), methodMapping.path());
        return new ControllerEndpoint(
                controller,
                method,
                document,
                new RequestMappingInfo(methodMapping.methods(), path)
        );
    }

    private List<KnowledgeChunk> collectImplementation(BusinessMapContext context, KnowledgeChunk root) {
        List<KnowledgeChunk> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        ArrayDeque<ImplementationNode> queue = new ArrayDeque<>();
        queue.add(new ImplementationNode(root, 0));
        while (!queue.isEmpty() && result.size() < MAX_EVIDENCE_STEPS) {
            ImplementationNode node = queue.removeFirst();
            if (!visited.add(node.chunk().getId())) {
                continue;
            }
            result.add(node.chunk());
            if (node.depth() >= MAX_CALL_DEPTH) {
                continue;
            }
            KnowledgeChunk implementation = resolveImplementation(context, node.chunk());
            if (implementation != null) {
                queue.addLast(new ImplementationNode(implementation, node.depth() + 1));
            }
            context.referencesBySource().getOrDefault(node.chunk().getId(), List.of()).stream()
                    .filter(reference -> "METHOD_CALL".equals(reference.getReferenceKind()))
                    .map(reference -> resolveTarget(context, reference))
                    .filter(Objects::nonNull)
                    .filter(this::isBusinessTarget)
                    .forEach(target -> queue.addLast(new ImplementationNode(target, node.depth() + 1)));
        }
        return List.copyOf(result);
    }

    private BusinessCodeEvidenceResponse evidence(BusinessMapContext context, KnowledgeChunk chunk) {
        KnowledgeDocument document = context.documentsById().get(chunk.getDocumentId());
        String content = chunk.getContent() == null ? "" : chunk.getContent();
        boolean truncated = content.length() > MAX_CODE_CHARACTERS;
        String code = truncated ? content.substring(0, MAX_CODE_CHARACTERS) : content;
        String layer = layer(chunk, document);
        return new BusinessCodeEvidenceResponse(
                chunk.getId(),
                chunk.getDocumentId(),
                layer,
                chunk.getSymbolName(),
                document == null ? null : document.getFilePath(),
                chunk.getStartLine(),
                chunk.getEndLine(),
                layerExplanation(layer),
                code,
                truncated,
                content.length()
        );
    }

    private BusinessMapContext loadContext(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "项目不存在");
        }
        if (project.getCurrentRevision() == null || project.getCurrentRevision().isBlank()) {
            return new BusinessMapContext(project, List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }
        List<KnowledgeDocument> documents = documentMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeDocument.class)
                        .eq(KnowledgeDocument::getProjectId, projectId)
                        .eq(KnowledgeDocument::getRevision, project.getCurrentRevision())
                        .eq(KnowledgeDocument::getDeleted, 0)
        );
        List<KnowledgeChunk> chunks = chunkMapper.selectList(
                Wrappers.lambdaQuery(KnowledgeChunk.class)
                        .eq(KnowledgeChunk::getProjectId, projectId)
                        .eq(KnowledgeChunk::getRevision, project.getCurrentRevision())
                        .in(KnowledgeChunk::getChunkType, List.of(
                                "CLASS", "METHOD", "CONFIG_PROPERTY", "DATABASE_TABLE",
                                "DATABASE_COLUMN", "DATABASE_INDEX", "DATABASE_CONSTRAINT", "DATABASE_CHANGE"
                        ))
                        .orderByAsc(KnowledgeChunk::getId)
        );
        List<CodeReference> references = referenceMapper.selectList(
                Wrappers.lambdaQuery(CodeReference.class)
                        .eq(CodeReference::getProjectId, projectId)
                        .eq(CodeReference::getRevision, project.getCurrentRevision())
                        .in(CodeReference::getReferenceKind, List.of("METHOD_CALL", "DATA_ACCESS"))
                        .orderByAsc(CodeReference::getId)
        );
        Map<Long, KnowledgeDocument> documentsById = documents.stream()
                .collect(Collectors.toMap(KnowledgeDocument::getId, document -> document));
        Map<Long, KnowledgeChunk> chunksById = chunks.stream()
                .collect(Collectors.toMap(KnowledgeChunk::getId, chunk -> chunk));
        Map<String, KnowledgeChunk> classesByName = chunks.stream()
                .filter(chunk -> "CLASS".equals(chunk.getChunkType()))
                .collect(Collectors.toMap(
                        KnowledgeChunk::getSymbolName,
                        chunk -> chunk,
                        (left, right) -> left
                ));
        Map<Long, List<CodeReference>> referencesBySource = references.stream()
                .collect(Collectors.groupingBy(
                        CodeReference::getSourceChunkId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<String, List<KnowledgeChunk>> methodsByCallKey = chunks.stream()
                .filter(chunk -> "METHOD".equals(chunk.getChunkType()))
                .collect(Collectors.groupingBy(
                        chunk -> methodKey(
                                simpleSymbol(ownerType(chunk.getSymbolName())),
                                methodName(chunk.getSymbolName()),
                                parameterCount(chunk.getMetadataJson())
                        )
                ));
        return new BusinessMapContext(
                project,
                chunks,
                documentsById,
                chunksById,
                classesByName,
                referencesBySource,
                methodsByCallKey
        );
    }

    private KnowledgeChunk resolveTarget(BusinessMapContext context, CodeReference reference) {
        if (reference.getTargetChunkId() != null) {
            return context.chunksById().get(reference.getTargetChunkId());
        }
        if (reference.getQualifier() == null || reference.getQualifier().isBlank()) {
            return null;
        }
        String qualifier = reference.getQualifier();
        int separator = qualifier.lastIndexOf('.');
        String variableName = separator < 0 ? qualifier : qualifier.substring(separator + 1);
        if (variableName.isBlank() || variableName.contains("(") || variableName.contains("[")) {
            return null;
        }
        String expectedOwner = Character.toUpperCase(variableName.charAt(0)) + variableName.substring(1);
        List<KnowledgeChunk> candidates = context.methodsByCallKey().getOrDefault(
                methodKey(expectedOwner, reference.getReferenceName(), reference.getArgumentCount()),
                List.of()
        );
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private KnowledgeChunk resolveImplementation(BusinessMapContext context, KnowledgeChunk method) {
        String content = value(method.getContent()).stripTrailing();
        if (!content.endsWith(";")) {
            return null;
        }
        String owner = simpleSymbol(ownerType(method.getSymbolName()));
        List<KnowledgeChunk> candidates = context.methodsByCallKey().getOrDefault(
                methodKey(
                        owner + "Impl",
                        methodName(method.getSymbolName()),
                        parameterCount(method.getMetadataJson())
                ),
                List.of()
        );
        return candidates.size() == 1 ? candidates.getFirst() : null;
    }

    private boolean isBusinessTarget(KnowledgeChunk chunk) {
        String qualifiedOwner = ownerType(chunk.getSymbolName());
        String owner = simpleSymbol(qualifiedOwner);
        String method = methodName(chunk.getSymbolName());
        if (qualifiedOwner.contains(".entity.")
                && (method.startsWith("get") || method.startsWith("set") || method.startsWith("is"))) {
            return false;
        }
        return !owner.endsWith("Response")
                && !owner.endsWith("Request")
                && !owner.endsWith("Dto")
                && !owner.endsWith("DTO")
                && !owner.equals("ApiResponse");
    }

    private String flowLabel(String symbolName) {
        return simpleSymbol(ownerType(symbolName)) + "." + simpleSymbol(symbolName);
    }

    private String methodKey(String owner, String method, Integer parameterCount) {
        return owner + "#" + method + "/" + Objects.requireNonNullElse(parameterCount, -1);
    }

    private Integer parameterCount(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() { });
            Object value = metadata.get("parameterCount");
            return value instanceof Number number ? number.intValue() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isController(KnowledgeChunk controller, KnowledgeDocument document) {
        Set<String> annotations = annotations(controller.getMetadataJson()).stream()
                .map(this::simpleSymbol)
                .collect(Collectors.toSet());
        return annotations.contains("RestController")
                || annotations.contains("Controller")
                || document.getFileName().endsWith("Controller.java");
    }

    private List<String> annotations(String metadataJson) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return List.of();
        }
        try {
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<>() { });
            Object value = metadata.get("annotations");
            if (!(value instanceof List<?> list)) {
                return List.of();
            }
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private RequestMappingInfo requestMapping(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        Matcher matcher = MAPPING_ANNOTATION.matcher(content);
        if (!matcher.find()) {
            return null;
        }
        String annotation = matcher.group(1);
        String arguments = matcher.group(2) == null ? "" : matcher.group(2);
        List<String> methods = switch (annotation) {
            case "GetMapping" -> List.of("GET");
            case "PostMapping" -> List.of("POST");
            case "PutMapping" -> List.of("PUT");
            case "DeleteMapping" -> List.of("DELETE");
            case "PatchMapping" -> List.of("PATCH");
            default -> requestMethods(arguments);
        };
        Matcher quoted = QUOTED_VALUE.matcher(arguments);
        String path = quoted.find() ? Objects.requireNonNullElse(quoted.group(1), quoted.group(2)) : "";
        return new RequestMappingInfo(methods, path);
    }

    private List<String> requestMethods(String arguments) {
        Matcher matcher = REQUEST_METHOD.matcher(arguments);
        List<String> methods = new ArrayList<>();
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods.isEmpty() ? List.of("ANY") : List.copyOf(methods);
    }

    private String classHeader(String content, String simpleName) {
        if (content == null) {
            return "";
        }
        Pattern declaration = Pattern.compile("\\b(class|interface|record|enum)\\s+" + Pattern.quote(simpleName));
        Matcher matcher = declaration.matcher(content);
        return matcher.find() ? content.substring(0, matcher.start()) : content;
    }

    private String combinePaths(String base, String method) {
        String combined = ("/" + value(base) + "/" + value(method)).replaceAll("/{2,}", "/");
        return combined.length() > 1 && combined.endsWith("/")
                ? combined.substring(0, combined.length() - 1)
                : combined;
    }

    private String moduleName(String controllerSymbol) {
        String key = moduleKey(controllerSymbol);
        return MODULE_NAMES.getOrDefault(key, splitCamelCase(key) + "业务");
    }

    private String moduleKey(String controllerSymbol) {
        return simpleSymbol(controllerSymbol).replaceFirst("Controller$", "");
    }

    private String featureName(String moduleKey, String module, String methodName, List<String> httpMethods) {
        String businessAction = BUSINESS_ACTION_NAMES.get(moduleKey + "#" + methodName);
        if (businessAction != null) return businessAction;
        String lower = methodName.toLowerCase(Locale.ROOT);
        if (lower.contains("login")) return "用户登录";
        if (lower.contains("register")) return "用户注册";
        if (lower.contains("import")) return "导入" + module;
        if (lower.contains("index")) return "构建" + module;
        if (lower.contains("review")) return "执行" + module;
        if (lower.contains("feedback")) return "提交" + module;
        if (startsWithAny(lower, "create", "add", "save", "start", "execute", "run", "import")) {
            return "创建或执行" + module;
        }
        if (startsWithAny(lower, "list", "page", "search", "query")) return "查询" + module + "列表";
        if (startsWithAny(lower, "get", "detail", "find", "latest")) return "查看" + module + "详情";
        if (startsWithAny(lower, "update", "edit", "modify")) return "更新" + module;
        if (startsWithAny(lower, "delete", "remove")) return "删除" + module;
        if (startsWithAny(lower, "confirm", "approve")) return "确认" + module;
        if (httpMethods.contains("GET")) return "查询" + module;
        return module + "：" + methodName;
    }

    private boolean startsWithAny(String value, String... prefixes) {
        for (String prefix : prefixes) {
            if (value.startsWith(prefix)) return true;
        }
        return false;
    }

    private String layer(KnowledgeChunk chunk, KnowledgeDocument document) {
        String owner = ownerType(chunk.getSymbolName());
        String path = document == null ? "" : document.getFilePath().toLowerCase(Locale.ROOT);
        if (owner.endsWith("Controller")) return "CONTROLLER";
        if (owner.endsWith("Service") || owner.endsWith("ServiceImpl") || owner.contains("Service$")
                || path.contains("/service/")) return "SERVICE";
        if (owner.endsWith("Mapper") || owner.endsWith("Repository") || owner.endsWith("Dao")
                || path.contains("/mapper/") || path.contains("/repository/")) return "DATA_ACCESS";
        return "SUPPORTING_CODE";
    }

    private String layerExplanation(String layer) {
        return switch (layer) {
            case "CONTROLLER" -> "接口入口：接收请求、校验参数并把业务交给服务层。";
            case "SERVICE" -> "业务实现：组织规则、状态变化以及多个组件之间的协作。";
            case "DATA_ACCESS" -> "数据访问：读取或写入业务数据。";
            default -> "关联实现：被业务链路调用的领域或辅助代码。";
        };
    }

    private String dataOperation(CodeReference reference) {
        String qualifier = value(reference.getQualifier());
        String call = qualifier.isBlank()
                ? reference.getReferenceName() + "()"
                : qualifier + "." + reference.getReferenceName() + "()";
        return simpleSymbol(reference.getReferenceName()) + "：" + call;
    }

    private String ownerType(String symbolName) {
        int separator = symbolName == null ? -1 : symbolName.indexOf('#');
        return separator < 0 ? value(symbolName) : symbolName.substring(0, separator);
    }

    private String methodName(String symbolName) {
        int hash = symbolName == null ? -1 : symbolName.indexOf('#');
        int parenthesis = symbolName == null ? -1 : symbolName.indexOf('(', hash + 1);
        if (hash < 0) return value(symbolName);
        return parenthesis < 0 ? symbolName.substring(hash + 1) : symbolName.substring(hash + 1, parenthesis);
    }

    private String simpleSymbol(String symbolName) {
        if (symbolName == null) return "";
        int method = symbolName.indexOf('#');
        if (method >= 0) return symbolName.substring(method + 1);
        int separator = symbolName.lastIndexOf('.');
        return separator < 0 ? symbolName : symbolName.substring(separator + 1);
    }

    private String splitCamelCase(String value) {
        return value.replaceAll("([a-z0-9])([A-Z])", "$1 $2");
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private record RequestMappingInfo(List<String> methods, String path) {
    }

    private record ControllerEndpoint(
            KnowledgeChunk controller,
            KnowledgeChunk method,
            KnowledgeDocument document,
            RequestMappingInfo mapping
    ) {
    }

    private record ImplementationNode(KnowledgeChunk chunk, int depth) {
    }

    private record BusinessMapContext(
            Project project,
            List<KnowledgeChunk> chunks,
            Map<Long, KnowledgeDocument> documentsById,
            Map<Long, KnowledgeChunk> chunksById,
            Map<String, KnowledgeChunk> classesByName,
            Map<Long, List<CodeReference>> referencesBySource,
            Map<String, List<KnowledgeChunk>> methodsByCallKey
    ) {
    }
}
