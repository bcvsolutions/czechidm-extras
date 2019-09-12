package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

import java.util.List;

public class SyncStatusPojo {

	private String systemName = null;
	private List<SystemSyncStatusPojo> syncs = null;

	public String getSystemName() {
		return systemName;
	}

	public void setSystemName(String systemName) {
		this.systemName = systemName;
	}

	public List<SystemSyncStatusPojo> getSyncs() {
		return syncs;
	}

	public void setSyncs(List<SystemSyncStatusPojo> syncs) {
		this.syncs = syncs;
	}
}
