package com.tellmeindia.iaccept.data;

import androidx.room.*;
import kotlinx.coroutines.flow.Flow;
import java.util.List;

@Dao
public interface IAcceptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertRide(RideRecord ride);

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    Flow<List<RideRecord>> getAllRides();

    @Query("SELECT * FROM ride_history WHERE userId = :userId ORDER BY timestamp DESC")
    Flow<List<RideRecord>> getRidesForUser(String userId);

    @Query("SELECT * FROM ride_history WHERE userId = :userId")
    List<RideRecord> getRidesForUserSync(String userId);

    @Query("SELECT * FROM ride_history WHERE isSynced = 0")
    List<RideRecord> getUnsyncedRides();

    @Update
    void updateRide(RideRecord ride);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveProfile(UserProfile user);

    @Query("SELECT * FROM user_profile LIMIT 1")
    Flow<UserProfile> getProfile();

    @Query("SELECT * FROM user_profile LIMIT 1")
    UserProfile getProfileSync();

    @Query("SELECT * FROM user_profile WHERE phone = :loginId OR gmail = :loginId LIMIT 1")
    UserProfile findUser(String loginId);

    @Query("DELETE FROM ride_history")
    void clearAllRides();

    @Query("DELETE FROM user_profile")
    void clearProfile();
}
