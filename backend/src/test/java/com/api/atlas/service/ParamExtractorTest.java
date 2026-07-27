package com.api.atlas.service;

import com.api.atlas.model.ParamDef;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class ParamExtractorTest {

    private final ParamExtractor paramExtractor = new ParamExtractor();

    @Test
    @DisplayName("null 输入 - 返回空列表")
    void extract_NullInput_ReturnsEmptyList() {
        assertThat(paramExtractor.extract(null)).isEmpty();
    }

    @Test
    @DisplayName("空字符串 - 返回空列表")
    void extract_EmptyInput_ReturnsEmptyList() {
        assertThat(paramExtractor.extract("")).isEmpty();
    }

    @Test
    @DisplayName("无占位符 - 返回空列表")
    void extract_NoPlaceholders_ReturnsEmptyList() {
        assertThat(paramExtractor.extract("SELECT * FROM users")).isEmpty();
    }

    @Test
    @DisplayName("单个占位符 - 返回一个 ParamDef")
    void extract_SinglePlaceholder_ReturnsOneParamDef() {
        List<ParamDef> params = paramExtractor.extract("SELECT * FROM users WHERE id = ${userId}");
        assertThat(params).hasSize(1);
        assertThat(params.get(0).getName()).isEqualTo("userId");
        assertThat(params.get(0).getJavaType()).isEqualTo("String");
        assertThat(params.get(0).getSortOrder()).isZero();
    }

    @Test
    @DisplayName("多个占位符 - 按顺序返回 ParamDef 列表")
    void extract_MultiplePlaceholders_ReturnsOrderedParamDefs() {
        List<ParamDef> params = paramExtractor.extract(
                "SELECT * FROM users WHERE name = ${userName} AND age = ${userAge}"
        );
        assertThat(params).hasSize(2);
        assertThat(params.get(0).getName()).isEqualTo("userName");
        assertThat(params.get(0).getSortOrder()).isZero();
        assertThat(params.get(1).getName()).isEqualTo("userAge");
        assertThat(params.get(1).getSortOrder()).isOne();
    }

    @Test
    @DisplayName("重复占位符 - 返回去重后的列表（保留首次出现顺序）")
    void extract_DuplicatePlaceholders_ReturnsUniqueOrdered() {
        List<ParamDef> params = paramExtractor.extract(
                "SELECT * FROM users WHERE id = ${userId} OR name = ${userName} OR parent_id = ${userId}"
        );
        assertThat(params).hasSize(2);
        assertThat(params.get(0).getName()).isEqualTo("userId");
        assertThat(params.get(1).getName()).isEqualTo("userName");
    }

    @Test
    @DisplayName("占位符紧邻文本 - 正确提取参数名")
    void extract_PlaceholderAdjacentToText_ExtractsCorrectly() {
        List<ParamDef> params = paramExtractor.extract(
                "INSERT INTO logs VALUES(${logId},${logType})"
        );
        assertThat(params).hasSize(2);
        assertThat(params.get(0).getName()).isEqualTo("logId");
        assertThat(params.get(1).getName()).isEqualTo("logType");
    }

    @Test
    @DisplayName("混合 ${} 和普通文本 - 只提取 ${} 占位符")
    void extract_MixedContent_ExtractsOnlyDollarBraces() {
        List<ParamDef> params = paramExtractor.extract(
                "SELECT * FROM t WHERE a = #{jdbcParam} AND b = ${myParam}"
        );
        assertThat(params).hasSize(1);
        assertThat(params.get(0).getName()).isEqualTo("myParam");
    }

    @Test
    @DisplayName("IBATIS 动态 SQL - 提取所有 ${} 占位符")
    void extract_IbatisDynamicSql_ExtractsAllPlaceholders() {
        List<ParamDef> params = paramExtractor.extract(
                "<select>SELECT * FROM users WHERE 1=1" +
                "<if test='${name} != null'>AND name = ${name}</if>" +
                "<if test='${status} != null'>AND status = ${status}</if></select>"
        );
        assertThat(params).hasSize(2);
        assertThat(params.get(0).getName()).isEqualTo("name");
        assertThat(params.get(1).getName()).isEqualTo("status");
    }
}
