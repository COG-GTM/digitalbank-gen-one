package io.digisic.bank.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import io.digisic.bank.model.UserProfile;

public interface UserProfileRepository extends CrudRepository<UserProfile, Long> {
	
	UserProfile findByEmailAddress(String paramString);
	  
	UserProfile findBySsn(String paramString);
	  
	List<UserProfile> findAll();
	
	/*
	 * Native queries read and write the raw ssn column, bypassing the encryption
	 * converter, to handle rows stored as plaintext before encryption was enabled.
	 */
	@Query(value="select count(*) from user_profile where ssn = :ssn", nativeQuery=true)
	long countByStoredSsn(@Param("ssn") String ssn);
	
	@Query(value="select id, ssn from user_profile where ssn not like 'enc:v1:%'", nativeQuery=true)
	List<Object[]> findPlaintextSsns();
	
	@Modifying
	@Query(value="update user_profile set ssn = :ssn where id = :id", nativeQuery=true)
	int updateStoredSsn(@Param("id") Long id, @Param("ssn") String ssn);

}
