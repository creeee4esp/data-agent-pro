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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import org.junit.jupiter.api.Test;

class ResultComparatorTest {

	private static ResultSetBO result(List<String> columns, String[]... rows) {
		List<Map<String, String>> data = new ArrayList<>();
		for (String[] row : rows) {
			Map<String, String> m = new HashMap<>();
			for (int i = 0; i < columns.size(); i++) {
				m.put(columns.get(i), row[i]);
			}
			data.add(m);
		}
		ResultSetBO r = new ResultSetBO();
		r.setColumn(columns);
		r.setData(data);
		return r;
	}

	@Test
	void ignoresColumnNamesAndRoundsNumbersToTwoDecimals() {
		ResultSetBO gold = result(Arrays.asList("gmv"), new String[] { "100.004" });
		ResultSetBO actual = result(Arrays.asList("SUM(item_amount)"), new String[] { "100" });
		assertTrue(ResultComparator.sameResult(gold, actual, false));
	}

	@Test
	void rowOrderMattersOnlyWhenRequired() {
		List<String> cols = Arrays.asList("name", "cnt");
		ResultSetBO a = result(cols, new String[] { "x", "1" }, new String[] { "y", "2" });
		ResultSetBO b = result(cols, new String[] { "y", "2" }, new String[] { "x", "1" });
		assertTrue(ResultComparator.sameResult(a, b, false));
		assertFalse(ResultComparator.sameResult(a, b, true));
	}

	@Test
	void differentValuesOrRowCountsDoNotMatch() {
		List<String> cols = Arrays.asList("cnt");
		assertFalse(ResultComparator.sameResult(result(cols, new String[] { "1" }), result(cols, new String[] { "2" }),
				false));
		assertFalse(ResultComparator.sameResult(result(cols, new String[] { "1" }),
				result(cols, new String[] { "1" }, new String[] { "1" }), false));
	}

	@Test
	void relaxedAllowsExtraColumnsAndReportsColumnPrecision() {
		ResultSetBO gold = result(Arrays.asList("name", "cnt"), new String[] { "x", "3" }, new String[] { "y", "1" });
		ResultSetBO actual = result(Arrays.asList("id", "cnt", "name", "avg"), new String[] { "10", "3", "x", "1.5" },
				new String[] { "20", "1", "y", "2.0" });
		ResultComparator.Match m = ResultComparator.compare(gold, actual, true);
		assertFalse(m.strict);
		assertTrue(m.relaxed);
		assertEquals(0.5, m.columnPrecision, 1e-9);
	}

	@Test
	void relaxedStillRequiresRowsToLineUp() {
		// 每一列的取值集合都相同，但行的组合不同：不能算对
		ResultSetBO gold = result(Arrays.asList("name", "cnt"), new String[] { "x", "1" }, new String[] { "y", "2" });
		ResultSetBO actual = result(Arrays.asList("name", "cnt", "extra"), new String[] { "x", "2", "a" },
				new String[] { "y", "1", "b" });
		assertFalse(ResultComparator.compare(gold, actual, false).relaxed);
	}

	@Test
	void approxIgnoresRowOrderAndTinyNumericDifferences() {
		List<String> cols = Arrays.asList("channel", "avg");
		ResultSetBO gold = result(cols, new String[] { "app", "212.60" }, new String[] { "web", "100.00" });
		ResultSetBO actual = result(cols, new String[] { "web", "100.00" }, new String[] { "app", "212.58" });
		ResultComparator.Match m = ResultComparator.compare(gold, actual, true);
		assertFalse(m.relaxed);
		assertTrue(m.approx);
		// 0.34% 的差异（口径不同）不算近似
		assertFalse(ResultComparator.compare(result(Arrays.asList("v"), new String[] { "2387.47" }),
				result(Arrays.asList("v"), new String[] { "2379.35" }), false).approx);
	}

	@Test
	void readOnlyGuardRejectsNonSelect() {
		assertNull(EndToEndBenchmarkIT.readOnlyViolation("SELECT 1"));
		assertTrue(EndToEndBenchmarkIT.readOnlyViolation("DELETE FROM trade_order") != null);
		assertTrue(EndToEndBenchmarkIT.readOnlyViolation("SELECT 1; SELECT 2") != null);
	}

}
