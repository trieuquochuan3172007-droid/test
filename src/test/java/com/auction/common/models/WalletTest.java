package com.auction.common.models;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WalletTest {

    @Test
    void depositWithdrawAndFreezeShouldUpdateBalancesCorrectly() {
        Wallet wallet = new Wallet();

        wallet.deposit(1_000.0);

        assertTrue(wallet.withdraw(250.0));
        assertTrue(wallet.freeze(300.0));

        wallet.release(100.0);

        assertEquals(550.0, wallet.getBalance());
        assertEquals(200.0, wallet.getFrozenAmount());
    }

    @Test
    void invalidOperationsShouldNotChangeState() {
        Wallet wallet = new Wallet();
        wallet.deposit(500.0);

        assertFalse(wallet.withdraw(700.0));
        assertFalse(wallet.freeze(-10.0));

        wallet.release(999.0);

        assertEquals(500.0, wallet.getBalance());
        assertEquals(0.0, wallet.getFrozenAmount());
    }
}
