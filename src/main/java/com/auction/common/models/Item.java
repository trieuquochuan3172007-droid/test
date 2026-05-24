package com.auction.common.models;

public abstract class Item extends Entity{
    protected String name;
    protected String description;
    protected double initPrice;
    protected String category;

    public Item(String id, String name, String description, double initPrice, String category){
        super(id);
        this.name=name;
        this.description=description;
        this.initPrice=initPrice;
        this.category=category;
    }
    public String getName(){
        return name;
    }
    public void setName(String name){
        this.name=name;
    }
    public String getDescription(){
        return description;
    }
    public void setDescription(String description){
        this.description=description;
    }
    public double getInitPrice() {
        return initPrice;
    }
    public void setInitPrice(double initPrice) {
        this.initPrice = initPrice;
    }
    public String getCategory() {
        return category;
    }
    public void setCategory(String category) {
        this.category = category;
    }
    public abstract void showDetail();
}
