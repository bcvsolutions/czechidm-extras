package eu.bcvsolutions.idm.extras.report.identity;

import java.io.Serializable;
import java.util.UUID;

/**
 * Transport data from executor to renderer
 *
 * @author Roman Kučera
 */
public class IdentityStateReportDto implements Serializable {

	private static final long serialVersionUID = 1L;
	private UUID id;
	private String username;
	private String firstName;
	private String lastName;
	private String state;
	private String dateOfChange;
	private String personalNumber;

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getFirstName() {
		return firstName;
	}

	public void setFirstName(String firstName) {
		this.firstName = firstName;
	}

	public String getLastName() {
		return lastName;
	}

	public void setLastName(String lastName) {
		this.lastName = lastName;
	}

	public String getState() {
		return state;
	}

	public void setState(String state) {
		this.state = state;
	}

	public String getDateOfChange() {
		return dateOfChange;
	}

	public void setDateOfChange(String dateOfChange) {
		this.dateOfChange = dateOfChange;
	}

	public String getPersonalNumber() {
		return personalNumber;
	}

	public void setPersonalNumber(String personalNumber) {
		this.personalNumber = personalNumber;
	}
}

