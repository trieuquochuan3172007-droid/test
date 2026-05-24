package com.auction.common.models;
public class Admin extends User {
    public Admin(String id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email);
    }
    @Override
    public String getRole() {
        return "ADMIN";
    }
    @Override
    public void printInfo() {
        System.out.println("=== QUẢN TRỊ VIÊN ===");
        super.printInfo();
    }
    // Các phương thức đặc quyền của Admin có thể thêm sau này:
    // public void lockUser(User user) { ... }
    // public void approveProduct(Item item) { ... }
}