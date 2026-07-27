package com.api.atlas.model;

import com.github.pagehelper.PageInfo;
import org.springframework.http.ResponseEntity;

import java.util.List;

public class R<T> {

    private int code;
    private T data;
    private String message;
    private Integer pageNum;
    private Integer pageSize;
    private Long total;

    public R() {
    }

    public R(int code, T data, String message) {
        this.code = code;
        this.data = data;
        this.message = message;
    }

    public static <T> R<T> ok(T data) {
        return new R<>(200, data, "success");
    }

    @SuppressWarnings("unchecked")
    public static <T> R<List<T>> ok(List<T> data, PageInfo<?> page) {
        R<List<T>> r = new R<>(200, data, "success");
        r.setPageNum(page.getPageNum());
        r.setPageSize(page.getPageSize());
        r.setTotal(page.getTotal());
        return r;
    }

    public static <T> R<T> created(T data) {
        return new R<>(201, data, "created");
    }

    public static ResponseEntity<Void> deleted() {
        return ResponseEntity.noContent().build();
    }

    public static <T> R<T> error(int code, String message) {
        return new R<>(code, null, message);
    }

    public static <T> R<T> error(int code, String message, T data) {
        return new R<>(code, data, message);
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }
}
