package io.github.charlsoliver.psychelink.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量嵌入服务：封装 Spring AI EmbeddingModel（OpenAI 兼容端点，默认智谱 embedding-2）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    /**
     * 单条文本向量化
     */
    public List<Float> embed(String text) {
        try {
            float[] vector = embeddingModel.embed(text);
            List<Float> result = new ArrayList<>(vector.length);
            for (float v : vector) {
                result.add(v);
            }
            return result;
        } catch (Exception e) {
            log.warn("Embedding 调用失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 批量向量化
     */
    public List<List<Float>> embed(List<String> texts) {
        List<List<Float>> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }
}
