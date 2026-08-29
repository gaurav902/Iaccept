package com.tellmeindia.iaccept.data;

import androidx.room.*;
import kotlinx.coroutines.flow.Flow;
import java.util.List;

@Dao
public interface IAcceptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void saveProfile(UserProfile user);

    @Query("SELECT * FROM user_profile LIMIT 1")
    Flow<UserProfile> getProfile();

    @Query("SELECT * FROM user_profile LIMIT 1")
    UserProfile getProfileSync();

    @Query("SELECT * FROM user_profile WHERE phone = :loginId OR gmail = :loginId LIMIT 1")
    UserProfile findUser(String loginId);

    @Query("DELETE FROM user_profile")
    void clearProfile();
}
