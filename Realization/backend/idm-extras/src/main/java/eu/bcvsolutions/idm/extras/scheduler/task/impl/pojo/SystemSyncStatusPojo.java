package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

public class SystemSyncStatusPojo {

	private String syncName = null;
	private boolean containsError = false;

	public String getSyncName() {
		return syncName;
	}

	public void setSyncName(String syncName) {
		this.syncName = syncName;
	}

	public boolean isContainsError() {
		return containsError;
	}

	public void setContainsError(boolean containsError) {
		this.containsError = containsError;
	}
}
