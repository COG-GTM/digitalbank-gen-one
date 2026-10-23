package io.digisic.bank.model;

import java.math.BigDecimal;
import java.util.Date;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.digisic.bank.model.security.Users;

/*
 * A money movement held for a second (checker) approval before it is posted.
 */
@Entity
public class PendingTransaction {

	public static final String STATUS_PENDING	= "PENDING";
	public static final String STATUS_APPROVED	= "APPROVED";
	public static final String STATUS_REJECTED	= "REJECTED";

	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE)
	@Column(name="id", nullable=false, updatable=false)
	private Long id;

	@JsonIgnore
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name = "account_id", nullable = false)
	private Account account;

	@JsonIgnore
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name = "to_account_id")
	private Account toAccount;

	private BigDecimal amount;

	private boolean debit;

	private String description;

	@ManyToOne (fetch = FetchType.EAGER)
	private TransactionType transactionType;

	private String status;

	@JsonIgnore
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name = "maker_id", nullable = false)
	private Users maker;

	@JsonIgnore
	@ManyToOne (fetch = FetchType.EAGER)
	@JoinColumn(name = "checker_id")
	private Users checker;

	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm")
	private Date createdDate;

	@JsonFormat(pattern="yyyy-MM-dd'T'HH:mm")
	private Date decidedDate;

	public PendingTransaction () {}

	public boolean isTransfer() {
		return toAccount != null;
	}

	@JsonProperty("accountId")
	public Long getAccountId() {
		return account == null ? null : account.getId();
	}

	@JsonProperty("toAccountId")
	public Long getToAccountId() {
		return toAccount == null ? null : toAccount.getId();
	}

	@JsonProperty("makerId")
	public Long getMakerId() {
		return maker == null ? null : maker.getId();
	}

	@JsonProperty("checkerId")
	public Long getCheckerId() {
		return checker == null ? null : checker.getId();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Account getAccount() {
		return account;
	}

	public void setAccount(Account account) {
		this.account = account;
	}

	public Account getToAccount() {
		return toAccount;
	}

	public void setToAccount(Account toAccount) {
		this.toAccount = toAccount;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public boolean isDebit() {
		return debit;
	}

	public void setDebit(boolean debit) {
		this.debit = debit;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public TransactionType getTransactionType() {
		return transactionType;
	}

	public void setTransactionType(TransactionType transactionType) {
		this.transactionType = transactionType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public Users getMaker() {
		return maker;
	}

	public void setMaker(Users maker) {
		this.maker = maker;
	}

	public Users getChecker() {
		return checker;
	}

	public void setChecker(Users checker) {
		this.checker = checker;
	}

	public Date getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(Date createdDate) {
		this.createdDate = createdDate;
	}

	public Date getDecidedDate() {
		return decidedDate;
	}

	public void setDecidedDate(Date decidedDate) {
		this.decidedDate = decidedDate;
	}

}
