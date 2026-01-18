package com.pdfreader.app.models;

public class UserProfile {
    private String name;
    private String profileImageUrl;
    private String readingHabit; // "Evening Reader", "Morning Reader", etc.
    private String preferredGenre;
    private int dailyTimeGoalMinutes;
    private int dailyPagesGoal;

    public UserProfile(String name) {
        this.name = name;
        this.dailyTimeGoalMinutes = 60;
        this.dailyPagesGoal = 30;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getReadingHabit() {
        return readingHabit;
    }

    public void setReadingHabit(String readingHabit) {
        this.readingHabit = readingHabit;
    }

    public String getPreferredGenre() {
        return preferredGenre;
    }

    public void setPreferredGenre(String preferredGenre) {
        this.preferredGenre = preferredGenre;
    }

    public int getDailyTimeGoalMinutes() {
        return dailyTimeGoalMinutes;
    }

    public void setDailyTimeGoalMinutes(int dailyTimeGoalMinutes) {
        this.dailyTimeGoalMinutes = dailyTimeGoalMinutes;
    }

    public int getDailyPagesGoal() {
        return dailyPagesGoal;
    }

    public void setDailyPagesGoal(int dailyPagesGoal) {
        this.dailyPagesGoal = dailyPagesGoal;
    }

    public String getGreeting() {
        // Use device's current time from system clock
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        int hour = calendar.get(java.util.Calendar.HOUR_OF_DAY);
        
        // Determine greeting based on device's current hour
        if (hour >= 5 && hour < 12) {
            return "Good Morning, " + name;
        } else if (hour >= 12 && hour < 17) {
            return "Good Afternoon, " + name;
        } else if (hour >= 17 && hour < 22) {
            return "Good Evening, " + name;
        } else {
            return "Good Night, " + name;
        }
    }
}
