package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.joda.time.DateTime;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.entity.SysSyncLog_;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmAuditFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmAuditService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityEventDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmEntityEventService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.notification.api.domain.NotificationLevel;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmMessageDto;
import eu.bcvsolutions.idm.core.notification.api.service.NotificationManager;
import eu.bcvsolutions.idm.core.rest.lookup.IdmIdentityDtoLookup;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.extras.report.identity.IdentityStateExecutor;
import eu.bcvsolutions.idm.extras.report.identity.IdentityStateReportDto;

/**
 * LRT send status about state of CzechIdM
 * TODO: select specific system
 * TODO: now exists to much inner classes, please add these classes to each file.
 * TODO: remake recipient fields
 *
 * @author Ondrej Kopr
 */
@Service
@DisallowConcurrentExecution
@Description("Send status notification")
public class CzechIdMStatusNotificationTask extends AbstractSchedulableTaskExecutor<Boolean> {

	private static final org.slf4j.Logger LOG =
			org.slf4j.LoggerFactory.getLogger(CzechIdMStatusNotificationTask.class);
	// TODO: add to configuration and change core!!
	public static String LAST_RUN_DATE_TIME = "idm.sec.core.status.lastRun";
	//	private static String SYSTEM_ID_PARAM = "systemIdParam";
	private static String SEND_PROVISIONING_STATUS_PARAM = "sendProvisioningStatusParam";
	private static String SEND_SYNC_STATUS_PARAM = "sendSyncStatusParam";
	private static String SEND_LRT_STATUS_PARAM = "sendLrtStatusParam";
	private static String SEND_EVENT_STATUS_PARAM = "sendEventStatusParam";
	private static String SEND_CONTRACTS_STATUS_PARAM = "sendContractsStatusParam";
	private static String RECIPIENTS_PARAM = "recipients";
	private static String REGEX = ",";
	@Autowired
	private SysProvisioningOperationService provisioningOperationService;
	@Autowired
	private IdmEntityEventService entityEventService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private IdmLongRunningTaskService longRunningTaskService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private NotificationManager notificationManager;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSyncLogService syncLongService;
	@Autowired
	private IdmIdentityDtoLookup identityLookup;
	@Autowired
	private IdmAuditService auditService;
	@Autowired
	private IdentityStateExecutor identityStateExecutor;

//	private List<SysSystemDto> systems;

	private boolean sendProvisioningStatus = false;
	private boolean sendSyncStatus = false;
	private boolean sendLrtStatus = false;
	private boolean sendEventStatus = false;
	private boolean sendContractsStatus = false;
	private DateTime lastRun = null;
	private DateTime started = null;
	private List<IdmIdentityDto> recipients = null;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		// we must set started there
		started = DateTime.now();

//		systems = new ArrayList<>();
		String lastRunAsString = configurationService.getValue(LAST_RUN_DATE_TIME);

		// first run, default run - 10days
		if (lastRunAsString == null) {
			lastRunAsString = DateTime.now().minusDays(10).toString();
		}
		lastRun = new DateTime(lastRunAsString);
		//
		sendProvisioningStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_PROVISIONING_STATUS_PARAM));
		sendSyncStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_SYNC_STATUS_PARAM));
		sendLrtStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_LRT_STATUS_PARAM));
		sendEventStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_EVENT_STATUS_PARAM));
		sendContractsStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_CONTRACTS_STATUS_PARAM));
		recipients = getRecipients(properties.get(RECIPIENTS_PARAM));
		//
