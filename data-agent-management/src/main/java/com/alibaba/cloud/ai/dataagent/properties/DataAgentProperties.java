/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX)
public class DataAgentProperties {

	/**
	 * spring.ai.alibaba.data-agent.embedding-batch.encoding-type=cl100k_base
	 * spring.ai.alibaba.data-agent.embedding-batch.max-token-count=2000
	 * spring.ai.alibaba.data-agent.embedding-batch.reserve-percentage=0.2
	 * spring.ai.alibaba.data-agent.embedding-batch.max-text-count=10
	 */
	private EmbeddingBatch embeddingBatch = new EmbeddingBatch();

	private VectorStoreProperties vectorStore = new VectorStoreProperties();

	private ReportTemplate reportTemplate = new ReportTemplate();

	/**
	 * sql执行失败重试次数
	 */
	private int maxSqlRetryCount = 10;

	/**
	 * sql优化最多次数
	 */
	private int maxSqlOptimizeCount = 10;

	/**
	 * sql优化分数阈值
	 */
	private double sqlScoreThreshold = 0.95;

	private TextSplitter textSplitter = new TextSplitter();

	/** Schema 召回（schema linking）相关配置，前缀 spring.ai.alibaba.data-agent.schema-linking。 */
	private SchemaLinking schemaLinking = new SchemaLinking();

	/**
	 * 智能体知识相关配置, 前缀spring.ai.alibaba.data-agent.agent-knowledge
	 */
	private AgentKnowledge agentKnowledge = new AgentKnowledge();

	@Getter
	@Setter
	public static class AgentKnowledge {

		private ParentChild parentChild = new ParentChild();

		private Rerank rerank = new Rerank();

	}

	/**
	 * 智能体知识召回后的精排（reranker 服务，接口 POST {query, texts} -> [{index, score}]）。默认关闭
	 */
	@Getter
	@Setter
	public static class Rerank {

		private boolean enabled = false;

		private String url = "http://127.0.0.1:8085/rerank";

		/** 向量召回后取前几个父块（QA / FAQ 各算一个）交给 reranker 打分。 */
		private int candidates = 8;

		/** 分数低于它的丢掉；全部低于时返回空。 */
		private double minScore = 0.5;

		/** 精排后最多保留几个。 */
		private int maxResults = 5;

		private int timeoutMs = 30000;

	}

	/**
	 * 父子分块(splitterType = parent-child); 入库时的切分参数和召回时的展开参数
	 */
	@Getter
	@Setter
	public static class ParentChild {

		/** 父块（章节）最长字符数，超过时按段落拆成多个父块。 */
		private int parentMaxChars = 1500;

		/** 父块最短字符数，更短的章节并入前一个父块。 */
		private int parentMinChars = 100;

		/** 子块目标字符数（不含标题路径前缀）。200 与 400 的召回相当，400 的向量少约 30%（K6 评测）。 */
		private int childChars = 400;

		/** 召回时检索的子块数。 */
		private int childTopK = 30;

		/** 召回后最多交给大模型的父块数（QA / FAQ 各算一个），与 default-topk-limit 相同。 */
		private int maxParents = 8;

		/** 交给大模型的父块总字符数上限；第一个父块总会保留。 */
		private int contextBudgetChars = 6000;

	}

	/**
	 * 最多保留的对话轮数
	 */
	private int maxturnhistory = 5;

	/**
	 * 单次规划最大长度限制
	 */
	private int maxplanlength = 2000;

	// 每张表的最大预估列数
	private int maxColumnsPerTable = 50;

	/**
	 * 是否启用SQL执行结果图表判断，默认启用
	 */
	private boolean enableSqlResultChart = true;

	/**
	 * 执行SQL结果图表化超时时间，默认15000ms。结构化输出可能触发 Spring AI 自动修复重试，过短的超时会取消仍在运行的模型请求。
	 */
	private Long enrichSqlResultTimeout = 15000L;

