package com.manga.agent.execution;

import com.manga.agent.domain.CreativeErrorCode;
import io.agentscope.core.model.transport.HttpTransportException;
import io.agentscope.extensions.model.openai.exception.OpenAIException;
import org.junit.jupiter.api.Test;

import java.net.SocketException;

import static org.assertj.core.api.Assertions.assertThat;

/** 校验 OpenAI 兼容流式异常的公开分类与信息边界。 */
class CreativeExecutionFailureTests {

    @Test
    void upstreamStreamResetReportsModelConnectionFailure() {
        var transport = new HttpTransportException("fixture detail", new SocketException("Connection reset"));
        var failure = new OpenAIException("provider response omitted", transport);

        var classified = CreativeExecutionFailure.classify(failure);
        assertThat(classified.errorCode()).isEqualTo(CreativeErrorCode.MODEL_CONNECTION_FAILED);
        assertThat(classified.getMessage()).isEqualTo(CreativeExecutionFailure.CONNECTION);
        assertThat(classified.getCause()).isSameAs(failure);
    }

    @Test
    void nonModelConnectionErrorsKeepTheirOriginalCategory() {
        var failure = new IllegalStateException("internal database connection", new SocketException("Connection reset"));

        assertThat(CreativeExecutionFailure.classify(failure).errorCode())
                .isEqualTo(CreativeErrorCode.CAPABILITY_EXECUTION_FAILED);
    }

    @Test
    void explicitHttpErrorsKeepTheirSpecificCategory() {
        var failure = new OpenAIException("forbidden", 403, "fixture response body");

        assertThat(CreativeExecutionFailure.classify(failure).errorCode())
                .isEqualTo(CreativeErrorCode.MODEL_AUTHENTICATION_FAILED);
    }
}
