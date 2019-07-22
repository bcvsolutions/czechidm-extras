package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

public class LrtStatusPojo {

	private String type = null;
	private String result = null;
	private Long warningCount = null;
	private Long failedCount = null;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getResult() {
		return result;
	}

	public void setResult(String result) {
		this.result = result;
	}

	public Long getWarningCount() {
		return warningCount;
	}

	public void setWarningCount(Long warningCount) {
		this.warningCount = warningCount;
	}

	public Long getFailedCount() {
		return failedCount;
	}

	public void setFailedCount(Long failedCount) {
		this.failedCount = failedCount;
	}
}
