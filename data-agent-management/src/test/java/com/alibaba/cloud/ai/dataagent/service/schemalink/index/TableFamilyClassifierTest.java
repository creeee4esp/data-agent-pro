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
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TableFamilyClassifierTest {

	private static final String[] ORDER = { "order_id", "buyer_id", "order_status", "pay_amount", "order_time" };

	private static TableInfoBO table(String name, String... columns) {
		List<ColumnInfoBO> cols = new ArrayList<>();
		for (String c : columns) {
			cols.add(ColumnInfoBO.builder().name(c).build());
		}
		TableInfoBO t = new TableInfoBO();
		t.setName(name);
		t.setColumns(cols);
		return t;
	}

	private static Map<String, String> classify(TableInfoBO... tables) {
		return TableFamilyClassifier.classify(Arrays.asList(tables));
	}

	@Test
	void shardJoinsAnchorEvenWithAbbreviatedColumns() {
		Map<String, String> f = classify(table("trade_order", ORDER),
				table("trade_order_202503", "ord_id", "buyer_id", "order_status", "pay_amt", "order_time"),
				table("trade_order_20250315", ORDER));
		assertEquals("trade_order", f.get("trade_order"));
		assertEquals("trade_order", f.get("trade_order_202503"));
		assertEquals("trade_order", f.get("trade_order_20250315"));
	}

	@Test
	void shardsWithoutAnchorUseLatestAsRepresentative() {
		Map<String, String> f = classify(table("log_visit_20250101", "visit_id", "user_id", "page"),
				table("log_visit_20251231", "visit_id", "user_id", "page"),
				table("log_visit_20250630", "visit_id", "user_id", "page"));
		assertEquals("log_visit_20251231", f.get("log_visit_20250101"));
		assertEquals("log_visit_20251231", f.get("log_visit_20250630"));
		assertEquals("log_visit_20251231", f.get("log_visit_20251231"));
	}

	@Test
	void singleNumberedTableWithoutAnchorStaysAlone() {
		Map<String, String> f = classify(table("report_2025", "a", "b", "c"));
		assertEquals("report_2025", f.get("report_2025"));
	}

	@Test
	void shardWithDifferentColumnsStaysAlone() {
		Map<String, String> f = classify(table("trade_order", ORDER),
				table("trade_order_202503", "order_id", "buyer_id", "x", "y", "z"));
		assertEquals("trade_order_202503", f.get("trade_order_202503"));
	}

	@Test
	void datedBackupIsCopyNotShard() {
		Map<String, String> f = classify(table("trade_order", ORDER),
				table("trade_order_bak_20250315", "order_id", "buyer_id", "order_status"));
		assertEquals("trade_order", f.get("trade_order_bak_20250315"));
	}

	@Test
	void personalCopyPicksCandidateWithHighestContainment() {
		Map<String, String> f = classify(table("trade_order", ORDER),
				table("pay_order", "pay_id", "order_id", "pay_channel", "pay_time"),
				table("u_lisi_order_analysis", "buyer_id", "order_status", "pay_amt"));
		assertEquals("trade_order", f.get("u_lisi_order_analysis"));
	}

	@Test
	void copyBelowContainmentThresholdStaysAlone() {
		Map<String, String> f = classify(table("trade_order", ORDER),
				table("tmp_order_0412", "order_id", "foo", "bar", "baz"));
		assertEquals("tmp_order_0412", f.get("tmp_order_0412"));
	}

	@Test
	void smallCopyOnlyJoinsExactBase() {
		Map<String, String> f = classify(table("dim_region", "province", "region"),
				table("dim_region_bak_20251027", "province", "region"),
				table("dws_trade_brand_order_1m", "stat_date", "brand_name", "order_cnt"),
				table("tmp_order_1m_1216", "stat_date", "brand_name"));
		assertEquals("dim_region", f.get("dim_region_bak_20251027"));
		assertEquals("tmp_order_1m_1216", f.get("tmp_order_1m_1216"));
	}

	@Test
	void distinctTablesWithSimilarNamesAreNotMerged() {
		String[] cols = { "stat_date", "category1_id", "order_cnt" };
		Map<String, String> f = classify(table("dws_trade_cate1_order_1d", cols),
				table("dws_trade_cate1_order_1w", cols), table("dws_trade_cate2_order_1d", cols),
				table("trade_order", ORDER), table("trade_order_item", "id", "order_id", "sku_id"));
		f.forEach((name, family) -> assertEquals(name, family));
	}

	@Test
	void copyBaseStripsMarkersAndRejectsPlainShards() {
		assertEquals("trade_order", TableFamilyClassifier.copyBase("trade_order_bak_20250315"));
		assertEquals("order_1d", TableFamilyClassifier.copyBase("u_lisi_order_1d_export"));
		assertEquals("order", TableFamilyClassifier.copyBase("tmp_order_0412"));
		assertEquals("dws_trade_cate1_order_1d",
				TableFamilyClassifier.copyBase("dws_trade_cate1_order_1d_bak_20250427"));
		assertNull(TableFamilyClassifier.copyBase("trade_order_202503"));
		assertNull(TableFamilyClassifier.copyBase("trade_order"));
	}

	@Test
	void sameColumnComparesSegmentBySegment() {
		assertTrue(TableFamilyClassifier.sameColumn("ord_id", "order_id"));
		assertTrue(TableFamilyClassifier.sameColumn("receiver_prov", "receiver_province"));
		assertTrue(TableFamilyClassifier.sameColumn("pay_tm", "pay_time"));
		assertFalse(TableFamilyClassifier.sameColumn("id", "item_id"));
		assertFalse(TableFamilyClassifier.sameColumn("order_1d", "order_1w"));
	}

}
