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
package com.alibaba.cloud.ai.dataagent.service.schemalink;

import lombok.Value;

/**
 * 记录这次召回是哪个实现做的、耗时多少。后面阶段会往里加"每一路召回的候选和分数"，评测器和消融分数都靠它
 */

@Value
public class LinkDiagnostics {

	String linkerName;

	long elapsedMillis;

}
