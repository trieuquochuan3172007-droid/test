package com.auction.common.models;

public class Bidder extends User {
    private Wallet wallet; 

    public Bidder(String id, String username, String password, String fullName, String email, double balance) {
        super(id, username, password, fullName, email);
        this.wallet = new Wallet(); 
        this.wallet.deposit(balance); // Nạp số dư ban đầu vào ví
    }

    @Override
    public String getRole() {
        return "BIDDER";
    }

    // Trả về trực tiếp object Wallet để xử lý khóa/mở tiền
    public Wallet getWallet() {
        return wallet;
    }

    // Giữ lại hàm này để code cũ (như UserDAO) gọi không bị lỗi
    public double getBalance() {
        return wallet.getBalance();
    }

    @Override
    public void printInfo() {
        super.printInfo();
        System.out.println("Số dư khả dụng: " + wallet.getBalance() + " | Tiền đang cọc: " + wallet.getFrozenAmount());
    }
}