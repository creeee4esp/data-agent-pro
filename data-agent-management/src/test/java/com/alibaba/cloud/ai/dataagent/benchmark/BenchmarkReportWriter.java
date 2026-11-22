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
package com.alibaba.cloud.ai.dataagent.benchmark;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 把评测结果写成两份文件：
 * <ul>
 * <li>{@code report.json}：完整数据（运行信息、汇总指标、逐题结果），供程序比较、画图；</li>
 * <li>{@code summary.md}：给人看的汇总表格，以及没有全命中的题目清单。</li>
 * </ul>
 * 目录名为 {@code 时间-评测名}，例如 {@code 20261015-153000-legacy-baseline}，多次评测互不覆盖。
 */
public final class BenchmarkReportWriter {

	private static final DateTimeFormatter DIR_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	/** 失败题目清单里展示召回结果的前几名。 */
	private static final int SHOW_TOP = 10;

	private BenchmarkReportWriter() {
	}

	/**
	 * 写出报告。
	 * @param root 报告根目录，例如 target/schema-benchmark
	 * @return 本次报告所在目录
	 */
	public static Path write(Path root, BenchmarkRunInfo info, MetricsSummary summary, List<QuestionResult> results)
			throws IOException {
		Path dir = root.resolve(DIR_TIME.format(info.getStartedAt()) + "-" + info.getLabel());
		Files.createDirectories(dir);

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("run", info);
		report.put("summary", summary);
		report.put("questions", toQuestionRows(results, info.getKs()));
		ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
			.enable(SerializationFeature.INDENT_OUTPUT)
			.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
		mapper.writeValue(dir.resolve("report.json").toFile(), report);

		Files.write(dir.resolve("summary.md"), renderMarkdown(info, summary, results).getBytes(StandardCharsets.UTF_8));
		return dir;
	}

	/** 逐题结果：在 QuestionResult 的基础上，加上每个 K 的召回率和是否全命中，方便直接查看 JSON。 */
	static List<Map<String, Object>> toQuestionRows(List<QuestionResult> results, List<Integer> ks) {
		List<Map<String, Object>> rows = new ArrayList<>();
		for (QuestionResult r : results) {
			Map<Integer, Double> recall = new TreeMap<>();
			Map<Integer, Boolean> fullHit = new TreeMap<>();
			for (int k : ks) {
				recall.put(k, r.tableRecallAt(k));
				fullHit.put(k, r.fullHitAt(k));
			}
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("id", r.getQuestionId());
			row.put("difficulty", r.getDifficulty());
			row.put("tags", r.getTags());
			row.put("elapsedMillis", r.getElapsedMillis());
			row.put("tableRecall", recall);
			row.put("fullHit", fullHit);
			row.put("acceptableTableSets", r.getAcceptableTableSets());
			row.put("rankedTables", r.getRankedTables());
			rows.add(row);
		}
		return rows;
	}

