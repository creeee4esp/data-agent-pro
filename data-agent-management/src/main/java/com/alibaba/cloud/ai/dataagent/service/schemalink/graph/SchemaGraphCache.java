/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.schemalink.graph;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.constant.DocumentMetadataConstant;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.DynamicFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 按数据源缓存表关系图。
 *
 * <p>
 * 冷启动时查两次向量库（表文档一次、列文档按批），再查逻辑关系，然后交给 {@link SchemaGraphBuilder}。之后在 TTL 内直接复用；schema
 * 或逻辑关系变化时调用 {@link #invalidate(Integer)} 主动失效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaGraphCache {

	/**
	 * 单个数据源最多取这么多张表。ES 单次查询有 10,000 条窗口限制，当前数据集约 3,300 张表。
	 */
	private static final int MAX_TABLE_DOCUMENTS = 10_000;

	private final AgentVectorStoreService vectorStoreService;

	private final DatasourceService datasourceService;

	private final DataAgentProperties properties;

	private final Map<Integer, CachedGraph> cache = new ConcurrentHashMap<>();

	public SchemaGraph get(Integer datasourceId) {
		// 配置写的是秒，这里换成毫秒；TTL <= 0 时 isFresh 永远返回 false（每次都重建）
		long ttlMillis = TimeUnit.SECONDS.toMillis(properties.getSchemaLinking().getJoinGraph().getCacheTtlSeconds());

		// 快路径：命中且没过期就直接返回，不进入 compute 的锁
		CachedGraph cached = cache.get(datasourceId);
		if (cached != null && cached.isFresh(ttlMillis)) {
			return cached.getGraph();
		}
		// 慢路径：ConcurrentHashMap.compute 对同一个 key 加锁，保证同一数据源只建一次图
		return cache.compute(datasourceId, (id, existing) -> {
			// 等锁期间别的线程可能已经建好了，再检查一次，避免重复查询向量库
			if (existing != null && existing.isFresh(ttlMillis)) {
				return existing;
			}
			return new CachedGraph(load(id));
		}).getGraph();
	}

	public void invalidate(Integer datasourceId) {
		cache.remove(datasourceId);
	}

	private SchemaGraph load(Integer datasourceId) {
		long start = System.currentTimeMillis();
		// 1. 表文档：一次查询拿全这个数据源的表（当前规模 3,300 张，远小于 10,000 窗口）
		List<Document> tableDocuments = fetchTableDocuments(datasourceId);
		// 打满上限说明可能有表没取到，图会不完整；这种情况至少要在日志里留痕
		if (tableDocuments.size() >= MAX_TABLE_DOCUMENTS) {
			log.warn("Schema graph for datasource {} reached the document limit {}; the graph may be incomplete",
					datasourceId, MAX_TABLE_DOCUMENTS);
		}
		// 2. 表名去重并排序：列查询分批、建图遍历都依赖这个顺序
		List<String> tableNames = sortedTableNames(tableDocuments);
		if (tableNames.isEmpty()) {
			// 数据源还没初始化 schema：不查列、不查逻辑关系，直接返回空图
			log.info("No schema documents for datasource {}; using an empty graph", datasourceId);
			return new SchemaGraph(Collections.emptyList(), Collections.emptyList(), 0);
		}
		// 3. 列文档：按批查询，命名推断需要列名、类型和主键标记
		List<Document> columnDocuments = fetchColumnDocuments(datasourceId, tableNames);
		// 4. 逻辑关系：人工配置的边，来自关系数据库而不是向量库
		List<LogicalRelation> logicalRelations = datasourceService.getLogicalRelations(datasourceId);

		// 5. 交给纯函数建图；这里只负责取数和缓存，算法都在 SchemaGraphBuilder 里
		SchemaGraph graph = SchemaGraphBuilder.build(tableDocuments, columnDocuments, logicalRelations,
				properties.getSchemaLinking().getJoinGraph());
		log.info("Built schema graph for datasource {}: {} tables, {} edges, {} skipped edges in {} ms", datasourceId,
				graph.getTables().size(), graph.edgeCount(), graph.getSkippedEdgeCount(),
				System.currentTimeMillis() - start);
		return graph;
	}

	private List<Document> fetchTableDocuments(Integer datasourceId) {
		// 条件：datasourceId = 当前数据源 AND vectorType = TABLE
		FilterExpressionBuilder b = new FilterExpressionBuilder();
		List<Filter.Expression> conditions = new ArrayList<>();
		conditions.add(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
		conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.TABLE).build());
		// 走上限 10,000 的精确过滤查询，不走相似度检索
		return vectorStoreService.getDocumentsOnlyByFilter(DynamicFilterService.combineWithAnd(conditions),
				MAX_TABLE_DOCUMENTS);
	}

	private List<Document> fetchColumnDocuments(Integer datasourceId, List<String> tableNames) {
		if (tableNames.isEmpty()) {
			return new ArrayList<>();
		}
		// 一批多少张表；批大小至少为 1，避免配置成 0 时死循环
		int batchSize = Math.max(1, properties.getSchemaLinking().getJoinGraph().getColumnFetchBatchSize());
		// 每张表最多多少列：全系统统一的假设（默认 50），用来算每批查询的 topK
		int maxColumnsPerTable = properties.getMaxColumnsPerTable();

		List<Document> columnDocuments = new ArrayList<>();
		for (int start = 0; start < tableNames.size(); start += batchSize) {
			// 当前批的表名区间：[start, start + batchSize)，最后一批可能不足 batchSize
			List<String> batch = tableNames.subList(start, Math.min(start + batchSize, tableNames.size()));
			// 条件：datasourceId = 当前数据源 AND vectorType = COLUMN AND tableName IN (这一批表名)
			FilterExpressionBuilder b = new FilterExpressionBuilder();
			List<Filter.Expression> conditions = new ArrayList<>();
			conditions.add(b.eq(Constant.DATASOURCE_ID, datasourceId.toString()).build());
			conditions.add(b.eq(DocumentMetadataConstant.VECTOR_TYPE, DocumentMetadataConstant.COLUMN).build());
			conditions.add(b.in(DocumentMetadataConstant.TABLE_NAME, batch.toArray()).build());
			// topK = 本批表数 × 每表最大列数：刚好覆盖这一批的全部列
			columnDocuments.addAll(vectorStoreService.getDocumentsOnlyByFilter(
					DynamicFilterService.combineWithAnd(conditions), batch.size() * maxColumnsPerTable));
		}
		return columnDocuments;
	}

	private List<String> sortedTableNames(List<Document> tableDocuments) {
		// TreeSet 同时完成去重和排序：列查询的分批边界、建图的遍历顺序都因此固定
		Set<String> names = new TreeSet<>();
		for (Document document : tableDocuments) {
			Object name = document.getMetadata().get(DocumentMetadataConstant.NAME);
			if (name != null && StringUtils.hasText(String.valueOf(name))) {
				names.add(String.valueOf(name));
			}
		}
		return new ArrayList<>(names);
	}

	/**
	 * 带加载时间的图，TTL 到点后重新加载。
	 */
	private static final class CachedGraph {

		private final SchemaGraph graph;

		private final long loadedAt;

		private CachedGraph(SchemaGraph graph) {
			this.graph = graph;
			this.loadedAt = System.currentTimeMillis();
		}

		private SchemaGraph getGraph() {
			return graph;
		}

		private boolean isFresh(long ttlMillis) {
			// TTL 配成 0 或负数表示不缓存：永远视为过期，每次调用都重新建图（测试用）
			return ttlMillis > 0 && System.currentTimeMillis() - loadedAt < ttlMillis;
		}

	}

}
