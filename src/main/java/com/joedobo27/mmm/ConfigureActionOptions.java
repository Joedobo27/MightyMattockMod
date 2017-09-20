package com.joedobo27.mmm;

class ConfigureActionOptions {
    private final int minSkill;
    private final int maxSkill;
    private final int longestTime;
    private final int shortestTime;
    private final int minimumStamina;

    ConfigureActionOptions(int minSkill, int maxSkill, int longestTime, int shortestTime, int minimumStamina) {
        this.minSkill = minSkill;
        this.maxSkill = maxSkill;
        this.longestTime = longestTime;
        this.shortestTime = shortestTime;
        this.minimumStamina = minimumStamina;
    }

    int getMinSkill() {
        return minSkill;
    }

    int getMaxSkill() {
        return maxSkill;
    }

    int getLongestTime() {
        return longestTime;
    }

    int getShortestTime() {
        return shortestTime;
    }

    int getMinimumStamina() {
        return minimumStamina;
    }
}
