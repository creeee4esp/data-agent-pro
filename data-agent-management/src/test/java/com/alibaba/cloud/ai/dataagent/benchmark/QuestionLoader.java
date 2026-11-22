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
package com.alibaba.cloud.ai.dataagent.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * 读取 JSONL 题集，并为每道题计算"可接受表集合"。
 * <p>
 * 加载时做三类校验，任何一类失败都抛出带行号的异常，避免拿着有问题的题集跑评测：
 * <ol>
 * <li>JSON 格式错误，或出现未知字段（多半是字段名拼错）；</li>
 * <li>必填字段缺失：id、question、gold_sql、tables；</li>
 * <li>人工标注的 tables 与解析 gold_sql 得到的表不一致（标注错误）。</li>
 * </ol>
 */
public class QuestionLoader {

	/** 默认配置的 ObjectMapper：遇到未知字段会报错，能发现拼错的字段名。 */
	private static final ObjectMapper MAPPER = new ObjectMapper();

	private QuestionLoader() {
	}

	/**
	 * 加载题集文件。
	 * @param path JSONL 文件路径，UTF-8 编码，每行一道题，空行跳过
	 * @return 按文件顺序排列的题目，每道题都已填好 acceptableTableSets
	 * @throws IOException 文件读取失败
	 * @throws IllegalArgumentException 题集内容有误，消息中带文件名和行号
	 */
	public static List<BenchmarkQuestion> load(Path path) throws IOException {
		List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
		List<BenchmarkQuestion> questions = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (!StringUtils.hasText(line))
				continue;
			int lineNo = i + 1;
			try {
				questions.add(parseLine(line));
			}
			catch (JsonProcessingException | IllegalArgumentException e) {
				throw new IllegalArgumentException(path.getFileName() + " line " + lineNo + ": " + describe(e), e);
			}
		}
		return questions;
	}

	private static BenchmarkQuestion parseLine(String line) throws IOException {
		BenchmarkQuestion q = MAPPER.readValue(line, BenchmarkQuestion.class);
		requireText(q.getId(), "id");
		requireText(q.getQuestion(), "question");
		requireText(q.getGoldSql(), "gold_sql");
		if (CollectionUtils.isEmpty(q.getTables())) {
			throw new IllegalArgumentException("tables must not be empty");
		}

		Set<String> goldTables = SqlTableExtractor.extractTables(q.getGoldSql());
		Set<String> labeled = normalizeAll(q.getTables());
		if (!labeled.equals(goldTables)) {
			throw new IllegalArgumentException(
					"question" + q.getId() + ": labeled tables " + labeled + " != tables in gold_sql" + goldTables);
		}
		// 用 LinkedHashSet<Set<String>> 去重：两条 SQL 用到同一组表时只保留一份，且保持"标准 SQL 在前"的顺序
		Set<Set<String>> acceptable = new LinkedHashSet<>();
		acceptable.add(goldTables);
		for (String alt : q.getAltSqls()) {
			acceptable.add(SqlTableExtractor.extractTables(alt));
		}
		q.setAcceptableTableSets(Collections.unmodifiableList(new ArrayList<>(acceptable)));
		return q;
	}

	private static Set<String> normalizeAll(List<String> tables) {
		Set<String> result = new LinkedHashSet<>();
		for (String t : tables) {
			result.add(t.trim().toLowerCase(Locale.ROOT));
		}
		return result;
	}

	private static void requireText(String value, String field) {
		if (!StringUtils.hasText(value)) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	/**
	 * 取异常的简短描述：Jackson 异常用 getOriginalMessage（不带冗长的位置信息），其他异常用 getMessage。
	 */
	private static String describe(Exception e) {
		if (e instanceof JsonProcessingException) {
			return ((JsonProcessingException) e).getOriginalMessage();
		}
		return e.getMessage();
	}

}
