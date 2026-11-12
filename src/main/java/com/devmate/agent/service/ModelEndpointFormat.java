package com.devmate.agent.service;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import java.net.URI;

/** Canonical API root; credentials, query strings and operation paths never become a stored base URL. */
public final class ModelEndpointFormat {
    private ModelEndpointFormat() { }

    public static String normalize(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null || uri.getPort() == 0
                    || uri.getPort() > 65535 || uri.getRawPath().contains("%") || uri.getRawPath().contains("..")) throw new IllegalArgumentException();
            String path = uri.getPath().replaceAll("/+$", "");
            for (String operation : new String[]{"/chat/completions", "/responses"}) {
                if (path.endsWith(operation)) path = path.substring(0, path.length() - operation.length());
            }
            return new URI("https", null, uri.getHost().toLowerCase(java.util.Locale.ROOT),
                    uri.getPort() == 443 ? -1 : uri.getPort(), path, null, null).toString();
        } catch (Exception exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "API 请求地址须为不含凭据、查询参数或片段的 HTTPS 基础地址");
        }
    }
}
