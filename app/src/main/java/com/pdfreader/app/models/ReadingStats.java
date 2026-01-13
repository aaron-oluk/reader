package com.pdfreader.app.models;

import java.util.HashMap;
import java.util.Map;

public class ReadingStats {
    private int currentStreak;
    private int longestStreak;
    private int booksFinished;
    private int pagesRead;
    private long totalTimeMinutes;
    private int readingSpeed; // words per minute
    private Map<String, Integer> weeklyPages; // day -> pages
    private int yearlyGoal;
    private int monthlyVolume;

    public ReadingStats() {
        this.weeklyPages = new HashMap<>();
        this.yearlyGoal = 24;
    }

    public int getCurrentStreak() {
        return currentStreak;
    }

    public void setCurrentStreak(int currentStreak) {
        this.currentStreak = currentStreak;
    }

    public int getLongestStreak() {
        return longestStreak;
    }

    public void setLongestStreak(int longestStreak) {
        this.longestStreak = longestStreak;
    }

    public int getBooksFinished() {
        return booksFinished;
    }

    public void setBooksFinished(int booksFinished) {
        this.booksFinished = booksFinished;
    }

    public int getPagesRead() {
        return pagesRead;
    }

    public void setPagesRead(int pagesRead) {
        this.pagesRead = pagesRead;
    }

    public long getTotalTimeMinutes() {
        return totalTimeMinutes;
    }

    public void setTotalTimeMinutes(long totalTimeMinutes) {
        this.totalTimeMinutes = totalTimeMinutes;
    }

    public int getReadingSpeed() {
        return readingSpeed;
    }

    public void setReadingSpeed(int readingSpeed) {
        this.readingSpeed = readingSpeed;
    }

    public Map<String, Integer> getWeeklyPages() {
        return weeklyPages;
    }

    public void setWeeklyPages(Map<String, Integer> weeklyPages) {
        this.weeklyPages = weeklyPages;
    }

    public int getYearlyGoal() {
        return yearlyGoal;
    }

    public void setYearlyGoal(int yearlyGoal) {
        this.yearlyGoal = yearlyGoal;
    }

    public int getMonthlyVolume() {
        return monthlyVolume;
    }

    public void setMonthlyVolume(int monthlyVolume) {
        this.monthlyVolume = monthlyVolume;
    }

    public int getGoalProgress() {
        if (yearlyGoal == 0) return 0;
        return (int) ((booksFinished * 100.0) / yearlyGoal);
    }
}
