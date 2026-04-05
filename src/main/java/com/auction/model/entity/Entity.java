package com.auction.model.entity;

import java.io.Serializable;
import java.time.LocalDateTime; //lớp đại diện cho một mốc thời gian vd: 22:47 04/04/2026

public abstract class Entity implements Serializable {
    private String id;
    private LocalDateTime createdAt;

    public Entity (String id){
        this.id = id;
        this.createdAt = LocalDateTime.now(); // là phương thức tĩnh giúp lấy ra thòi gian tạo đối tượng.
        // một vài thao tác sẽ dùng đến sau này như làm logic cho auction ( phiên đấu giá ) ,
        // chúng ta phải thường xuyên so sánh thời gian .
    }
    // Lấy id ra
    public String getId() {return id; }
    public LocalDateTime getCreatedAt() {return createdAt; }

    // hiện thị thông tin, để cho các lớp con ghi đè và in ra thông tin của chúng.
    public abstract String toDisplayString();

    @Override
    // Định nghĩa lại hàm equals để so sánh id, nếu không sẽ so sánh địa chỉ bộ nhớ
    public boolean equals(Object o){
        if(this == o) return true; // nếu cùng địa chỉ thì chắc chăn bằng nhau
        if(o==null || getClass() != o) return false;
        Entity entity = (Entity) o;
        return id.equals(entity.id);
    }
    @Override
    public int hashCode(){
        return id.hashCode();
    }
}