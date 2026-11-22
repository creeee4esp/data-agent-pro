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
package com.alibaba.cloud.ai.dataagent.service.schemalink.index;

import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表家族分组：把"一张正表和它的复制品"归为一个家族。
 * <p>
 * 规则（名字找候选，列来判定，两者都满足才归入）：
 * <ul>
 * <li>分表：表名 = 正表名 + 末尾一段日期或序号（如 {@code trade_order_202503}），且列包含度 ≥ 0.9；
 * 没有正表时，同一模板的分表自成一个家族，由最新的一张做代表；</li>
 * <li>副本：反复去掉副本标记（{@code tmp_}、{@code test_}、{@code u_某人_} 前缀，{@code _bak}
 * 等后缀，以及其后的日期或序号）后， 基础名等于正表名，或正表名以 {@code _基础名} 结尾；副本至少 3 列，且列包含度 ≥ 0.6；</li>
 * <li>其余的表自成一个家族。</li>
 * </ul>
 * 列包含度 = 本表的列中，能在正表里找到对应列的比例；列名相同或互为缩写（如 {@code pay_amt} ≈ {@code pay_amount}）都算对应。
 */
public final class TableFamilyClassifier {

	/** 分表与正表（或代表分表）的最低列包含度。真实分表的列完全相同，留一点余量给列名缩写。 */
	static final double SHARD_CONTAINMENT = 0.9;

	/** 副本与正表的最低列包含度。副本常常只取正表的一部分列。 */
	static final double COPY_CONTAINMENT = 0.6;

	/** 列数低于此值的副本，只归入基础名完全相等的正表：列太少时，列包含度在哪张表上都可能很高，判定没有意义。 */
	static final int MIN_COPY_COLUMNS = 3;

	private static final Pattern COPY_PREFIX = Pattern.compile("^(tmp|test|u_[a-z]+)_");

	private static final Pattern COPY_SUFFIX = Pattern.compile("_(bak|old|tmp|his|v\\d+|analysis|check|export)$");

	private static final Pattern TRAILING_NUMBER = Pattern.compile("_\\d+$");

	/** 分表后缀：末尾一段日期（yyyyMMdd / yyyyMM）或纯数字序号。 */
	private static final Pattern SHARD_SUFFIX = Pattern.compile("^(.+)_(\\d+)$");

	private TableFamilyClassifier() {
	}

	/**
	 * 计算每张表所属的家族。
	 * @param tables 数据源的全部表，列信息需已补全
	 * @return 表名 → 家族 id（家族代表表的表名）；不属于任何家族的表映射到自己
	 */
	public static Map<String, String> classify(List<TableInfoBO> tables) {
		// TreeMap：按表名排序遍历，保证结果确定
		Map<String, List<String>> columns = new TreeMap<>();
		for (TableInfoBO table : tables) {
			columns.put(table.getName(), columnNames(table));
		}

		Map<String, String> family = new HashMap<>();
		Map<String, String> copyBases = new TreeMap<>();
		// 分表候选：分表模板（去掉末尾数字后的名字）→ 成员
		Map<String, List<String>> shardGroups = new TreeMap<>();
		List<String> anchors = new ArrayList<>();

		for (String name : columns.keySet()) {
			family.put(name, name);
			String copyBase = copyBase(name);
			if (copyBase != null) {
				copyBases.put(name, copyBase);
				continue;
			}
			Matcher m = SHARD_SUFFIX.matcher(name);
			if (m.matches()) {
				shardGroups.computeIfAbsent(m.group(1), k -> new ArrayList<>()).add(name);
			}
			else {
				anchors.add(name);
			}
		}

		assignShards(shardGroups, columns, family);
		assignCopies(copyBases, anchors, columns, family);
		return family;
	}

	private static void assignShards(Map<String, List<String>> shardGroups, Map<String, List<String>> columns,
			Map<String, String> family) {
		for (Map.Entry<String, List<String>> group : shardGroups.entrySet()) {
			String base = group.getKey();
			List<String> members = group.getValue();
			// 有正表：归入正表；没有正表：至少两张分表才成组，由最新的一张做代表
			String representative = columns.containsKey(base) ? base : latest(members);
			if (!columns.containsKey(base) && members.size() < 2) {
				continue;
			}
			for (String member : members) {
				if (!member.equals(representative)
						&& containment(columns.get(member), columns.get(representative)) >= SHARD_CONTAINMENT) {
					family.put(member, representative);
				}
			}
		}
	}

