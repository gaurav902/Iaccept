package com.tellmeindia.iaccept.data;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "ride_history")
public class RideRecord {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String userId; // Unique ID to separate user data
    public int fare;
    public double distance;
    public double pickupDistance;
    public double dropDistance;
    public String pickupAddress;
    public String dropAddress;
    public String title;
    public String details;
    public long timestamp;
    public boolean isAccepted;
    public boolean isSynced;

    public RideRecord() {
        this.id = 0;
        this.isSynced = false;
    }

    public RideRecord(long id, String userId, int fare, double distance, double pickupDistance, double dropDistance, String pickupAddress, String dropAddress, String title, String details, long timestamp, boolean isAccepted, boolean isSynced) {
        this.id = id;
        this.userId = userId;
        this.fare = fare;
        this.distance = distance;
        this.pickupDistance = pickupDistance;
        this.dropDistance = dropDistance;
        this.pickupAddress = pickupAddress;
        this.dropAddress = dropAddress;
        this.title = title;
        this.details = details;
        this.timestamp = timestamp;
        this.isAccepted = isAccepted;
        this.isSynced = isSynced;
    }

    // Manual copy method
    public RideRecord copy(boolean isSynced) {
        return new RideRecord(this.id, this.userId, this.fare, this.distance, this.pickupDistance, this.dropDistance, this.pickupAddress, this.dropAddress, this.title, this.details, this.timestamp, this.isAccepted, isSynced);
    }
}