	/** 生成 summary.md 的内容。纯函数，便于单元测试。 */
	static String renderMarkdown(BenchmarkRunInfo info, MetricsSummary summary, List<QuestionResult> results) {
		StringBuilder md = new StringBuilder();
		md.append("# Schema 召回评测：").append(info.getLabel()).append("\n\n");

		md.append("| 项 | 值 |\n|---|---|\n");
		md.append("| 实现 | ").append(info.getLinkerName()).append(" |\n");
		md.append("| 时间 | ").append(info.getStartedAt()).append(" |\n");
		md.append("| 数据集 | ").append(info.getDataset()).append("（").append(info.getTableCount()).append(" 张表） |\n");
		md.append("| 题集 | ")
			.append(info.getQuestionsFile())
			.append("（")
			.append(info.getQuestionCount())
			.append(" 题） |\n");
		md.append("| Embedding | ").append(info.getEmbeddingModel()).append(" |\n");
		md.append("| 表召回参数 | topK=")
			.append(info.getTableTopK())
			.append("，阈值=")
			.append(info.getTableSimilarityThreshold())
			.append(" |\n");
		md.append("| 初始化 | ")
			.append(info.getInitMillis())
			.append(" ms，模型调用 ")
			.append(info.getInitEmbeddingCalls())
			.append(" 次 |\n");
		md.append("| 召回阶段模型调用 | ").append(info.getQueryEmbeddingCalls()).append(" 次 |\n");
		md.append("| 平均输出表数 | ").append(String.format("%.1f", info.getAvgTables())).append(" |\n");
		if (info.getChatModel() != null) {
			md.append("| 对话模型 | ")
				.append(info.getChatModel())
				.append("，调用 ")
				.append(info.getChatCalls())
				.append(" 次（未命中缓存 ")
				.append(info.getChatMisses())
				.append("），token 输入 ")
				.append(info.getPromptTokens())
				.append(" / 输出 ")
				.append(info.getCompletionTokens())
				.append(" |\n");
		}
		md.append("| 可复现性 | ").append(describeDeterminism(info.getDeterministic())).append(" |\n\n");

		md.append("## 整体\n\n");
		appendGroupTable(md, info.getKs(), "全部", summary.getOverall(), true);

		md.append("\n## 按题型\n\n");
		appendGroups(md, info.getKs(), summary.getByTag());

		md.append("\n## 按难度\n\n");
		appendGroups(md, info.getKs(), summary.getByDifficulty());

		int maxK = info.getKs().get(info.getKs().size() - 1);
		md.append("\n## 未全命中的题（K=").append(maxK).append("）\n\n");
		boolean any = false;
		for (QuestionResult r : results) {
			if (!r.fullHitAt(maxK)) {
				any = true;
				List<String> top = r.getRankedTables().subList(0, Math.min(SHOW_TOP, r.getRankedTables().size()));
				md.append("- **")
					.append(r.getQuestionId())
					.append("** 召回率 ")
					.append(pct(r.tableRecallAt(maxK)))
					.append("；正确答案 ")
					.append(r.getAcceptableTableSets())
					.append("；召回前 ")
					.append(top.size())
					.append(" 名 ")
					.append(top)
					.append("\n");
			}
		}
		if (!any) {
			md.append("无\n");
		}
		return md.toString();
	}

	private static void appendGroups(StringBuilder md, List<Integer> ks,
			Map<String, MetricsSummary.GroupMetrics> groups) {
		boolean header = true;
		for (Map.Entry<String, MetricsSummary.GroupMetrics> e : groups.entrySet()) {
			appendGroupTable(md, ks, e.getKey(), e.getValue(), header);
			header = false;
		}
	}

	/** 一行指标：分组名 | 题数 | 各 K 的全命中率 | 各 K 的召回率 | P50 | P95。header=true 时先输出表头。 */
	private static void appendGroupTable(StringBuilder md, List<Integer> ks, String name, MetricsSummary.GroupMetrics g,
			boolean header) {
		if (header) {
			md.append("| 分组 | 题数 |");
			for (int k : ks) {
				md.append(" 全命中@").append(k).append(" |");
			}
			for (int k : ks) {
				md.append(" 召回率@").append(k).append(" |");
			}
			md.append(" P50 ms | P95 ms |\n|---|---|");
			for (int i = 0; i < ks.size() * 2; i++) {
				md.append("---|");
			}
			md.append("---|---|\n");
		}
		md.append("| ").append(name).append(" | ").append(g.getQuestionCount()).append(" |");
		for (int k : ks) {
			md.append(" ").append(pct(g.getFullHitRate().get(k))).append(" |");
		}
		for (int k : ks) {
			md.append(" ").append(pct(g.getTableRecall().get(k))).append(" |");
		}
		md.append(" ").append(g.getP50Millis()).append(" | ").append(g.getP95Millis()).append(" |\n");
	}

	static String describeDeterminism(Boolean deterministic) {
		if (deterministic == null) {
			return "未检查";
		}
		return deterministic ? "两次独立运行逐题结果一致" : "**两次运行结果不一致，本报告不可信**";
	}

	/** 0.8333 → "83.3%"。固定用 Locale.ROOT，避免某些语言环境把小数点写成逗号。 */
	static String pct(double value) {
		return String.format(Locale.ROOT, "%.1f%%", value * 100);
	}

}
