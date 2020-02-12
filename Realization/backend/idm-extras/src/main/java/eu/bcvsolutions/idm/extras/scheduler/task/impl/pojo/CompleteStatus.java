package eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo;

import java.util.List;

import eu.bcvsolutions.idm.extras.report.identity.IdentityStateReportDto;

public class CompleteStatus {

	private List<ProvisioningStatusPojo> provisioning = null;
	private List<LrtStatusPojo> lrts = null;
	private EventStatusPojo events = null;
	private List<SyncStatusPojo> syncs = null;
	private String uptime = null;
	private boolean containsError = false;
	private String errorDuringSend = null;
	private List<IdentityStateReportDto> contracts = null;
	private List<LrtStatusPojo> lrtRunningTooLong = null;
	private Integer numberOfEvents = null;

	public List<LrtStatusPojo> getLrtRunningTooLong() {
		return lrtRunningTooLong;
	}

	public void setLrtRunningTooLong(List<LrtStatusPojo> lrtRunningTooLong) {
		this.lrtRunningTooLong = lrtRunningTooLong;
	}

	public Integer getNumberOfEvents() {
		return numberOfEvents;
	}

	public void setNumberOfEvents(Integer numberOfEvents) {
		this.numberOfEvents = numberOfEvents;
	}

	public List<ProvisioningStatusPojo> getProvisioning() {
		return provisioning;
	}

	public void setProvisioning(List<ProvisioningStatusPojo> provisioning) {
		this.provisioning = provisioning;
	}

	public List<LrtStatusPojo> getLrts() {
		return lrts;
	}

	public void setLrts(List<LrtStatusPojo> lrts) {
		this.lrts = lrts;
	}

	public EventStatusPojo getEvents() {
		return events;
	}

	public void setEvents(EventStatusPojo events) {
		this.events = events;
	}

	public List<SyncStatusPojo> getSyncs() {
		return syncs;
	}

	public void setSyncs(List<SyncStatusPojo> syncs) {
		this.syncs = syncs;
	}

	public String getUptime() {
		return uptime;
	}

	public void setUptime(String uptime) {
		this.uptime = uptime;
	}

	public boolean isContainsError() {
		return containsError;
	}

	public void setContainsError(boolean containsError) {
		this.containsError = containsError;
	}

	public String getErrorDuringSend() {
		return errorDuringSend;
	}

	public void setErrorDuringSend(String errorDuringSend) {
		this.errorDuringSend = errorDuringSend;
	}

	public List<IdentityStateReportDto> getContracts() {
		return contracts;
	}

	public void setContracts(List<IdentityStateReportDto> contracts) {
		this.contracts = contracts;
	}
}
