package com.auction.common.models;
import java.io.Serializable;
public class Wallet implements Serializable {
    private static final long serialVersionUID = 1L;
    private double balance;       
    private double frozenAmount;

    public Wallet() {
        this.balance = 0.0;
        this.frozenAmount = 0.0;
    }
    public void deposit(double amount) {
        if (amount > 0) balance += amount;
    }
    public boolean withdraw(double amount) {
        if (amount > 0 && balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }
    public boolean freeze(double amount) {
        if (amount > 0 && balance >= amount) {
            balance -= amount;
            frozenAmount += amount;
            return true;
        }
        return false;
    }
    public void release(double amount) {
        if (amount > 0 && frozenAmount >= amount) {
            frozenAmount -= amount;
            balance += amount;
        }
    }
    public double getBalance() { return balance; }
    public double getFrozenAmount() { return frozenAmount; }
}