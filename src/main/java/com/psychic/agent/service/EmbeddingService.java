package com.psychic.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 向量嵌入服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private static final int VECTOR_DIMENSION = 1024;
    private final WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.ai.openai.embedding.options.model:embedding-2}")
    private String embeddingModel;

    public List<Float> embed(String text) {
        try {
            Map<String, Object> requestBody = Map.of(
                    "model", embeddingModel,
                    "input", text
            );

            String response = webClient.post()
                    .uri("/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .block();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isArray() && !data.isEmpty()) {
                JsonNode embeddingNode = data.get(0).path("embedding");
                if (embeddingNode.isArray()) {
                    List<Float> vector = new ArrayList<>(embeddingNode.size());
                    for (JsonNode value : embeddingNode) {
                        vector.add((float) value.asDouble());
                    }
                    return vector;
                }
            }
        } catch (Exception e) {
            log.warn("Embedding API 调用失败，返回零向量: {}", e.getMessage());
        }
        return Collections.nCopies(VECTOR_DIMENSION, 0.0f);
    }

    public List<List<Float>> embed(List<String> texts) {
        List<List<Float>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
