package com.devmate.project.controller;

import com.devmate.common.api.ApiResponse;
import com.devmate.common.api.PageResponse;
import com.devmate.project.dto.CreateProjectRequest;
import com.devmate.project.dto.ProjectQueryRequest;
import com.devmate.project.dto.ProjectResponse;
import com.devmate.project.dto.UpdateProjectRequest;
import com.devmate.project.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@Validated
@Tag(name = "项目管理", description = "管理待解析、理解和审查的 Java 项目")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "创建项目", description = "保存项目元数据；创建后状态为 CREATED，不会自动拉取源码。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "字段校验失败或 Git 项目缺少仓库地址",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "未登录或令牌已过期",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            @Valid @RequestBody CreateProjectRequest request
    ) {
        ProjectResponse project = projectService.createProject(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(project));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "查询项目详情")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "项目不存在",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<ProjectResponse> getProject(
            @Parameter(description = "项目 ID", required = true,
                    schema = @Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "2084116785588305922"))
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        return ApiResponse.success(projectService.getProject(projectId));
    }

    @GetMapping
    @Operation(summary = "分页查询项目", description = "只返回当前登录用户有权访问且未删除的项目。")
    public ApiResponse<PageResponse<ProjectResponse>> listProjects(
            @Valid @ModelAttribute ProjectQueryRequest request
    ) {
        return ApiResponse.success(projectService.listProjects(request));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "更新项目元数据",
            description = "完整更新允许编辑的字段，不允许客户端修改项目状态和索引版本。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "字段或业务规则校验失败",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "项目不存在",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<ProjectResponse> updateProject(
            @Parameter(description = "项目 ID", required = true,
                    schema = @Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "2084116785588305922"))
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId,
            @Valid @RequestBody UpdateProjectRequest request
    ) {
        return ApiResponse.success(projectService.updateProject(projectId, request));
    }

    @DeleteMapping("/{projectId}")
    @Operation(summary = "删除项目", description = "逻辑删除项目；重复删除按项目不存在处理。")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "项目不存在",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409", description = "项目仍被运行中的任务占用",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class)))
    })
    public ApiResponse<Void> deleteProject(
            @Parameter(description = "项目 ID", required = true,
                    schema = @Schema(type = "string", pattern = "^[1-9][0-9]*$", example = "2084116785588305922"))
            @Positive(message = "项目ID必须大于0") @PathVariable Long projectId
    ) {
        projectService.deleteProject(projectId);
        return ApiResponse.success(null);
    }
}
