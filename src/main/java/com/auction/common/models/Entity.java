package com.auction.common.models;
import java.io.Serializable;
public abstract class Entity implements Serializable {
    private static final long serialVersionUID = 1L; // Cần có để lưu file không bị lỗi

    protected String id; // Mã định danh duy nhất

    public Entity() {
    }

    public Entity(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
