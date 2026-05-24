package com.auction.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuctionSessionTest {

    @Test
    void processBidShouldRejectWhenSessionIsNotRunning() {
        LocalDateTime now = LocalDateTime.now();
        AuctionSession session = new AuctionSession("A1", "ITEM1", "SELLER1", 100.0, now, now.plusHours(1));

        boolean accepted = session.processBid("BIDDER1", 150.0);

        assertFalse(accepted);
        assertEquals(100.0, session.getCurrentHighestBid());
    }

    @Test
    void processBidShouldUpdateHighestBidAndWinner() {
        LocalDateTime now = LocalDateTime.now();
        AuctionSession session = new AuctionSession("A2", "ITEM2", "SELLER2", 100.0, now, now.plusHours(1));
        session.setStatus(AuctionStatus.RUNNING);

        boolean accepted = session.processBid("BIDDER2", 160.0);

        assertTrue(accepted);
        assertEquals(160.0, session.getCurrentHighestBid());
        assertEquals("BIDDER2", session.getWinnerID());
    }

    @Test
    void processBidShouldExtendWhenAuctionEndsSoon() {
        LocalDateTime now = LocalDateTime.now();
        AuctionSession session = new AuctionSession("A3", "ITEM3", "SELLER3", 100.0, now, now.plusHours(1));
        session.setStatus(AuctionStatus.RUNNING);
        LocalDateTime originalEnd = LocalDateTime.now().plusSeconds(20);
        session.setEndTime(originalEnd);

        boolean accepted = session.processBid("BIDDER3", 180.0);

        assertTrue(accepted);
        assertEquals(AuctionStatus.EXTENDED, session.getStatus());
        LocalDateTime extendedEnd = session.getEndTime();
        assertTrue(extendedEnd.isAfter(originalEnd.plusSeconds(59)));
    }
}
