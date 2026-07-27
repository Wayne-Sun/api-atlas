package com.api.atlas.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class ApiInterfaceCreateDTO {

    @NotBlank
    private String englishName;

    @NotBlank
    private String chineseName;

    @NotBlank
    private String method;

    @NotNull
    private Long dataSourceId;

    @NotBlank
    private String queryType;

    @NotBlank
    private String queryContent;

    private Boolean isPaginated;

    private Integer pageSize;

    public String getEnglishName() {
        return englishName;
    }

    public void setEnglishName(String englishName) {
        this.englishName = englishName;
    }

    public String getChineseName() {
        return chineseName;
    }

    public void setChineseName(String chineseName) {
        this.chineseName = chineseName;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Long getDataSourceId() {
        return dataSourceId;
    }

    public void setDataSourceId(Long dataSourceId) {
        this.dataSourceId = dataSourceId;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getQueryContent() {
        return queryContent;
    }

    public void setQueryContent(String queryContent) {
        this.queryContent = queryContent;
    }

    public Boolean getIsPaginated() {
        return isPaginated;
    }

    public void setIsPaginated(Boolean isPaginated) {
        this.isPaginated = isPaginated;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }
}
