package com.krishimandi.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String name;
    private String password;
    private String role = "FARMER";
    private String state;
    private List<String> watchlist;
    private boolean alertsEnabled = true;
    private String language = "en";
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lastLoginAt;
    private int totalPredictions = 0;

    public User() {}

    public String getId()                { return id; }
    public String getName()              { return name; }
    public String getEmail()             { return email; }
    public String getPassword()          { return password; }
    public String getRole()              { return role; }
    public String getState()             { return state; }
    public List<String> getWatchlist()   { return watchlist; }
    public boolean isAlertsEnabled()     { return alertsEnabled; }
    public String getLanguage()          { return language; }
    public LocalDateTime getCreatedAt()  { return createdAt; }
    public LocalDateTime getLastLoginAt(){ return lastLoginAt; }
    public int getTotalPredictions()     { return totalPredictions; }

    public void setId(String id)                      { this.id = id; }
    public void setName(String name)                  { this.name = name; }
    public void setEmail(String email)                { this.email = email; }
    public void setPassword(String password)          { this.password = password; }
    public void setRole(String role)                  { this.role = role; }
    public void setState(String state)                { this.state = state; }
    public void setWatchlist(List<String> w)          { this.watchlist = w; }
    public void setAlertsEnabled(boolean v)           { this.alertsEnabled = v; }
    public void setLanguage(String language)          { this.language = language; }
    public void setCreatedAt(LocalDateTime dt)        { this.createdAt = dt; }
    public void setLastLoginAt(LocalDateTime dt)      { this.lastLoginAt = dt; }
    public void setTotalPredictions(int n)            { this.totalPredictions = n; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final User u = new User();
        public Builder name(String v)           { u.name = v; return this; }
        public Builder email(String v)          { u.email = v; return this; }
        public Builder password(String v)       { u.password = v; return this; }
        public Builder role(String v)           { u.role = v; return this; }
        public Builder state(String v)          { u.state = v; return this; }
        public Builder language(String v)       { u.language = v; return this; }
        public Builder alertsEnabled(boolean v) { u.alertsEnabled = v; return this; }
        public Builder watchlist(List<String> v){ u.watchlist = v; return this; }
        public Builder totalPredictions(int v)  { u.totalPredictions = v; return this; }
        public Builder createdAt(LocalDateTime v){ u.createdAt = v; return this; }
        public User build()                     { return u; }
    }
}
