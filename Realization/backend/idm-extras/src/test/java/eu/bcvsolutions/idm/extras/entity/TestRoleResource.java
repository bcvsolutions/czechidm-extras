package eu.bcvsolutions.idm.extras.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import java.time.ZonedDateTime;

import eu.bcvsolutions.idm.core.api.domain.DefaultFieldLengths;

/**
 * Entity for test (role) table resource
 * 
 * @author svandav
 *
 */
@Entity
@Table(name = TestRoleResource.TABLE_NAME)
public class TestRoleResource {
	
	public static final String TABLE_NAME = "test_role_resource";

	@Id
	@Column(name = "NAME", length = DefaultFieldLengths.NAME)
	private String name;
	@Column(name = "TYPE", length = DefaultFieldLengths.NAME)
	private String type;
	@Column(name = "PRIORITY")
	private int priority;
	@Column(name = "DESCRIPTION", length = DefaultFieldLengths.NAME)
	private String description;
	@Column(name = "APPROVE_REMOVE", length = DefaultFieldLengths.NAME)
	private Boolean approveRemove = Boolean.FALSE;
	@Column(name = "MODIFIED")
	private ZonedDateTime modified;
	@Column(name = "STATUS", length = DefaultFieldLengths.NAME)
	private String status;
	@Column(name = "EAV_ATTRIBUTE", length = DefaultFieldLengths.NAME)
	private String eavAttribute;
	@Column(name = "MEMBER", length = DefaultFieldLengths.LOG)
	private String member;

	public String getEavAttribute() {
		return eavAttribute;
	}

	public void setEavAttribute(String eavAttribute) {
		this.eavAttribute = eavAttribute;
	}

	public String getMember() {
		return member;
	}

	public void setMember(String member) {
		this.member = member;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public Boolean getApproveRemove() {
		return approveRemove;
	}

	public void setApproveRemove(Boolean approveRemove) {
		this.approveRemove = approveRemove;
	}

	public ZonedDateTime getModified() {
		return modified;
	}

	public void setModified(ZonedDateTime modified) {
		this.modified = modified;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	
}
