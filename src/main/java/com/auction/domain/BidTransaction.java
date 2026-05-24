package com.auction.domain;

import java.time.LocalDateTime;

public class BidTransaction {
    private final String auctionID;
    private final String bidderID;
    private final double bidAmount;
    private final LocalDateTime bidTime;

    public BidTransaction(String auctionID, String bidderID, double bidAmount, LocalDateTime bidTime) {
        this.auctionID = auctionID;
        this.bidderID = bidderID;
        this.bidAmount = bidAmount;
        this.bidTime = bidTime;
    }

    public String getAuctionID() {
        return auctionID;
    }

    public String getBidderID() {
        return bidderID;
    }

    public double getBidAmount() {
        return bidAmount;
    }

    public LocalDateTime getBidTime() {
        return bidTime;
    }
}
