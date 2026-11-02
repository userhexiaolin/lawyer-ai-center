package org.ruoyi.service.chat.impl.provider;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.ruoyi.common.chat.domain.dto.request.ChatRequest;
import org.ruoyi.common.chat.domain.vo.chat.ChatModelVo;
import org.ruoyi.common.chat.security.CustomApiCredentialPolicy;
import org.ruoyi.enums.ChatModeType;
import org.ruoyi.observability.MyChatModelListener;
import org.ruoyi.service.chat.AbstractChatService;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

/** vLLM OpenAI-compatible chat; HTTP/1.1 avoids Uvicorn dropping h2c upgrade request bodies. */
@Service
public class VllmServiceImpl implements AbstractChatService {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(180);

    @Override
    public StreamingChatModel buildStreamingChatModel(ChatModelVo config, ChatRequest request) {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(validateConfiguration(config))
            .httpClientBuilder(httpClientBuilder())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(request == null ? null : request.getGenerationTemperature())
            .customParameters(org.ruoyi.common.chat.config.NonThinking.parameters())
            .timeout(DEFAULT_TIMEOUT)
            .listeners(List.of(new MyChatModelListener()))
            .returnThinking(false)
            .build();
    }

    @Override
    public ChatModel buildChatModel(ChatModelVo config) {
        return OpenAiChatModel.builder()
            .baseUrl(validateConfiguration(config))
            .httpClientBuilder(httpClientBuilder())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .timeout(DEFAULT_TIMEOUT)
            .customParameters(org.ruoyi.common.chat.config.NonThinking.parameters())
            .build();
    }

    @Override
    public String getProviderName() {
        return ChatModeType.VLLM.getCode();
    }

    private static JdkHttpClientBuilder httpClientBuilder() {
        return JdkHttpClient.builder()
            .httpClientBuilder(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1));
    }

    private String validateConfiguration(ChatModelVo config) {
        if (!getProviderName().equals(config.getProviderCode())) {
            throw new IllegalArgumentException("模型厂商与 vLLM 适配器不匹配");
        }
        return CustomApiCredentialPolicy.normalizeBaseUrl(
            CustomApiCredentialPolicy.OPENAI_PROVIDER, config.getApiHost());
    }
}
