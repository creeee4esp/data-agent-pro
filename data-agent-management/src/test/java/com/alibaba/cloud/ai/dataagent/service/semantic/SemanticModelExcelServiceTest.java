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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;

import com.alibaba.cloud.ai.dataagent.dto.schema.SemanticModelImportItem;
import com.alibaba.excel.EasyExcel;
import org.junit.jupiter.api.Test;

/**
 * 真实读写 xlsx：POI 锁定为 5.4.1（Tika 需要）后，EasyExcel 3.3.4 仍能正常工作。
 */
class SemanticModelExcelServiceTest {

	@Test
	void parsesXlsxWrittenByEasyExcel() throws Exception {
		SemanticModelImportItem item = new SemanticModelImportItem();
		item.setTableName("trade_order");
		item.setColumnName("pay_time");
		item.setBusinessName("支付时间");
		item.setDataType("datetime");
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		EasyExcel.write(out, SemanticModelImportItem.class).sheet("s").doWrite(Collections.singletonList(item));

		List<SemanticModelImportItem> items = new SemanticModelExcelService()
			.parseExcel(new ByteArrayInputStream(out.toByteArray()), "model.xlsx");

		assertThat(items).hasSize(1);
		assertThat(items.get(0).getTableName()).isEqualTo("trade_order");
		assertThat(items.get(0).getColumnName()).isEqualTo("pay_time");
		assertThat(items.get(0).getBusinessName()).isEqualTo("支付时间");
	}

}
