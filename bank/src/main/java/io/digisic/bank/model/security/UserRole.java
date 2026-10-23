package io.digisic.bank.model.security;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
public class UserRole {
  
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	private long id;
  
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name="userId")
	private Users user;
  
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name="roleId")
	private Role role;
	
	// default constructor
	public UserRole() {}
  
	public UserRole(Users user, Role role) {
		this.user = user;
		this.role = role;
	}

	/**
	 * @return the id
	 */
	public long getId() {
		return id;
	}

	/**
	 * @param id the id to set
	 */
	public void setId(long id) {
		this.id = id;
	}

	/**
	 * @return the user
	 */
	public Users getUser() {
		return user;
	}

	/**
	 * @param user the user to set
	 */
	public void setUser(Users user) {
		this.user = user;
	}

	/**
	 * @return the role
	 */
	public Role getRole() {
		return role;
	}

	/**
	 * @param role the role to set
	 */
	public void setRole(Role role) {
		this.role = role;
	}
  
	public String toString() {
	    
		String userRole = "\n\nUser Roles ***********************";
    
		userRole += "\nId:\t\t\t" 			+ this.getId();
		userRole += "\nUser:\t\t" 			+ this.getUser().getUsername();
		userRole += "\nRole:\t\t\t" 		+ this.getRole().getName();

	    return userRole;
	    
	}
}
