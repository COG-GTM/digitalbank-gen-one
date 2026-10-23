package io.digisic.credit.model;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

import io.digisic.credit.model.security.Users;

@Entity
public class AuthorizedUsers {
	
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	private long id;
	
	@ManyToOne(fetch=FetchType.EAGER)
	@JoinColumn(name="userId")
	private Users user;
	
	@ManyToOne(fetch=FetchType.EAGER)
	@JoinColumn(name="cardId")
	private CreditCard card;
	
	/*
	 * Default Constructor
	 */
	public AuthorizedUsers() {
		
	}
	
	/*
	 * Constructor
	 */
	public AuthorizedUsers(Users user, CreditCard card) {
		
		this.user = user;
		this.card = card;
		
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
	 * @return the card
	 */
	public CreditCard getCard() {
		return card;
	}

	/**
	 * @param card the card to set
	 */
	public void setCard(CreditCard card) {
		this.card = card;
	}

}
