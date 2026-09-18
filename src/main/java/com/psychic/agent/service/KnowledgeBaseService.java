package com.psychic.agent.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * 知识库服务：知识入库管线（加载 → 文档化 → 向量化 → 写入 Milvus）与 RAG 检索
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private static final int TOP_K = 3;

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore milvusVectorStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动时从 knowledge_base.json 导入知识库（已有数据则跳过）
     */
    public int initializeFromJson() {
        try {
            if (!milvusVectorStore.isAvailable()) {
                log.warn("Milvus 不可用，跳过知识库导入");
                return 0;
            }
            if (milvusVectorStore.count() > 0) {
                log.info("Milvus 已有知识库数据，跳过导入。当前条目数: {}", milvusVectorStore.count());
                return 0;
            }

            List<Map<String, String>> qaList = loadKnowledgeJson();
            int imported = addQABatch(qaList);
            log.info("知识库初始化导入完成，新增 {} 条", imported);
            return imported;
        } catch (Exception e) {
            log.error("知识库初始化失败: {}", e.getMessage(), e);
            return 0;
        }
    }

    /**
     * RAG 检索：查询向量化 → Milvus HNSW Top-K → 拼接为上下文
     */
    public String retrieve(String query) {
        if (query == null || query.isBlank() || !milvusVectorStore.isAvailable()) {
            return "";
        }
        try {
            List<Float> queryEmbedding = embeddingService.embed(query);
            if (queryEmbedding.isEmpty()) {
                log.warn("查询向量化失败，跳过 RAG 检索");
                return "";
            }
            List<String> results = milvusVectorStore.search(queryEmbedding, TOP_K);
            return String.join("\n\n", results);
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 批量导入问答对：Q/A 拼接为文档 → 向量化 → 写入 Milvus
     */
    public int addQABatch(List<Map<String, String>> qaList) {
        if (qaList == null || qaList.isEmpty() || !milvusVectorStore.isAvailable()) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();

        int index = 0;
        for (Map<String, String> qa : qaList) {
            String question = qa.getOrDefault("question", "");
            String answer = qa.getOrDefault("answer", "");
            if (question.isBlank() || answer.isBlank()) {
                continue;
            }
            String document = "Q: " + question + "\nA: " + answer;
            List<Float> embedding = embeddingService.embed(document);
            if (embedding.isEmpty()) {
                log.warn("第 {} 条知识向量化失败，跳过", index);
                continue;
            }
            ids.add("kb-" + UUID.randomUUID());
            documents.add(document);
            embeddings.add(embedding);
            index++;
        }
        milvusVectorStore.insert(ids, documents, embeddings);
        return ids.size();
    }

    private List<Map<String, String>> loadKnowledgeJson() {
        try (InputStream inputStream = new ClassPathResource("knowledge_base.json").getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<List<Map<String, String>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("读取 knowledge_base.json 失败", e);
        }
    }
}
