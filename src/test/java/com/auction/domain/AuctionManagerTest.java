package com.auction.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionManagerTest {

    @Test
    void createStartBidAndCloseSessionShouldFollowExpectedFlow() {
        AuctionManager manager = AuctionManager.getInstance();
        String auctionId = "TEST-" + System.nanoTime();

        assertTrue(manager.createSession(auctionId, "ITEM-1", "SELLER-1", 100.0));
        assertTrue(manager.startSession(auctionId));
        assertTrue(manager.placeBid(auctionId, "BIDDER-1", 150.0));
        assertTrue(manager.closeSession(auctionId));

        AuctionSession session = manager.getSession(auctionId);
        assertNotNull(session);
        assertEquals(150.0, session.getCurrentHighestBid());
        assertEquals("BIDDER-1", session.getWinnerID());
        assertEquals(AuctionStatus.FINISHED, session.getStatus());
    }

    @Test
    void createSessionShouldRejectDuplicateAuctionId() {
        AuctionManager manager = AuctionManager.getInstance();
        String auctionId = "TEST-DUP-" + System.nanoTime();

        assertTrue(manager.createSession(auctionId, "ITEM-2", "SELLER-2", 200.0));
        assertFalse(manager.createSession(auctionId, "ITEM-3", "SELLER-3", 300.0));
    }
}
