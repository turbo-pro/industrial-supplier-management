package io.github.turbopro.ism.supplier;
import org.apache.ibatis.annotations.*;

/** Global anonymous admission control, keyed only by hashed transport peer and DB UTC minute. */
@Mapper
public interface PortalRegistrationRateMapper {
    @Insert("INSERT INTO sup_portal_registration_rate_bucket(source_hash,window_minute,attempts) VALUES(#{hash},TIMESTAMPDIFF(MINUTE,'1970-01-01',UTC_TIMESTAMP()),1) ON DUPLICATE KEY UPDATE attempts=attempts+1")
    int countAttempt(String hash);
    @Select("SELECT attempts FROM sup_portal_registration_rate_bucket WHERE source_hash=#{hash} AND window_minute=TIMESTAMPDIFF(MINUTE,'1970-01-01',UTC_TIMESTAMP())")
    Integer attempts(String hash);
    @Delete("DELETE FROM sup_portal_registration_rate_bucket WHERE window_minute<TIMESTAMPDIFF(MINUTE,'1970-01-01',UTC_TIMESTAMP())-60 LIMIT 100")
    int cleanup();
}
