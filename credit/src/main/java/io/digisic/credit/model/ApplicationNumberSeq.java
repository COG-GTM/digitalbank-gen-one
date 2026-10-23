package io.digisic.credit.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonProperty.Access;

@Entity
@SequenceGenerator(name="APP_NO_SEQ", initialValue=728934001, allocationSize=50)
public class ApplicationNumberSeq {
	
	@Id
	@GeneratedValue(strategy=GenerationType.SEQUENCE, generator="APP_NO_SEQ")
	@Column(nullable=false, updatable=false, unique=true)
	@JsonProperty (access = Access.READ_ONLY)
	private Long id;

	/**
	 * @return the id
	 */
	public Long getId() {
		return id;
	}
}
