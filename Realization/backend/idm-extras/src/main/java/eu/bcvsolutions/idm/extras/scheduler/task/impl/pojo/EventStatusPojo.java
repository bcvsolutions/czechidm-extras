package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

import java.util.List;

public class EventStatusPojo {

	private Long exceptions = null;
	private List<String> exceptionsNiceLabels = null;
	private Long notExecuted = null;
	private List<String> notExecutedNiceLabels = null;
	private Long blocked = null;
	private List<String> blockedNiceLabels = null;

	public Long getExceptions() {
		return exceptions;
	}

	public void setExceptions(Long exceptions) {
		this.exceptions = exceptions;
	}

	public Long getNotExecuted() {
		return notExecuted;
	}

	public void setNotExecuted(Long notExecuted) {
		this.notExecuted = notExecuted;
	}

	public Long getBlocked() {
		return blocked;
	}

	public void setBlocked(Long blocked) {
		this.blocked = blocked;
	}

	public List<String> getExceptionsNiceLabels() {
		return exceptionsNiceLabels;
	}

	public void setExceptionsNiceLabels(List<String> exceptionsNiceLabels) {
		this.exceptionsNiceLabels = exceptionsNiceLabels;
	}

	public List<String> getNotExecutedNiceLabels() {
		return notExecutedNiceLabels;
	}

	public void setNotExecutedNiceLabels(List<String> notExecutedNiceLabels) {
		this.notExecutedNiceLabels = notExecutedNiceLabels;
	}

	public List<String> getBlockedNiceLabels() {
		return blockedNiceLabels;
	}

	public void setBlockedNiceLabels(List<String> blockedNiceLabels) {
		this.blockedNiceLabels = blockedNiceLabels;
	}
}