	@Getter
	@Setter
	public static class SchemaLinking {

		/**
		 * agentic 补全阶段启用的工具（默认全部启用），用于消融实验，例如只启用
		 * retrieve_schema,describe_tables,expand_family。不认识的名字会被忽略。
		 */
		private List<String> completionTools = new ArrayList<>(Arrays.asList("retrieve_schema", "describe_tables",
				"expand_family", "explore_schema", "verify_schema"));

		/**
		 * 表关系图配置
		 */
		private JoinGraph joinGraph = new JoinGraph();

		/**
		 * 表关系图的建图和缓存参数。置信度暂不暴露，先写常量
		 */
		@Getter
		@Setter
		public static class JoinGraph {

			/**
			 * 图缓存有效期(秒)。schema或逻辑关系变化时应主动失效，TTL只做兜底
			 */
			private int cacheTtlSeconds = 600;

			/**
			 * 查询列文档时每批的表数。一批最多 批大小 × max-columns-per-table 条文档
			 */
			private int columnFetchBatchSize = 100;

			/**
			 * 每个外键列最多保留几个命名推断候选。设为 0 关闭命名推断
			 */
			private int maxNamingCandidates = 3;

			/**
			 * 是否在精选表之后做外键多跳补全（补中间表 + JOIN 提示）。默认关闭，链路行为与原来一致
			 */
			private boolean enabled = false;

			/**
			 * 补全时最多补进来几张中间表
			 */
			private int maxAddedTables = 5;

		}

	}

	@Getter
	@Setter
	public static class ReportTemplate {

		// Marked.js (Markdown 解析器) 南方科技大学开源软件镜像站
		private String markedUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/marked/12.0.0/marked.min.js";

		// ECharts (图表库) 南方科技大学开源软件镜像站
		private String echartsUrl = "https://mirrors.sustech.edu.cn/cdnjs/ajax/libs/echarts/5.5.0/echarts.min.js";

	}

	@Getter
	@Setter
	public static class TextSplitter {

		/**
		 * 默认分块大小，基于token数量 默认值：1000
		 */
		private int chunkSize = 1000;

		/**
		 * TokenTextSplitter 策略配置
		 */
		private TokenTextSplitterConfig token = new TokenTextSplitterConfig();

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		private RecursiveTextSplitterConfig recursive = new RecursiveTextSplitterConfig();

		/**
		 * SentenceTextSplitter 策略配置
		 */
		private SentenceTextSplitterConfig sentence = new SentenceTextSplitterConfig();

		/**
		 * SemanticTextSplitter 策略配置
		 */
		private SemanticTextSplitterConfig semantic = new SemanticTextSplitterConfig();

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		private ParagraphTextSplitterConfig paragraph = new ParagraphTextSplitterConfig();

		/**
		 * TokenTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class TokenTextSplitterConfig {

			/**
			 * 最小分块字符数 默认值：400
			 */
			private int minChunkSizeChars = 400;

			/**
			 * 嵌入最小分块长度 默认值：10
			 */
			private int minChunkLengthToEmbed = 10;

			/**
			 * 最大分块数量 默认值：5000
			 */
			private int maxNumChunks = 5000;

			/**
			 * 是否保留分隔符 默认值：true
			 */
			private boolean keepSeparator = true;

		}

		/**
		 * RecursiveCharacterTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class RecursiveTextSplitterConfig {

			/**
			 * 重叠区域字符数 默认值：200。
			 * <p>
			 * 注意：目前不生效。recursive 使用的 RecursiveCharacterTextSplitter（spring-ai-alibaba）只接受
			 * chunkSize 和分隔符，没有重叠参数。
			 */
			private int chunkOverlap = 200;

