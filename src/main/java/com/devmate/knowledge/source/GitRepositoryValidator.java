package com.devmate.knowledge.source;

import com.devmate.common.error.BusinessException;
import com.devmate.common.error.ErrorCode;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

@Component
public class GitRepositoryValidator {

    public void validate(String repositoryUrl) {
        URI uri;
        try {
            uri = new URI(repositoryUrl);
        } catch (URISyntaxException exception) {
            throw invalid("Git仓库地址格式不正确");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("第一版源码导入只允许HTTPS Git仓库");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("Git仓库地址必须包含有效域名");
        }
        if (uri.getUserInfo() != null) {
            throw invalid("Git仓库地址不能包含用户名或密码");
        }

        String host = uri.getHost().toLowerCase();
        if ("localhost".equals(host) || host.endsWith(".local") || isPrivateIpLiteral(host)) {
            throw invalid("不允许访问本机或内网Git地址");
        }
    }

    private boolean isPrivateIpLiteral(String host) {
        if (!host.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress();
        } catch (UnknownHostException exception) {
            return true;
        }
    }

    private BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }
}
