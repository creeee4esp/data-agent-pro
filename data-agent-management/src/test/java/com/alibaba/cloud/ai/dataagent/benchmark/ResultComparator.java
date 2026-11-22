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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;

/**
 * 执行结果比较，取值规则与 tools/dataset-gen 的 validate.py 一致：只比较值、不比较列名；数值统一保留两位小数（四舍五入）；
 * {@code orderMatters=false} 时不比较行的顺序。
 * <p>
 * 两种口径：
 * <ul>
 * <li>严格：列数和列的顺序都必须相同；</li>
 * <li>宽松：允许多出来的列——标准答案的每一列都能在结果里找到一列值完全相同的列（一一对应，顺序不限），且行数相同。此时列精确率 = 标准答案列数 /
 * 结果列数，用来衡量多返回了多少无关的列。</li>
 * </ul>
 */
final class ResultComparator {

	/** 近似口径的数值相对误差上限（0.1%）。 */
	static final double RELATIVE_TOLERANCE = 0.001;

	/** 近似口径的数值绝对误差上限。 */
	static final double ABSOLUTE_TOLERANCE = 0.01;

	private ResultComparator() {
	}

	/** 比较结果：宽松口径不匹配时 {@code columnPrecision} 为 {@code null}。 */
	static final class Match {

		final boolean strict;

		final boolean relaxed;

		final Double columnPrecision;

		/** 近似：在宽松的基础上，不比较行顺序，数值允许 {@link #RELATIVE_TOLERANCE} 的相对误差或 0.01 的绝对误差。 */
		final boolean approx;

		Match(boolean strict, boolean relaxed, Double columnPrecision) {
			this(strict, relaxed, columnPrecision, relaxed);
		}

		Match(boolean strict, boolean relaxed, Double columnPrecision, boolean approx) {
			this.strict = strict;
			this.relaxed = relaxed;
			this.columnPrecision = columnPrecision;
			this.approx = approx;
		}

	}

	static boolean sameResult(ResultSetBO expected, ResultSetBO actual, boolean orderMatters) {
		return compare(expected, actual, orderMatters).strict;
	}

	static Match compare(ResultSetBO expected, ResultSetBO actual, boolean orderMatters) {
		List<List<String>> gold = rows(expected);
		List<List<String>> got = rows(actual);
		boolean strict = sortIfUnordered(gold, orderMatters).equals(sortIfUnordered(got, orderMatters));
		int goldCols = columnCount(expected);
		int gotCols = columnCount(actual);
		if (gold.size() != got.size() || gotCols < goldCols || goldCols == 0) {
			return new Match(strict, strict, strict ? 1.0 : null, strict);
		}
		boolean relaxed = strict || assign(0, new int[goldCols], new boolean[gotCols], gold, got, orderMatters, false);
		boolean approx = relaxed || assign(0, new int[goldCols], new boolean[gotCols], gold, got, false, true);
		return new Match(strict, relaxed, approx ? (double) goldCols / gotCols : null, approx);
	}

	/** 回溯找一种列映射（标准答案第 i 列 → 结果第 mapping[i] 列），使投影后的结果与标准答案相同。 */
	private static boolean assign(int i, int[] mapping, boolean[] used, List<List<String>> gold, List<List<String>> got,
			boolean orderMatters, boolean tolerant) {
		if (i == mapping.length) {
			return rowsEqual(sortIfUnordered(gold, orderMatters), sortIfUnordered(project(got, mapping), orderMatters),
					tolerant);
		}
		List<String> goldColumn = column(gold, i);
		for (int j = 0; j < used.length; j++) {
			if (!used[j] && sameValues(goldColumn, column(got, j), orderMatters, tolerant)) {
				used[j] = true;
				mapping[i] = j;
				if (assign(i + 1, mapping, used, gold, got, orderMatters, tolerant)) {
					return true;
				}
				used[j] = false;
			}
		}
		return false;
	}

	/** 剪枝：单列的取值（不考虑顺序时按多重集合）必须相同。 */
	private static boolean sameValues(List<String> a, List<String> b, boolean orderMatters, boolean tolerant) {
		if (orderMatters) {
			return valuesEqual(a, b, tolerant);
		}
		List<String> x = new ArrayList<>(a);
		List<String> y = new ArrayList<>(b);
		x.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
		y.sort(Comparator.nullsFirst(Comparator.naturalOrder()));
		return valuesEqual(x, y, tolerant);
	}

	private static boolean rowsEqual(List<List<String>> a, List<List<String>> b, boolean tolerant) {
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!valuesEqual(a.get(i), b.get(i), tolerant)) {
				return false;
			}
		}
		return true;
	}

	private static boolean valuesEqual(List<String> a, List<String> b, boolean tolerant) {
		if (!tolerant) {
			return a.equals(b);
		}
		if (a.size() != b.size()) {
			return false;
		}
		for (int i = 0; i < a.size(); i++) {
			if (!closeEnough(a.get(i), b.get(i))) {
				return false;
			}
		}
		return true;
	}

	/** 相同，或都是数值且相对误差 ≤ 0.1% 或绝对误差 ≤ 0.01。 */
	static boolean closeEnough(String a, String b) {
		if (a == null || b == null) {
			return a == b;
		}
		if (a.equals(b)) {
			return true;
		}
		try {
			double x = Double.parseDouble(a);
			double y = Double.parseDouble(b);
			double diff = Math.abs(x - y);
			return diff <= ABSOLUTE_TOLERANCE + 1e-9 || diff <= RELATIVE_TOLERANCE * Math.max(Math.abs(x), Math.abs(y));
		}
		catch (NumberFormatException e) {
			return false;
		}
	}

	private static List<List<String>> project(List<List<String>> rows, int[] mapping) {
		List<List<String>> out = new ArrayList<>();
		for (List<String> row : rows) {
			List<String> projected = new ArrayList<>();
			for (int j : mapping) {
				projected.add(row.get(j));
			}
			out.add(projected);
		}
		return out;
	}

	private static List<String> column(List<List<String>> rows, int index) {
		List<String> values = new ArrayList<>();
		for (List<String> row : rows) {
			values.add(row.get(index));
		}
		return values;
	}

	private static List<List<String>> sortIfUnordered(List<List<String>> rows, boolean orderMatters) {
		if (orderMatters) {
			return rows;
		}
		List<List<String>> sorted = new ArrayList<>(rows);
		sorted.sort(Comparator.comparing(Object::toString));
		return sorted;
	}

	private static int columnCount(ResultSetBO result) {
		return result == null || result.getColumn() == null ? 0 : result.getColumn().size();
	}

	/** 按列的顺序取值并归一化，保持原有行顺序。 */
	static List<List<String>> rows(ResultSetBO result) {
		List<List<String>> rows = new ArrayList<>();
		if (result == null || result.getData() == null) {
			return rows;
		}
		List<String> columns = result.getColumn() == null ? Collections.<String>emptyList() : result.getColumn();
		for (Map<String, String> row : result.getData()) {
			List<String> values = new ArrayList<>();
			for (String column : columns) {
				values.add(normalizeValue(row.get(column)));
			}
			rows.add(values);
		}
		return rows;
	}

	static String normalizeValue(String value) {
		if (value == null) {
			return null;
		}
		String v = value.trim();
		try {
			return new BigDecimal(v).setScale(2, RoundingMode.HALF_UP).toPlainString();
		}
		catch (NumberFormatException e) {
			return v;
		}
	}

}
