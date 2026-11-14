package com.manga.agent.execution;

import com.manga.agent.domain.CreativeErrorCode;
import com.manga.agent.domain.CreativeRuntimeMessages;
import com.manga.agent.harness.AgentRuntimeException;
import io.agentscope.core.model.ModelHttpException;
import io.agentscope.core.model.transport.HttpTransportException;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import reactor.core.Exceptions;

/** 将异常链转换为稳定且不包含供应商响应或凭据的公开错误。 */
public final class CreativeExecutionFailure {
    /** 达到总时限后的可执行提示。 */
    public static final String TIMEOUT = "模型调用已达到总时限，请缩小章节内容或重新生成；本次调用不会自动重发";
    /** 供应商数据流停止后的提示。 */
    public static final String IDLE = "模型超过无响应时限，请检查供应商连接后重新生成";
    /** 失联后禁止隐式重发，以免产生重复费用。 */
    public static final String UNCERTAIN = "执行租约已失联，本步骤曾调用模型；为避免重复计费，已停止自动恢复，请核对后重新生成";
    /** 事件持久化跟不上供应商输出。 */
    public static final String BACKPRESSURE = "事件写入队列已满，已停止生成；请检查数据库连接和写入延迟后重新生成";
    /** 供应商认证失败后的处理提示。 */
    public static final String AUTHENTICATION = "模型认证失败，请检查所选文本模型的密钥和权限";
    /** 供应商限流后的处理提示。 */
    public static final String RATE_LIMIT = "供应商限流，请稍后重新生成或调整模型配置";
    /** 供应商返回异常协议后的处理提示。 */
    public static final String PROTOCOL = "模型服务返回异常，请检查模型名称、协议和供应商状态";
    private CreativeExecutionFailure() { }

    /** 优先保留领域错误，只按类型和 HTTP 状态归类第三方失败。 */
    public static AgentRuntimeException classify(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; Objects.nonNull(current) && depth < 16; depth++, current = current.getCause()) {
            if (current instanceof AgentRuntimeException domain) return domain;
            if (Exceptions.isOverflow(current)) return new AgentRuntimeException(CreativeErrorCode.EVENT_BACKPRESSURE_OVERFLOW, BACKPRESSURE, failure);
            if (current instanceof TimeoutException) return new AgentRuntimeException(CreativeErrorCode.MODEL_TIMEOUT, TIMEOUT, failure);
            Integer httpStatus = current instanceof ModelHttpException http ? http.getStatusCode()
                    : current instanceof HttpTransportException transport ? transport.getStatusCode() : null;
            if (Objects.nonNull(httpStatus)) {
                int status = httpStatus;
                if (status == 401 || status == 403) return new AgentRuntimeException(CreativeErrorCode.MODEL_AUTHENTICATION_FAILED,
                        AUTHENTICATION, failure);
                if (status == 429) return new AgentRuntimeException(CreativeErrorCode.MODEL_RATE_LIMITED,
                        RATE_LIMIT, failure);
                return new AgentRuntimeException(CreativeErrorCode.MODEL_PROTOCOL_ERROR,
                        PROTOCOL, failure);
            }
            if (current.getCause() == current) break;
        }
        return new AgentRuntimeException(CreativeErrorCode.CAPABILITY_EXECUTION_FAILED,
                CreativeRuntimeMessages.AGENT_STEP_EXECUTION_FAILED, failure);
    }
}
