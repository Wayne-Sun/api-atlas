package com.api.atlas.model;

public class ParamDef {
    private String name;
    private String javaType;
    private String remark;
    private int sortOrder;

    public ParamDef() {
    }

    public ParamDef(String name, String javaType, String remark, int sortOrder) {
        this.name = name;
        this.javaType = javaType;
        this.remark = remark;
        this.sortOrder = sortOrder;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getJavaType() {
        return javaType;
    }

    public void setJavaType(String javaType) {
        this.javaType = javaType;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}
