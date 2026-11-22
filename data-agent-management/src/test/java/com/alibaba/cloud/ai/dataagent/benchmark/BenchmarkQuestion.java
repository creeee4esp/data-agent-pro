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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
public class BenchmarkQuestion {

	/** 题号，如 m0-003。 */
	private String id;

	/** 自然语言问题。 */
	private String question;

	/** 业务口径说明，对应项目里的业务知识；可以为空字符串。 */
	private String evidence;

	/** 标准 SQL，只引用核心表。 */

	@JsonProperty("gold_sql")
	private String goldSql;

	/** 其他正确写法（例如查 ADS/DWD 层），结果与标准 SQL 一致。 */
	@JsonProperty("alt_sqls")
	private List<String> altSqls = new ArrayList<>();

	/** 人工标注的标准 SQL 所用表，加载时会与解析结果交叉校验。 */
	private List<String> tables = new ArrayList<>();

	/** 人工标注的标准 SQL 所用列，格式为 表.列，后续计算列召回率时使用。 */
	private List<String> columns = new ArrayList<>();

	/** 难度：L1～L4。 */
	private String difficulty;

	/** 题型标签，如 value_match、multi_hop。 */
	private List<String> tags = new ArrayList<>();

	/** 判分时是否比较行顺序（召回评测用不到，保留以便读取完整题集）。 */
	@JsonProperty("order_matters")
	private boolean orderMatters;

	/**
	 * 可接受的表集合：第一个来自标准 SQL，其余来自每条替代 SQL（已去重）。 召回结果覆盖其中任意一组即算"全命中"。不参与 JSON 反序列化。
	 */
	@JsonIgnore
	private List<Set<String>> acceptableTableSets = Collections.emptyList();

}