//		Object systemIdsAsObject = properties.get(SYSTEM_ID_PARAM);
//		if (systemIdsAsObject != null) {
//			String systemIdsAsString = systemIdsAsObject.toString();
//			String[] splitedUuid = systemIdsAsString.split(REGEX);
//			for (String uuidAsString : splitedUuid) {
//				uuidAsString = uuidAsString.trim();
//				try {
//					SysSystemDto systemDto = systemService.get(EntityUtils.toUuid(uuidAsString));
//
//					if (systemDto != null) {
//						systems.add(systemDto);
//					} else {
//						LOG.warn("System for id: [{0}] not found.", uuidAsString);
//					}
//
//				} catch (ClassCastException e) {
//					LOG.error("UUID [{0}] is not uuid.", uuidAsString, e);
//				}
//			}
//		}

		configurationService.setValue(LAST_RUN_DATE_TIME, started.toString());
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(SEND_PROVISIONING_STATUS_PARAM, sendProvisioningStatus);
		props.put(SEND_SYNC_STATUS_PARAM, sendSyncStatus);
		props.put(SEND_LRT_STATUS_PARAM, sendLrtStatus);
		props.put(SEND_EVENT_STATUS_PARAM, sendEventStatus);
		props.put(SEND_CONTRACTS_STATUS_PARAM, sendContractsStatus);
		props.put(RECIPIENTS_PARAM, recipients);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> attributes = new LinkedList<>();
		attributes.add(createBooleanAttribute(SEND_PROVISIONING_STATUS_PARAM, "Send provisioning status"));
		attributes.add(createBooleanAttribute(SEND_CONTRACTS_STATUS_PARAM, "Send contract status"));
		attributes.add(createBooleanAttribute(SEND_EVENT_STATUS_PARAM, "Send event status"));
		attributes.add(createBooleanAttribute(SEND_LRT_STATUS_PARAM, "Send LRT status"));
		attributes.add(createBooleanAttribute(SEND_SYNC_STATUS_PARAM, "Send sync status"));
		//
		IdmFormAttributeDto recipients = new IdmFormAttributeDto(
				RECIPIENTS_PARAM,
				"Recipient identities",
				PersistentType.SHORTTEXT);
		recipients.setPlaceholder("For multiple recipients add coma between...");
		attributes.add(recipients);
		return attributes;
	}

	private IdmFormAttributeDto createBooleanAttribute(String face, String name) {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto(
				face,
				name,
				PersistentType.BOOLEAN);
		attribute.setFaceType(null);
		return attribute;
	}

	@Override
	public Boolean process() {
		CompleteStatus status = new CompleteStatus();
		status.containsError = false;

		try {

			if (sendProvisioningStatus) {
				status.provisioning = getProvisioningStatus();
				if (status.provisioning.size() != 0) {
					status.containsError = true;
				}
			}

			if (sendLrtStatus) {
				status.lrts = getLrtStatus();
				if (status.lrts.size() != 0) {
					status.containsError = true;
				}
			}

			if (sendEventStatus) {
				status.events = getEventStatus();
				if (status.events != null) {
					status.containsError = true;
				}
			}

			if (sendSyncStatus) {
				status.syncs = getSyncStatus();
				if (status.syncs.size() != 0) {
					status.containsError = true;
				}
			}

			if (sendContractsStatus) {
				status.contracts = getContractsStatus();
			}

			RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
			status.uptime = String.valueOf((runtimeMXBean.getUptime() / (1000 * 60 * 60 * 24)));
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.errorDuringSend = e.getMessage();
		} finally {
			notificationManager.send(ExtrasModuleDescriptor.TOPIC_STATUS,
					new IdmMessageDto.Builder()
							.setLevel(NotificationLevel.INFO)
							.addParameter("status", status)
							.build(),
					recipients);
		}
		if (status.errorDuringSend != null) {
			return Boolean.FALSE;
		}
		return Boolean.TRUE;
	}

	/**
	 * Get status per system
	 *
	 * @return
	 */
	private List<ProvisioningStatusPojo> getProvisioningStatus() {
		List<ProvisioningStatusPojo> systems = new ArrayList<>();


		for (SysSystemDto system : systemService.find(null)) {
			ProvisioningStatusPojo status = new ProvisioningStatusPojo();
			status.systemName = system.getName();

			SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
			filter.setSystemId(system.getId());

			filter.setResultState(OperationState.EXCEPTION);
			List<SysProvisioningOperationDto> list = provisioningOperationService.find(filter, null).getContent();

			if (!list.isEmpty()) {
				status.error = (long) list.size();
				status.errorNiceLabels = getIdentityNiceLabelForOperations(list);
			}

			filter.setResultState(OperationState.BLOCKED);
			list = provisioningOperationService.find(filter, null).getContent();
			if (!list.isEmpty()) {
				status.blocked = (long) list.size();
				status.blockedNiceLabels = getIdentityNiceLabelForOperations(list);
			}

			filter.setResultState(OperationState.NOT_EXECUTED);
			list = provisioningOperationService.find(filter, null).getContent();
			if (!list.isEmpty()) {
				status.notExecuted = (long) list.size();
				status.notExecutedNiceLabels = getIdentityNiceLabelForOperations(list);
			}

			if (status.blocked != null || status.error != null || status.notExecuted != null) {
				systems.add(status);
			}
		}

		return systems;
	}

	/**
	 * Return identity nice labels for given operation. Now supports only identity as entity type.
	 * Final nice labels are unique.
	 *
	 * @param operations
	 * @return
	 */
	private List<String> getIdentityNiceLabelForOperations(List<SysProvisioningOperationDto> operations) {
		List<String> niceLabels = new ArrayList<String>();
		if (operations == null) {
			return niceLabels;
		}

		for (SysProvisioningOperationDto operation : operations) {
			// Now supports only identity
			if (operation.getEntityType() == SystemEntityType.IDENTITY) {
				IdmIdentityDto identityDto = identityLookup.lookup(operation.getEntityIdentifier());
				StringBuilder niceLabel = new StringBuilder();
				if (identityDto == null) {
					niceLabel.append("DEL:");
					niceLabel.append(operation.getEntityIdentifier());
					// Deleted
				} else {
					niceLabel.append(identityDto.getUsername());
					niceLabel.append(" (");
					niceLabel.append(identityDto.getExternalCode());
					niceLabel.append(')');
				}
				String finalNiceLabel = niceLabel.toString();
				if (!niceLabels.contains(finalNiceLabel)) {
					niceLabels.add(niceLabel.toString());
				}
			}
		}
		return niceLabels;
	}

	/**
	 * Status all queue
	 *
	 * @return
	 */
	private List<LrtStatusPojo> getLrtStatus() {
		List<LrtStatusPojo> statuses = new ArrayList<>();

		IdmLongRunningTaskFilter filter = new IdmLongRunningTaskFilter();
		filter.setFrom(lastRun);

		for (IdmLongRunningTaskDto task : longRunningTaskService.find(filter, null).getContent()) {
			LrtStatusPojo status = new LrtStatusPojo();
			status.type = task.getTaskType();
			// dry run skip
			if (task.isDryRun()) {
				continue;
			}

			if (task.getResultState() == OperationState.EXCEPTION) {
				status.result = OperationState.EXCEPTION.name();
			} else if (task.getResultState() == OperationState.CANCELED) {
				status.result = OperationState.CANCELED.name();
			} else if (task.getResultState() == OperationState.NOT_EXECUTED) {
				status.result = OperationState.NOT_EXECUTED.name();
			}

			if (status.result == null) {
				continue;
			}

			status.failedCount = task.getFailedItemCount();
			status.warningCount = task.getWarningItemCount();

			if (status.result != null || status.failedCount != null || status.warningCount != null) {
				statuses.add(status);
			}
		}

		return statuses;

	}

	private EventStatusPojo getEventStatus() {
		EventStatusPojo status = new EventStatusPojo();

		IdmEntityEventFilter filter = new IdmEntityEventFilter();

		filter.setStates(Lists.newArrayList(OperationState.EXCEPTION));
		List<IdmEntityEventDto> list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty()) {
			status.exceptions = (long) list.size();
			status.exceptionsNiceLabels = getIdentityNiceLabelForEvents(list);
		}

		filter.setStates(Lists.newArrayList(OperationState.NOT_EXECUTED));
		list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty()) {
			status.notExecuted = (long) list.size();
			status.notExecutedNiceLabels = getIdentityNiceLabelForEvents(list);
		}

		filter.setStates(Lists.newArrayList(OperationState.BLOCKED));
		list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty()) {
			status.blocked = (long) list.size();
			status.blockedNiceLabels = getIdentityNiceLabelForEvents(list);
		}

		if (status.blocked != null || status.exceptions != null || status.notExecuted != null) {
			return status;
		}
		return null;
	}

	/**
	 * Return nice labels for given events. Method return nice labels only for identities.
	 * Returend list are unique.
	 *
	 * @param events
	 * @return
	 */
	private List<String> getIdentityNiceLabelForEvents(List<IdmEntityEventDto> events) {
		List<String> niceLabels = new ArrayList<String>();
		if (events == null) {
			return niceLabels;
		}

		for (IdmEntityEventDto event : events) {
			// Only for identities
			if (event.getContent() != null && event.getContent() instanceof IdmIdentityDto) {
				Serializable id = event.getContent().getId();
				if (id != null) {
					IdmIdentityDto identityDto = identityLookup.lookup(id);
					StringBuilder niceLabel = new StringBuilder();
					if (identityDto == null) {
						niceLabel.append("DEL:");
						niceLabel.append(id);
						// Deleted
					} else {
						niceLabel.append(identityDto.getUsername());
						niceLabel.append(" (");
						niceLabel.append(identityDto.getExternalCode());
						niceLabel.append(')');
					}
					String finalNiceLabel = niceLabel.toString();
					if (!niceLabels.contains(finalNiceLabel)) {
						niceLabels.add(niceLabel.toString());
					}
				}
			}
		}
		return niceLabels;
	}

	private List<IdentityStateReportDto> getContractsStatus() {
		List<IdentityStateReportDto> result = new ArrayList<IdentityStateReportDto>();
		IdmAuditFilter filter = new IdmAuditFilter();
		filter.setFrom(lastRun);
		filter.setTill(started);
		filter.setType(IdmIdentity.class.getName());
		List<String> changedAttributesList = new ArrayList<String>();
		changedAttributesList.add(IdmIdentity_.state.getName());
		filter.setChangedAttributesList(changedAttributesList);

		List<IdmAuditDto> audits = auditService.find(filter, null).getContent();
		for (IdmAuditDto audit : audits) {
			IdentityStateReportDto reportDto = identityStateExecutor.getIdentityState(audit);
			result.add(reportDto);
		}

		return result;
	}

	private List<SyncStatusPojo> getSyncStatus() {
		List<SyncStatusPojo> statuses = new ArrayList<>();


		for (SysSystemDto system : systemService.find(null)) {
			SyncStatusPojo status = new SyncStatusPojo();
			status.systemName = system.getName();

			SysSyncConfigFilter filterSync = new SysSyncConfigFilter();
			filterSync.setSystemId(system.getId());
			List<AbstractSysSyncConfigDto> content = syncConfigService.find(filterSync, null).getContent();

			List<SystemSyncStatusPojo> systemSyncStatus = new ArrayList<>();
			for (AbstractSysSyncConfigDto sync : content) {
				if (!sync.isEnabled()) {
					continue;
				}
				SystemSyncStatusPojo syncStatus = new SystemSyncStatusPojo();
				syncStatus.syncName = sync.getName();

				// BEWARE syncLog filter hasn't filter from
				// for everytime we must load all

				SysSyncLogFilter filter = new SysSyncLogFilter();
				filter.setSynchronizationConfigId(sync.getId());
				List<SysSyncLogDto> logs = syncLongService.find(filter, new PageRequest(0, 1, new Sort(Direction.DESC,
						SysSyncLog_.created.getName()))).getContent();

				// must be only one
				SysSyncLogDto logDto = logs.get(0);

				if (logDto.isContainsError()) {
					syncStatus.containsError = true;
				}

				if (syncStatus.containsError) {
					systemSyncStatus.add(syncStatus);
				}
			}

			if (!systemSyncStatus.isEmpty()) {
				status.syncs = systemSyncStatus;
			}

			if (status.syncs != null) {
				statuses.add(status);
			}
		}

		return statuses;
	}

	private List<IdmIdentityDto> getRecipients(Object object) {
		List<IdmIdentityDto> recipients = new ArrayList<>();
		if (object != null) {
			for (String usernameOrId : object.toString().split(REGEX)) {
				IdmIdentityDto identityDto = identityLookup.lookup(usernameOrId.trim());

				if (identityDto == null) {
					LOG.error("Identity with identifier: [{}] mnot found.", usernameOrId);
					continue;
				}
				recipients.add(identityDto);
			}
		}
		return recipients;
	}

	public class ProvisioningStatusPojo {
		public String systemName = null;
		public Long error = null;
		public List<String> errorNiceLabels = null;
		public Long blocked = null;
		public List<String> blockedNiceLabels = null;
		public Long notExecuted = null;
		public List<String> notExecutedNiceLabels = null;

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

	public class LrtStatusPojo {
		public String type = null;
		public String result = null;
		public Long warningCount = null;
		public Long failedCount = null;

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

	public class EventStatusPojo {
		public Long exceptions = null;
		public List<String> exceptionsNiceLabels = null;
		public Long notExecuted = null;
		public List<String> notExecutedNiceLabels = null;
		public Long blocked = null;
		public List<String> blockedNiceLabels = null;

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

	public class SyncStatusPojo {
		public String systemName = null;
		public List<SystemSyncStatusPojo> syncs = null;

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

	public class SystemSyncStatusPojo {
		public String syncName = null;
		public boolean containsError = false;

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

	public class CompleteStatus {
		public List<ProvisioningStatusPojo> provisioning = null;
		public List<LrtStatusPojo> lrts = null;
		public EventStatusPojo events = null;
		public List<SyncStatusPojo> syncs = null;
		public String uptime = null;
		public boolean containsError = false;
		public String errorDuringSend = null;
		public List<IdentityStateReportDto> contracts = null;

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
}

