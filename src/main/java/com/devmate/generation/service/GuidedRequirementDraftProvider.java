package com.devmate.generation.service;

import com.devmate.generation.model.RequirementAnswer;
import com.devmate.generation.model.RequirementDraft;
import com.devmate.generation.model.RequirementInputType;
import com.devmate.generation.model.RequirementOption;
import com.devmate.generation.model.RequirementQuestion;
import com.devmate.generation.model.RequirementQuestionCategory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class GuidedRequirementDraftProvider implements RequirementDraftProvider {

    private static final String PROMPT_VERSION = "guided-requirement-v2";

    @Override
    public RequirementDraft createInitialDraft(String originalRequirement) {
        String requirement = originalRequirement.trim();
        return new RequirementDraft(
                baseRequirementSummary(requirement),
                defaultArchitecture(),
                List.of(
                        "采用 Java 21、Spring Boot 3、MyBatis-Plus、MySQL 和 Flyway。",
                        "采用模块化单体，Controller、Service、Mapper 分层。",
                        "第一版只生成后端，并使用自动化测试验证核心业务。"
                ),
                questionsFor(requirement),
                PROMPT_VERSION
        );
    }

    @Override
    public RequirementDraft reviseDraft(
            String originalRequirement,
            RequirementDraft previousDraft,
            List<RequirementAnswer> answers
    ) {
        String supplements = answers.stream()
                .map(answer -> "- " + questionText(previousDraft.questions(), answer.questionId())
                        + "：" + answer.answer())
                .collect(Collectors.joining("\n"));
        return new RequirementDraft(
                baseRequirementSummary(originalRequirement.trim())
                        + "\n\n已确认的用户决策：\n" + supplements,
                previousDraft.architectureSummary()
                        + "\n具体模块、数据模型和状态机将在代码生成前根据上述决策固化。",
                previousDraft.assumptions(),
                previousDraft.questions(),
                PROMPT_VERSION
        );
    }

    private List<RequirementQuestion> questionsFor(String requirement) {
        Scenario scenario = Scenario.from(requirement);
        return List.of(
                roleQuestion(scenario),
                workflowQuestion(scenario),
                businessRuleQuestion(scenario),
                integrationQuestion()
        );
    }

    private RequirementQuestion roleQuestion(Scenario scenario) {
        return question(
                "target-users",
                RequirementQuestionCategory.BUSINESS,
                RequirementInputType.MULTIPLE_CHOICE,
                "第一版需要支持哪些使用角色？",
                "角色会决定接口权限、可见数据和操作边界。",
                scenario.roleRecommendation,
                "先覆盖完成核心流程必需的角色，能避免第一版权限体系过度复杂。",
                scenario.roleOptions,
                true,
                true
        );
    }

    private RequirementQuestion workflowQuestion(Scenario scenario) {
        return question(
                "core-workflow",
                RequirementQuestionCategory.TRADEOFF,
                RequirementInputType.SINGLE_CHOICE,
                scenario.workflowQuestion,
                "流程选择会直接决定状态机、接口和事务边界。",
                scenario.workflowRecommendation,
                scenario.workflowRecommendationReason,
                scenario.workflowOptions,
                true,
                true
        );
    }

    private RequirementQuestion businessRuleQuestion(Scenario scenario) {
        return question(
                "business-rules",
                RequirementQuestionCategory.TECHNICAL,
                RequirementInputType.MULTIPLE_CHOICE,
                "第一版需要保证哪些业务规则？",
                "这些选择会被落实为参数校验、状态校验、唯一约束和自动化测试。",
                "采用业务编号防重复、合法状态流转和关键操作留痕",
                "这三项是管理系统最常见的数据正确性底线，高并发保护只在真实场景需要时加入。",
                List.of(
                        option("unique-request", "防止重复业务", "同一业务编号不能重复创建。", "增加唯一键和重复提交处理。", true),
                        option("state-transition", "限制状态跳转", "只能按照批准的流程改变状态。", "增加状态机校验和失败测试。", true),
                        option("operation-audit", "记录关键操作", "保存创建、审批和关闭等关键动作。", "增加操作人和时间等审计字段。", true),
                        option("concurrency-guard", "防止同时修改冲突", "多人同时处理同一数据时只允许一个结果生效。", "增加版本号或条件更新及冲突提示。", scenario == Scenario.INVENTORY)
                ),
                true,
                true
        );
    }

    private RequirementQuestion integrationQuestion() {
        return question(
                "external-integration",
                RequirementQuestionCategory.BUSINESS,
                RequirementInputType.FREE_TEXT,
                "第一版是否必须连接短信、支付、企业微信等外部服务？如没有可以留空。",
                "外部服务会增加超时、重试、配置和失败恢复设计。",
                "第一版先不接外部服务",
                "先完成内部业务闭环更容易验证；确认确有业务需要时再增加对应适配器。",
                List.of(),
                false,
                true
        );
    }

    private RequirementQuestion question(
            String id,
            RequirementQuestionCategory category,
            RequirementInputType inputType,
            String text,
            String reason,
            String recommendation,
            String recommendationReason,
            List<RequirementOption> options,
            boolean required,
            boolean allowCustomAnswer
    ) {
        return new RequirementQuestion(
                id, category, inputType, text, reason, recommendation, recommendationReason,
                options, required, allowCustomAnswer, false
        );
    }

    private static RequirementOption option(
            String id,
            String label,
            String description,
            String impact,
            boolean recommended
    ) {
        return new RequirementOption(id, label, description, impact, recommended);
    }

    private String questionText(List<RequirementQuestion> questions, String questionId) {
        return questions.stream()
                .filter(question -> question.id().equals(questionId))
                .map(RequirementQuestion::question)
                .findFirst()
                .orElse(questionId);
    }

    private String defaultArchitecture() {
        return "第一版采用 Spring Boot 模块化单体：按业务领域划分模块，使用 REST API、DTO、Service、MyBatis-Plus 和 Flyway；"
                + "认证、权限、异常处理和审计作为共享基础能力。网络调用与数据库事务分离，生成结果必须通过 Maven 编译和测试。";
    }

    private String baseRequirementSummary(String requirement) {
        return "目标：构建“" + requirement + "”。AI 判断这是一个管理与业务流程型后端，第一版先完成可运行的核心业务闭环，"
                + "并把角色、流程和关键规则转化为明确的代码约束。";
    }

    private enum Scenario {
        WORK_ORDER(
                "报修人、处理人员和管理员",
                List.of(
                        option("reporter", "报修人", "提交问题并查看处理进度。", "需要限制只能查看自己提交的数据。", true),
                        option("handler", "处理人员", "受理、处理并填写结果。", "需要处理队列和状态变更权限。", true),
                        option("administrator", "管理员", "分派任务、管理人员并查看全部数据。", "需要管理接口和全局数据权限。", true)
                ),
                "工单处理完成后采用哪种验收方式？",
                "由报修人确认后完成",
                "企业内部工单需要明确交付和责任边界，同时避免处理人员单方面结束工单。",
                List.of(
                        option("reporter-confirm", "报修人确认", "处理完成后由报修人验收。", "增加待验收状态和确认接口。", true),
                        option("handler-complete", "处理后直接完成", "处理人员提交结果后立即结束。", "状态更少，但缺少用户验收。", false),
                        option("timeout-complete", "超时自动完成", "待验收超过期限后自动结束。", "需要定时任务、通知和超时规则。", false)
                )
        ),
        APPOINTMENT(
                "预约人、服务人员和管理员",
                List.of(
                        option("requester", "预约人", "选择时间并提交或取消预约。", "需要个人预约记录和可预约时间查询。", true),
                        option("provider", "服务人员", "查看并处理分配给自己的预约。", "需要服务人员排期和数据隔离。", true),
                        option("administrator", "管理员", "维护资源、排班和全部预约。", "需要资源配置和管理权限。", true)
                ),
                "用户提交预约后采用哪种确认方式？",
                "服务人员确认后生效",
                "可以先校验资源和时间冲突，适合大多数需要人工安排的预约业务。",
                List.of(
                        option("manual-confirm", "人工确认", "服务人员确认后预约生效。", "增加待确认状态和确认接口。", true),
                        option("direct-confirm", "提交即生效", "时间可用时立即预约成功。", "流程较短，但并发冲突控制要求更高。", false),
                        option("wait-list", "候补排队", "时间已满时进入候补队列。", "增加候补顺序和释放名额后的递补逻辑。", false)
                )
        ),
        INVENTORY(
                "仓库操作员、审核人员和管理员",
                List.of(
                        option("operator", "仓库操作员", "创建入库、出库和盘点单。", "需要库存操作权限和单据归属。", true),
                        option("auditor", "审核人员", "审核库存变更单据。", "需要审核队列和职责分离。", true),
                        option("administrator", "管理员", "维护物料、仓库和人员。", "需要基础资料及全局查询权限。", true)
                ),
                "库存数量在什么时候正式变化？",
                "单据审核通过后变更库存",
                "先审核再扣减可以减少误操作，并让库存变化具备清晰业务凭证。",
                List.of(
                        option("after-approval", "审核后变更", "单据审核通过后更新库存。", "增加草稿、待审核和已完成状态。", true),
                        option("on-submit", "提交时变更", "操作员提交后立即更新库存。", "流程更短，但撤销和误操作处理更复杂。", false),
                        option("reservation", "先冻结再确认", "先冻结可用数量，完成后正式扣减。", "增加冻结库存、释放和超时处理。", false)
                )
        ),
        CONTENT(
                "编辑、审核人员和管理员",
                List.of(
                        option("editor", "编辑", "创建和修改内容草稿。", "需要草稿归属和编辑权限。", true),
                        option("reviewer", "审核人员", "审核并决定是否发布。", "需要审核队列和审核记录。", true),
                        option("administrator", "管理员", "管理栏目、人员和全部内容。", "需要全局配置及管理权限。", true)
                ),
                "内容采用哪种发布流程？",
                "审核通过后发布",
                "审核可以降低错误内容直接上线的风险，并保留清晰的发布责任。",
                List.of(
                        option("review-publish", "审核后发布", "编辑提交，审核通过后上线。", "增加待审核、驳回和已发布状态。", true),
                        option("direct-publish", "编辑直接发布", "有权限的编辑可以直接上线。", "流程更短，但需要更严格的编辑权限。", false),
                        option("scheduled-publish", "定时发布", "审核后在指定时间自动上线。", "增加发布时间、定时任务和失败恢复。", false)
                )
        ),
        GENERIC(
                "业务人员、处理人员和管理员",
                List.of(
                        option("requester", "业务发起人", "创建申请并查看自己的处理进度。", "需要数据归属和个人查询权限。", true),
                        option("operator", "业务处理人", "处理、审核或完成业务任务。", "需要待办列表和状态变更权限。", true),
                        option("administrator", "管理员", "维护规则、人员并查看全部数据。", "需要管理接口和全局数据权限。", true)
                ),
                "核心业务采用哪种处理流程？",
                "提交后由处理人员受理，完成后由发起人确认",
                "该流程责任边界清晰，适用于多数企业内部管理系统。",
                List.of(
                        option("accept-and-confirm", "受理并确认", "提交、受理、处理、确认后完成。", "状态完整，适合需要交付确认的业务。", true),
                        option("approval-flow", "提交并审批", "提交、审核通过后直接完成。", "适合审批类业务，状态相对精简。", false),
                        option("direct-process", "直接处理", "提交后由处理人直接完成。", "开发较快，但责任和验收边界较弱。", false)
                )
        );

        private final String roleRecommendation;
        private final List<RequirementOption> roleOptions;
        private final String workflowQuestion;
        private final String workflowRecommendation;
        private final String workflowRecommendationReason;
        private final List<RequirementOption> workflowOptions;

        Scenario(
                String roleRecommendation,
                List<RequirementOption> roleOptions,
                String workflowQuestion,
                String workflowRecommendation,
                String workflowRecommendationReason,
                List<RequirementOption> workflowOptions
        ) {
            this.roleRecommendation = roleRecommendation;
            this.roleOptions = roleOptions;
            this.workflowQuestion = workflowQuestion;
            this.workflowRecommendation = workflowRecommendation;
            this.workflowRecommendationReason = workflowRecommendationReason;
            this.workflowOptions = workflowOptions;
        }

        private static Scenario from(String requirement) {
            if (requirement.contains("工单") || requirement.contains("维修") || requirement.contains("报修")) {
                return WORK_ORDER;
            }
            if (requirement.contains("预约")) {
                return APPOINTMENT;
            }
            if (requirement.contains("库存") || requirement.contains("仓库") || requirement.contains("出入库")) {
                return INVENTORY;
            }
            if (requirement.contains("内容") || requirement.contains("文章") || requirement.contains("发布")) {
                return CONTENT;
            }
            return GENERIC;
        }
    }
}
