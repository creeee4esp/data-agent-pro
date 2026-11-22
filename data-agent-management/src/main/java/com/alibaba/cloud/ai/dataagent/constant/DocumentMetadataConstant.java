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
package com.alibaba.cloud.ai.dataagent.constant;

// Document中metadata中存储的key
public final class DocumentMetadataConstant {

	private DocumentMetadataConstant() {

	}

	// 列文档所属表是否为家族代表表。列检索只检索代表表的列
	public static final String FAMILY_REPRESENTATIVE = "familyRepresentative";

	// 表家族 id（家族代表表的表名），写在表文档和列文档上
	public static final String FAMILY_ID = "familyId";

	// column
	public static final String COLUMN = "column";

	// table
	public static final String TABLE = "table";

	// name
	public static final String NAME = "name";

	// tableName
	public static final String TABLE_NAME = "tableName";

	// vectorType
	public static final String VECTOR_TYPE = "vectorType";

	// knowledgeId
	public static final String DB_AGENT_KNOWLEDGE_ID = "agentKnowledgeId";

	// FAQ/DOCUMENT/QA
	public static final String CONCRETE_AGENT_KNOWLEDGE_TYPE = "concreteAgentKnowledgeType";

	// 智能体的知识
	public static final String AGENT_KNOWLEDGE = "agentKnowledge";

	// businessTerm
	public static final String BUSINESS_TERM = "businessTerm";

	// 知识是否参与召回（Boolean）。检索按 recall == true 过滤；加字段之前写入的旧文档由启动时的同步补上
	public static final String RECALL = "recall";

	// businessTermId
	public static final String DB_BUSINESS_TERM_ID = "businessTermId";

	// 父子分块: 子块所属父块的 id（<agentKnowledgeId>_<parentSeq>），召回时按它分组、拼回父块
	public static final String PARENT_ID = "parentId";

	// 父子分块: 父块在文档里的序号(从0开始)
	public static final String PARENT_SEQ = "parentSeq";

	// 父子分块: 子块在父块里的序号（从 0 开始），拼回父块时按它排序
	public static final String CHILD_SEQ = "childSeq";

	// 精排分数（reranker 打分，只在召回结果里出现，不入库）
	public static final String RERANK_SCORE = "rerankScore";

	// 父子分块: 章节路径(文档标题>章节标题)
	public static final String SECTION = "section";

}
