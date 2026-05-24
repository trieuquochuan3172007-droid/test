package com.auction.common.models;
public class Seller extends User {
    public Seller(String id, String username, String password, String fullName, String email) {
        super(id, username, password, fullName, email);
    }
    @Override
    public String getRole() {
        return "SELLER";
    }
}