			/**
			 * 分隔符列表（如果为 null，该类内部有默认的分隔符列表）
			 */
			private String[] separators = null;

		}

		/**
		 * SentenceTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SentenceTextSplitterConfig {

			/**
			 * 句子重叠数量 默认值：1（保留前一个分块的最后1个句子）
			 */
			private int sentenceOverlap = 1;

		}

		/**
		 * SemanticTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class SemanticTextSplitterConfig {

			/**
			 * 最小分块大小 默认值：200
			 */
			private int minChunkSize = 200;

			/**
			 * 最大分块大小 默认值：1000
			 */
			private int maxChunkSize = 1000;

			/**
			 * 语义相似度阈值 默认值：0.5（0-1之间，越低越容易分块）
			 */
			private double similarityThreshold = 0.5;

		}

		/**
		 * ParagraphTextSplitter 策略配置
		 */
		@Getter
		@Setter
		public static class ParagraphTextSplitterConfig {

			/**
			 * 段落重叠字符数 默认值：200（保留前一个分块的最后200个字符，而非段落数量）
			 */
			private int paragraphOverlapChars = 200;

		}

	}

	@Getter
	@Setter
	public static class EmbeddingBatch {

		/**
		 * encodingType 默认值：cl100k_base，适用于OpenAI等模型
		 */
		private String encodingType = "cl100k_base";

		/**
		 * 每批次最大令牌数 值越小，每批次文档越少，但更安全 值越大，处理效率越高，但可能超出API限制 建议值：2000-8000，根据实际API限制调整
		 */
		private int maxTokenCount = 8000;

		/**
		 * 预留百分比 用于预留缓冲空间，避免超出限制 建议值：0.1-0.2（10%-20%）
		 */
		private double reservePercentage = 0.2;

		/**
		 * 每批次最大文本数量 适用于DashScope等有文本数量限制的API DashScope限制为10
		 */
		private int maxTextCount = 10;

	}

	@Getter
	@Setter
	public static class VectorStoreProperties {

		// 专门给召回Table 用的配置
		private int tableTopkLimit = 10;

		// 设置低尽可能保证表不会召回漏掉
		private double tableSimilarityThreshold = 0.2;

		// 全局默认配置（给 BusinessTerm, AgentKnowledge 等使用）
		/**
		 * 相似度阈值配置，用于过滤相似度分数大于等于此阈值的文档。
		 * <p>
		 * 0.55 来自知识检索评测（qwen3-embedding:0.6b）：命中率与 0.4 相同，无关查询的误召回从 80% 降到 0%。换 Embedding
		 * 模型时需重新评估。
		 */
		private double defaultSimilarityThreshold = 0.55;

		/**
		 * 查询时返回的最大文档数量
		 */
		private int defaultTopkLimit = 8;

		/**
		 * 一次删除操作中，最多删除的文档数量
		 */
		private int batchDelTopkLimit = 5000;

		/**
		 * 写入向量库时每批的文档数。ES 的一次写入是一个 bulk 请求，默认上限 100 MB；1024 维向量每个文档约 10 KB，500 个约 5 MB。
		 */
		private int writeBatchSize = 500;

		/**
		 * Expected embedding dimension for the configured persistent vector store. A
		 * value of 0 disables the check, which is useful for the development-only simple
		 * store.
		 */
		private int embeddingDimension = 0;

		/**
		 * 是否启用混合搜索
		 */
		private boolean enableHybridSearch = false;

		/**
		 * Maximum wait for each hybrid retrieval branch.
		 */
		private long hybridSearchTimeoutMs = 3000;

		/**
		 * Elasticsearch最小分数阈值，用于es执行关键词搜索时过滤相关性较低的文档
		 */
		private double elasticsearchMinScore = 0.5;

		/**
		 * 混合检索 RRF 融合的平滑常数 k。10 比论文默认的 60 好：知识检索评测 Recall@8 81.1% → 83.5%、MRR 0.59 →
		 * 0.62（5～20 之间差别不大）。
		 */
		private int rrfK = 10;

		/**
		 * SimpleVectorStore本地序列化文件地址
		 */
		private String filePath = "./vectorstore/vectorstore.json";

	}

}
