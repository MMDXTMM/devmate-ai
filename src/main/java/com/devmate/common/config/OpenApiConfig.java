package com.devmate.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    private static final Set<String> PUBLIC_API_OPERATIONS = Set.of(
            "POST /api/auth/register",
            "POST /api/auth/login",
            "GET /api/health"
    );

    @Bean
    public OpenAPI devMateOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("DevMate AI API")
                        .version("0.2.0")
                        .description("Java 项目理解与 RAG 代码审查 Agent 的后端接口契约。"))
                .components(new Components().addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后获得的访问令牌")
                ));
    }

    @Bean
    public OpenApiCustomizer devMateContractCustomizer() {
        return openApi -> {
            normalizeIdSchemas(openApi);
            openApi.getPaths().forEach((path, pathItem) ->
                    pathItem.readOperationsMap().forEach((method, operation) -> {
                        normalizeIdParameters(operation.getParameters());
                        String operationKey = method.name() + " " + path;
                        if (!PUBLIC_API_OPERATIONS.contains(operationKey) && path.startsWith("/api/")) {
                            if (operation.getSecurity() == null || operation.getSecurity().stream()
                                    .noneMatch(requirement -> requirement.containsKey(BEARER_AUTH))) {
                                operation.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
                            }
                            addStandardAuthResponses(operation.getResponses());
                        }
                    })
            );
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void normalizeIdSchemas(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        openApi.getComponents().getSchemas().values().forEach(schema -> {
            if (schema.getProperties() == null) {
                return;
            }
            schema.getProperties().forEach((propertyName, propertyValue) -> {
                String fieldName = String.valueOf(propertyName);
                Schema<?> property = (Schema<?>) propertyValue;
                if (isIdName(fieldName) && isInteger(property)) {
                    schema.getProperties().put(propertyName, stringIdSchema(property));
                } else if (isIdsName(fieldName) && property.getItems() != null
                        && isInteger(property.getItems())) {
                    property.setItems(stringIdSchema(property.getItems()));
                }
            });
        });
    }

    private void normalizeIdParameters(java.util.List<Parameter> parameters) {
        if (parameters == null) {
            return;
        }
        parameters.stream()
                .filter(parameter -> isIdName(parameter.getName()) && parameter.getSchema() != null)
                .filter(parameter -> isInteger(parameter.getSchema()))
                .forEach(parameter -> parameter.setSchema(stringIdSchema(parameter.getSchema())));
    }

    private boolean isIdName(String name) {
        return name != null && (name.equals("id") || name.endsWith("Id"));
    }

    private boolean isIdsName(String name) {
        return name != null && name.endsWith("Ids");
    }

    private boolean isInteger(Schema<?> schema) {
        return "integer".equals(schema.getType())
                || (schema.getTypes() != null && schema.getTypes().contains("integer"));
    }

    private StringSchema stringIdSchema(Schema<?> source) {
        StringSchema target = new StringSchema();
        target.setDescription(source.getDescription());
        target.setExample(source.getExample());
        target.setReadOnly(source.getReadOnly());
        target.setWriteOnly(source.getWriteOnly());
        target.setNullable(source.getNullable());
        target.setPattern("^[1-9][0-9]*$");
        target.setTypes(Set.of("string"));
        return target;
    }

    private void addStandardAuthResponses(ApiResponses responses) {
        if (responses == null) {
            return;
        }
        responses.putIfAbsent("401", new io.swagger.v3.oas.models.responses.ApiResponse()
                .description("未登录或访问令牌无效"));
        responses.putIfAbsent("403", new io.swagger.v3.oas.models.responses.ApiResponse()
                .description("已登录但无权访问该资源"));
    }
}