	private static void assignCopies(Map<String, String> copyBases, List<String> anchors,
			Map<String, List<String>> columns, Map<String, String> family) {
		for (Map.Entry<String, String> copy : copyBases.entrySet()) {
			List<String> copyColumns = columns.get(copy.getKey());
			boolean exactOnly = copyColumns.size() < MIN_COPY_COLUMNS;
			String base = copy.getValue();
			String best = null;
			double bestScore = COPY_CONTAINMENT;
			for (String anchor : anchors) {
				boolean exact = anchor.equals(base);
				if (!exact && (exactOnly || !anchor.endsWith("_" + base))) {
					continue;
				}
				double score = containment(copyColumns, columns.get(anchor));
				// 包含度更高者优先；相同时基础名完全相等者优先（anchors 已按名字排序，结果确定）
				if (score > bestScore || (score == bestScore && (best == null || exact))) {
					best = anchor;
					bestScore = score;
				}
			}
			if (best != null) {
				family.put(copy.getKey(), best);
			}
		}
	}

	/**
	 * 反复去掉副本标记和末尾数字，得到基础名。
	 * @return 基础名；表名里没有任何副本标记时返回 {@code null}（不是副本）
	 */
	static String copyBase(String name) {
		String s = name;
		boolean marked = false;
		while (true) {
			String t = TRAILING_NUMBER.matcher(s).replaceFirst("");
			String u = COPY_SUFFIX.matcher(t).replaceFirst("");
			String v = COPY_PREFIX.matcher(u).replaceFirst("");
			marked |= !u.equals(t) || !v.equals(u);
			if (v.equals(s) || v.isEmpty()) {
				return marked ? s : null;
			}
			s = v;
		}
	}

	/**
	 * 列包含度：{@code source} 的列中，能在 {@code target} 里找到对应列的比例。
	 */
	static double containment(List<String> source, List<String> target) {
		if (source.isEmpty()) {
			return 0;
		}
		int hit = 0;
		for (String s : source) {
			for (String t : target) {
				if (sameColumn(s, t)) {
					hit++;
					break;
				}
			}
		}
		return (double) hit / source.size();
	}

	/** 列名相同，或按 {@code _} 切分后段数相同、每段相同或互为缩写。 */
	static boolean sameColumn(String a, String b) {
		if (a.equals(b)) {
			return true;
		}
		String[] x = a.split("_");
		String[] y = b.split("_");
		if (x.length != y.length) {
			return false;
		}
		for (int i = 0; i < x.length; i++) {
			if (!x[i].equals(y[i]) && !isAbbreviation(x[i], y[i]) && !isAbbreviation(y[i], x[i])) {
				return false;
			}
		}
		return true;
	}

	/** {@code shortWord} 是否是 {@code longWord} 的缩写：更短、首字母相同、字母按顺序出现（amt ≈ amount）。 */
	static boolean isAbbreviation(String shortWord, String longWord) {
		if (shortWord.isEmpty() || shortWord.length() >= longWord.length()
				|| shortWord.charAt(0) != longWord.charAt(0)) {
			return false;
		}
		int j = 0;
		for (int i = 0; i < longWord.length() && j < shortWord.length(); i++) {
			if (longWord.charAt(i) == shortWord.charAt(j)) {
				j++;
			}
		}
		return j == shortWord.length();
	}

	/** 末尾数字最大的一张（日期即最新）。 */
	private static String latest(List<String> members) {
		String latest = null;
		long max = -1;
		for (String member : members) {
			String digits = member.substring(member.lastIndexOf('_') + 1);
			// 超长数字串按 0 处理，避免溢出；这类表名实际不会出现
			long value = digits.length() > 18 ? 0 : Long.parseLong(digits);
			if (value > max) {
				max = value;
				latest = member;
			}
		}
		return latest;
	}

	private static List<String> columnNames(TableInfoBO table) {
		List<String> names = new ArrayList<>();
		if (table.getColumns() != null) {
			for (ColumnInfoBO column : table.getColumns()) {
				names.add(column.getName().toLowerCase(Locale.ROOT));
			}
		}
		return names;
	}

}
