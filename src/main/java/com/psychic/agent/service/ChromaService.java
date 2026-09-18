package com.psychic.agent.service;

import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

/**
 * ChromaDB 向量数据库服务（基于 HTTP API）
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChromaService {

    @Value("${chromadb.url:http://localhost:8000}")
    private String chromaUrl;

    @Value("${chromadb.collection:psychelink-knowledge}")
    private String collectionName;

    @Value("${chromadb.tenant:default_tenant}")
    private String tenant;

    @Value("${chromadb.database:default_database}")
    private String database;

    private final RestTemplate restTemplate = new RestTemplate();
    private volatile String collectionId;
    private volatile Boolean useV2;

    public void initialize() {
        try {
            this.collectionId = ensureCollection();
            log.info("ChromaService 初始化完成，collectionId={}, count={}", this.collectionId, count());
        } catch (Exception e) {
            log.warn("ChromaService 初始化失败，跳过知识库功能: {}", e.getMessage());
        }
    }

    /**
     * 检查 ChromaDB 是否可用
     */
    public boolean isAvailable() {
        return collectionId != null && !collectionId.isBlank();
    }

    public void addDocuments(List<String> ids, List<String> documents, List<Map<String, Object>> metadatas, List<List<Float>> embeddings) {
        if (ids == null || documents == null || embeddings == null) {
            return;
        }
        if (ids.size() != documents.size() || ids.size() != embeddings.size()) {
            throw new IllegalArgumentException("ids/documents/embeddings size 不一致");
        }

        String activeCollectionId = ensureCollection();
        String url = collectionOperationBase(activeCollectionId) + "/add";

        Map<String, Object> payload = new HashMap<>();
        payload.put("ids", ids);
        payload.put("documents", documents);
        payload.put("metadatas", metadatas != null ? metadatas : Collections.emptyList());
        payload.put("embeddings", embeddings);

        restTemplate.postForEntity(url, jsonEntity(payload), Object.class);
        log.info("Chroma addDocuments 成功，新增: {}", ids.size());
    }

    @WithSpan("rag")
    public List<Map<String, Object>> querySimilar(List<Float> queryEmbedding, int nResults) {
        log.info("[OTEL-TRACE] ChromaDB querySimilar called, nResults={}", nResults);
        if (queryEmbedding == null || queryEmbedding.isEmpty()) {
            return Collections.emptyList();
        }
        String activeCollectionId = ensureCollection();
        String url = collectionOperationBase(activeCollectionId) + "/query";

        Map<String, Object> payload = new HashMap<>();
        payload.put("query_embeddings", List.of(queryEmbedding));
        payload.put("n_results", Math.max(1, nResults));
        payload.put("include", List.of("documents", "metadatas", "distances"));

        ResponseEntity<Map> response = restTemplate.postForEntity(url, jsonEntity(payload), Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            return Collections.emptyList();
        }

        List<String> docs = firstNestedStringList(body.get("documents"));
        List<Map<String, Object>> metas = firstNestedMapList(body.get("metadatas"));
        List<Double> distances = firstNestedDoubleList(body.get("distances"));

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("document", docs.get(i));
            item.put("metadata", i < metas.size() ? metas.get(i) : Map.of());
            item.put("distance", i < distances.size() ? distances.get(i) : null);
            results.add(item);
        }
        return results;
    }

    public long count() {
        String activeCollectionId = ensureCollection();
        String url = collectionOperationBase(activeCollectionId) + "/count";
        try {
            ResponseEntity<Long> response = restTemplate.getForEntity(url, Long.class);
            if (response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception ignore) {
            // 部分版本返回 {"count":N}
            try {
                ResponseEntity<Map> response = restTemplate.postForEntity(url, jsonEntity(Map.of()), Map.class);
                Object value = response.getBody() != null ? response.getBody().get("count") : null;
                if (value instanceof Number number) {
                    return number.longValue();
                }
            } catch (Exception inner) {
                log.warn("Chroma count 调用失败: {}", inner.getMessage());
            }
        }
        return 0L;
    }

    private String collectionOperationBase(String activeCollectionId) {
        if (Boolean.TRUE.equals(useV2)) {
            return baseApiV2Collections() + "/" + activeCollectionId;
        }
        return baseApiV1() + "/collections/" + activeCollectionId;
    }

    private String ensureCollection() {
        if (this.collectionId != null && !this.collectionId.isBlank()) {
            return this.collectionId;
        }
        List<Map<String, Object>> collections = listCollections();
        for (Map<String, Object> c : collections) {
            String name = String.valueOf(c.get("name"));
            if (collectionName.equals(name)) {
                this.collectionId = String.valueOf(c.get("id"));
                return this.collectionId;
            }
        }

        String url = collectionsBaseUrl();
        Map<String, Object> payload = Map.of("name", collectionName);
        ResponseEntity<Map> created = restTemplate.postForEntity(url, jsonEntity(payload), Map.class);
        Map<String, Object> body = created.getBody();
        if (body == null || body.get("id") == null) {
            throw new IllegalStateException("创建 Chroma collection 失败");
        }
        this.collectionId = String.valueOf(body.get("id"));
        return this.collectionId;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listCollections() {
        // 优先尝试 v2
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(baseApiV2Collections(), List.class);
            this.useV2 = true;
            return convertToMapList(response.getBody());
        } catch (Exception v2Error) {
            // 回退 v1
            ResponseEntity<List> response = restTemplate.getForEntity(baseApiV1() + "/collections", List.class);
            this.useV2 = false;
            return convertToMapList(response.getBody());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> convertToMapList(Object body) {
        if (body instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add((Map<String, Object>) map);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private String collectionsBaseUrl() {
        if (Boolean.TRUE.equals(useV2)) {
            return baseApiV2Collections();
        }
        return baseApiV1() + "/collections";
    }

    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    private String baseApiV1() {
        return UriComponentsBuilder.fromHttpUrl(chromaUrl).path("/api/v1").toUriString();
    }

    private String baseApiV2Collections() {
        return UriComponentsBuilder.fromHttpUrl(chromaUrl)
                .path("/api/v2/tenants/")
                .path(tenant)
                .path("/databases/")
                .path(database)
                .path("/collections")
                .toUriString();
    }

    @SuppressWarnings("unchecked")
    private List<String> firstNestedStringList(Object value) {
        if (!(value instanceof List<?> outer) || outer.isEmpty() || !(outer.get(0) instanceof List<?> inner)) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        for (Object item : inner) {
            result.add(item == null ? "" : String.valueOf(item));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> firstNestedMapList(Object value) {
        if (!(value instanceof List<?> outer) || outer.isEmpty() || !(outer.get(0) instanceof List<?> inner)) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : inner) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            } else {
                result.add(Map.of());
            }
        }
        return result;
    }

    private List<Double> firstNestedDoubleList(Object value) {
        if (!(value instanceof List<?> outer) || outer.isEmpty() || !(outer.get(0) instanceof List<?> inner)) {
            return Collections.emptyList();
        }
        List<Double> result = new ArrayList<>();
        for (Object item : inner) {
            if (item instanceof Number number) {
                result.add(number.doubleValue());
            }
        }
        return result;
    }
}
