package io.github.charlsoliver.psychelink.service;

import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.DataType;
import io.milvus.grpc.SearchResults;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.collection.CreateCollectionParam;
import io.milvus.param.collection.FieldType;
import io.milvus.param.collection.FlushParam;
import io.milvus.param.collection.GetCollectionStatisticsParam;
import io.milvus.param.collection.HasCollectionParam;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.InsertParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.param.index.CreateIndexParam;
import io.milvus.response.GetCollStatResponseWrapper;
import io.milvus.response.QueryResultsWrapper;
import io.milvus.response.SearchResultsWrapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Milvus 向量存储：知识库 Collection 管理 + HNSW 索引 + 向量检索
 *
 * Schema: id (VARCHAR 主键) / text (VARCHAR) / vector (FLOAT_VECTOR)
 * Index : HNSW (M=16, efConstruction=256, Metric=IP)，检索期 ef 可调
 *
 * Milvus 不可用时优雅降级（isAvailable=false），知识库检索返回空上下文，不影响对话主链路。
 */
@Service
@Slf4j
public class MilvusVectorStore {

    private static final String ID_FIELD = "id";
    private static final String TEXT_FIELD = "text";
    private static final String VECTOR_FIELD = "vector";

    private final MilvusServiceClient client;
    private final String collectionName;

    private volatile boolean available = false;

    public MilvusVectorStore(@Value("${milvus.host:localhost}") String host,
                             @Value("${milvus.port:19530}") int port,
                             @Value("${milvus.collection:psychelink_knowledge}") String collectionName,
                             @Value("${milvus.dimension:1024}") int dimension) {
        this.collectionName = collectionName;
        MilvusServiceClient local = null;
        try {
            local = new MilvusServiceClient(ConnectParam.newBuilder()
                    .withHost(host)
                    .withPort(port)
                    .build());
            ensureCollection(local, collectionName, dimension);
            this.available = true;
            log.info("Milvus 连接成功: {}:{}, collection={}", host, port, collectionName);
        } catch (Exception e) {
            log.warn("Milvus 不可用（{}:{}），知识库检索将降级为空上下文: {}", host, port, e.getMessage());
            if (local != null) {
                try {
                    local.close(1000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                local = null;
            }
        }
        this.client = local;
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * 创建 Collection（不存在时）并建立 HNSW 索引、执行 Load
     */
    private void ensureCollection(MilvusServiceClient client, String name, int dimension) {
        R<Boolean> hasResp = client.hasCollection(
                HasCollectionParam.newBuilder().withCollectionName(name).build());
        if (hasResp.getStatus() == R.Status.Success.getCode()
                && Boolean.TRUE.equals(hasResp.getData())) {
            client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).build());
            log.info("Milvus collection 已存在: {}", name);
            return;
        }

        FieldType idField = FieldType.newBuilder()
                .withName(ID_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(128)
                .withPrimaryKey(true)
                .withAutoID(false)
                .build();
        FieldType textField = FieldType.newBuilder()
                .withName(TEXT_FIELD)
                .withDataType(DataType.VarChar)
                .withMaxLength(65535)
                .build();
        FieldType vectorField = FieldType.newBuilder()
                .withName(VECTOR_FIELD)
                .withDataType(DataType.FloatVector)
                .withDimension(dimension)
                .build();

        client.createCollection(CreateCollectionParam.newBuilder()
                .withCollectionName(name)
                .withShardsNum(1)
                .addFieldType(idField)
                .addFieldType(textField)
                .addFieldType(vectorField)
                .build());

        // HNSW 索引：M 越大召回越高但内存越大；efConstruction 为构建期候选集；ef 为查询期可调旋钮
        client.createIndex(CreateIndexParam.newBuilder()
                .withCollectionName(name)
                .withFieldName(VECTOR_FIELD)
                .withIndexType(IndexType.HNSW)
                .withMetricType(MetricType.IP)
                .withExtraParam("{\"M\":16,\"efConstruction\":256}")
                .build());

        client.loadCollection(LoadCollectionParam.newBuilder().withCollectionName(name).build());
        log.info("Milvus collection 已创建并建立 HNSW 索引: {}, dim={}", name, dimension);
    }

    /**
     * 批量写入向量文档
     */
    public void insert(List<String> ids, List<String> texts, List<List<Float>> vectors) {
        if (!available || ids.isEmpty()) {
            return;
        }
        client.insert(InsertParam.newBuilder()
                .withCollectionName(collectionName)
                .withFields(List.of(
                        new InsertParam.Field(ID_FIELD, ids),
                        new InsertParam.Field(TEXT_FIELD, texts),
                        new InsertParam.Field(VECTOR_FIELD, vectors)))
                .build());
        client.flush(FlushParam.newBuilder()
                .withCollectionNames(List.of(collectionName))
                .withSyncFlush(Boolean.FALSE)
                .build());
    }

    /**
     * Top-K 相似检索，返回文本内容（按相似度降序）
     */
    public List<String> search(List<Float> queryVector, int topK) {
        if (!available) {
            return List.of();
        }
        R<SearchResults> resp = client.search(SearchParam.newBuilder()
                .withCollectionName(collectionName)
                .withMetricType(MetricType.IP)
                .withVectorFieldName(VECTOR_FIELD)
                .withFloatVectors(List.of(queryVector))
                .withTopK(topK)
                .withOutFields(List.of(TEXT_FIELD))
                .withParams("{\"ef\":64}")
                .build());
        if (resp.getStatus() != R.Status.Success.getCode()) {
            log.warn("Milvus 检索失败: {}", resp.getMessage());
            return List.of();
        }
        SearchResultsWrapper wrapper = new SearchResultsWrapper(resp.getData().getResults());
        List<String> texts = new ArrayList<>();
        for (QueryResultsWrapper.RowRecord row : wrapper.getRowRecords(0)) {
            Object text = row.get(TEXT_FIELD);
            if (text != null && !String.valueOf(text).isBlank()) {
                texts.add(String.valueOf(text));
            }
        }
        return texts;
    }

    /**
     * 查询 Collection 中记录条数（用于判断是否需要导入知识库）
     */
    public long count() {
        if (!available) {
            return 0;
        }
        try {
            R<io.milvus.grpc.GetCollectionStatisticsResponse> resp =
                    client.getCollectionStatistics(GetCollectionStatisticsParam.newBuilder()
                            .withCollectionName(collectionName).build());
            if (resp.getStatus() == R.Status.Success.getCode()) {
                return new GetCollStatResponseWrapper(resp.getData()).getRowCount();
            }
        } catch (Exception e) {
            log.warn("Milvus 统计失败: {}", e.getMessage());
        }
        return 0;
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try {
                client.close(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
