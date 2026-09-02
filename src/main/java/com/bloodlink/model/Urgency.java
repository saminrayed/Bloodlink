package com.bloodlink.model;

public enum Urgency {
    NORMAL(1), URGENT(2), CRITICAL(3);
    private final int weight;
    Urgency(int weight) { this.weight = weight; }
    public int getWeight() { return weight; }
}
