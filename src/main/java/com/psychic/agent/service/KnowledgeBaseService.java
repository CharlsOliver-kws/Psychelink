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
 * 知识库服务 - RAG 检索与生成
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeBaseService {

    private final EmbeddingService embeddingService;
    private final ChromaService chromaService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public int initializeFromJson() {
        try {
            chromaService.initialize();
            if (chromaService.count() > 0) {
                log.info("Chroma 已有数据，跳过初始化导入。当前条目数: {}", chromaService.count());
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
     * 检索相关知识（基于向量检索）
     */
    public String retrieve(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        // ChromaDB 不可用时跳过 RAG
        if (!chromaService.isAvailable()) {
            log.debug("ChromaDB 不可用，跳过向量检索");
            return "";
        }
        try {
            List<Float> queryEmbedding = embeddingService.embed(query);
            List<Map<String, Object>> results = chromaService.querySimilar(queryEmbedding, 3);
            StringBuilder context = new StringBuilder();
            for (Map<String, Object> item : results) {
                String document = String.valueOf(item.getOrDefault("document", ""));
                if (!document.isBlank()) {
                    if (!context.isEmpty()) {
                        context.append("\n\n");
                    }
                    context.append(document);
                }
            }
            return context.toString();
        } catch (Exception e) {
            log.error("向量检索失败: {}", e.getMessage(), e);
            return "";
        }
    }

    /**
     * 构建知识库索引（当前项目保留空实现，避免破坏接口）
     */
    public void buildIndex(String content, String metadata) {
        log.debug("buildIndex 调用: contentLength={}, metadata={}", content != null ? content.length() : 0, metadata);
    }

    /**
     * 添加问答对到知识库
     */
    public void addQA(String question, String answer, String category, String riskLevel, String source) {
        List<Map<String, String>> singleton = new ArrayList<>();
        Map<String, String> item = new HashMap<>();
        item.put("question", question);
        item.put("answer", answer);
        item.put("category", category);
        item.put("riskLevel", riskLevel);
        item.put("source", source);
        singleton.add(item);
        addQABatch(singleton);
    }

    /**
     * 批量添加问答对到知识库
     */
    public int addQABatch(List<Map<String, String>> qaList) {
        if (qaList == null || qaList.isEmpty()) {
            return 0;
        }
        List<String> ids = new ArrayList<>();
        List<String> documents = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        List<List<Float>> embeddings = new ArrayList<>();

        int index = 0;
        for (Map<String, String> qa : qaList) {
            String question = qa.getOrDefault("question", "");
            String answer = qa.getOrDefault("answer", "");
            if (question.isBlank() || answer.isBlank()) {
                continue;
            }
            String document = "Q: " + question + "\nA: " + answer;
            ids.add("kb-" + System.currentTimeMillis() + "-" + index);
            documents.add(document);
            embeddings.add(embeddingService.embed(document));
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("question", question);
            metadata.put("category", qa.getOrDefault("category", "general"));
            metadata.put("riskLevel", qa.getOrDefault("riskLevel", "UNKNOWN"));
            metadata.put("source", qa.getOrDefault("source", "knowledge_base.json"));
            metadatas.add(metadata);
            index++;
        }
        chromaService.addDocuments(ids, documents, metadatas, embeddings);
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
