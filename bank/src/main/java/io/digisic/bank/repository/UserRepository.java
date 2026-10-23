package io.digisic.bank.repository;

import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import io.digisic.bank.model.security.Users;

public interface UserRepository extends CrudRepository<Users, Long> {

	Users findByUsername(String paramString);
	  
	List<Users> findAll();

	@Query("select u.failedLoginAttempts from Users u where u.username = :username")
	Integer findFailedLoginAttempts(@Param("username") String username);

	@Query("select u.accountNonLocked from Users u where u.username = :username")
	Boolean findAccountNonLocked(@Param("username") String username);

	// Atomic so concurrent failures cannot overwrite each other's increment
	@Transactional
	@Modifying
	@Query("update Users u set u.failedLoginAttempts = coalesce(u.failedLoginAttempts, 0) + 1 where u.username = :username")
	int incrementFailedLoginAttempts(@Param("username") String username);

	@Transactional
	@Modifying
	@Query("update Users u set u.failedLoginAttempts = 0 where u.username = :username and u.failedLoginAttempts <> 0")
	int resetFailedLoginAttempts(@Param("username") String username);

	// Returns 1 only for the request that actually performed the lock
	@Transactional
	@Modifying
	@Query("update Users u set u.accountNonLocked = false where u.username = :username and u.accountNonLocked = true")
	int lock(@Param("username") String username);
}
