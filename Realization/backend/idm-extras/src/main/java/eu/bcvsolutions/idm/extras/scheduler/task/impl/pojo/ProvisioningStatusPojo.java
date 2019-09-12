package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

import java.util.List;

public class ProvisioningStatusPojo {

	private String systemName = null;
	private Long error = null;
	private List<String> errorNiceLabels = null;
	private Long blocked = null;
	private List<String> blockedNiceLabels = null;
	private Long notExecuted = null;
	private List<String> notExecutedNiceLabels = null;

	public String getSystemName() {
		return systemName;
	}

	public void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	public Long getError() {
		return error;
	}

	public void setError(Long error) {
		this.error = error;
	}

	public Long getBlocked() {
		return blocked;
	}

	public void setBlocked(Long blocked) {
		this.blocked = blocked;
	}

	public Long getNotExecuted() {
		return notExecuted;
	}

	public void setNotExecuted(Long notExecuted) {
		this.notExecuted = notExecuted;
	}

	public List<String> getErrorNiceLabels() {
		return errorNiceLabels;
	}

	public void setErrorNiceLabels(List<String> errorNiceLabels) {
		this.errorNiceLabels = errorNiceLabels;
	}

	public List<String> getBlockedNiceLabels() {
		return blockedNiceLabels;
	}

	public void setBlockedNiceLabels(List<String> blockedNiceLabels) {
		this.blockedNiceLabels = blockedNiceLabels;
	}

	public List<String> getNotExecutedNiceLabels() {
		return notExecutedNiceLabels;
	}

	public void setNotExecutedNiceLabels(List<String> notExecutedNiceLabels) {
		this.notExecutedNiceLabels = notExecutedNiceLabels;
	}
}
