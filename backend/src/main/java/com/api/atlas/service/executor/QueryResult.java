package com.api.atlas.service.executor;

import java.util.List;
import java.util.Map;

public class QueryResult {

    private List<Map<String, Object>> rows;
    private long total;
    private int pageNum;
    private int pageSize;
    private long responseTimeMs;

    public QueryResult() {
    }

    public QueryResult(List<Map<String, Object>> rows, long total, int pageNum, int pageSize, long responseTimeMs) {
        this.rows = rows;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.responseTimeMs = responseTimeMs;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }
}
