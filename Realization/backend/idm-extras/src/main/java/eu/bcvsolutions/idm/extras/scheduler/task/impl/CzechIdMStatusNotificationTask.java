package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.quartz.DisallowConcurrentExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;
import com.google.common.primitives.Ints;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
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
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.CompleteStatus;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.EventStatusPojo;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.LrtStatusPojo;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.ProvisioningStatusPojo;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.SyncStatusPojo;
import eu.bcvsolutions.idm.extras.scheduler.task.impl.pojo.SystemSyncStatusPojo;

/**
 * LRT send status about state of CzechIdM
 * TODO: select specific system
 * TODO: add selectbox for identities
 *
 * @author Ondrej Kopr
 * @author Marek Klement
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
	private static String LRT_IS_RUNNING_TOO_LONG_PARAM = "lrtIsRunningTooLongParam";
	private static String TOO_MANY_EVENTS_PARAM = "tooManyEventsParam";
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
	private ZonedDateTime lastRun = null;
	private ZonedDateTime started = null;
	private List<IdmIdentityDto> recipients = null;
	private Integer runningTooLong = 0;
	private Integer numberOfEvents = 0;

	@Override
	public void init(Map<String, Object> properties) {
		super.init(properties);

		// we must set started there
		started = ZonedDateTime.now();

//		systems = new ArrayList<>();
		String lastRunAsString = configurationService.getValue(LAST_RUN_DATE_TIME);

		// first run, default run - 10days
		if (lastRunAsString == null) {
			lastRunAsString = ZonedDateTime.now().minusDays(10).toString();
		}
		lastRun = ZonedDateTime.parse(lastRunAsString);
		//
		sendProvisioningStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_PROVISIONING_STATUS_PARAM));
		sendSyncStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_SYNC_STATUS_PARAM));
		sendLrtStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_LRT_STATUS_PARAM));
		sendEventStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_EVENT_STATUS_PARAM));
		sendContractsStatus = BooleanUtils.toBoolean(getParameterConverter().toBoolean(properties, SEND_CONTRACTS_STATUS_PARAM));
		recipients = getRecipients(properties.get(RECIPIENTS_PARAM));
		runningTooLong = Optional.ofNullable((String)properties.get(LRT_IS_RUNNING_TOO_LONG_PARAM))
				 .map(Ints::tryParse).orElse(-1);
		numberOfEvents = Optional.ofNullable((String)properties.get(TOO_MANY_EVENTS_PARAM))
				 .map(Ints::tryParse).orElse(-1);
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
		props.put(LRT_IS_RUNNING_TOO_LONG_PARAM, runningTooLong);
		props.put(TOO_MANY_EVENTS_PARAM, numberOfEvents);
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
		attributes.add(createIntAttribute(LRT_IS_RUNNING_TOO_LONG_PARAM, "Number of hours for LRTs running too long"));
		attributes.add(createIntAttribute(TOO_MANY_EVENTS_PARAM, "Number of maximum events"));
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
	
	private IdmFormAttributeDto createIntAttribute(String face, String name) {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto(
				face,
				name,
				PersistentType.INT);
		attribute.setFaceType(null);
		return attribute;
	}

	@Override
	public Boolean process() {
		CompleteStatus status = new CompleteStatus();
		status.setContainsError(false);
		
		RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
		status.setUptime(String.valueOf((runtimeMXBean.getUptime() / (1000 * 60 * 60 * 24))));

		try {

			if (sendProvisioningStatus) {
				status.setProvisioning(getProvisioningStatus());
				if (!status.getProvisioning().isEmpty()) {
					status.setContainsError(true);
				}
			}
			
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.setErrorDuringSend(e.getMessage());
		}
		
		try {

			if (sendLrtStatus) {
				status.setLrts(getLrtStatus());
				// by default and with wrong values it is disabled
				if(runningTooLong > 0) {
					status.setLrtRunningTooLong(getLongRunningLrt());
				}
				if (!status.getLrts().isEmpty() || (status.getLrtRunningTooLong() != null && !status.getLrtRunningTooLong().isEmpty())) {
					status.setContainsError(true);
				}
			}
			
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.setErrorDuringSend(e.getMessage());
		}
		
		try {

			if (sendEventStatus) {
				status.setEvents(getEventStatus());
				// by default and with wrong values it is disabled
				if(numberOfEvents > 0) {
					status.setNumberOfEvents(getNumberOfTooManyEvents());
				}
				if (status.getEvents() != null || status.getNumberOfEvents() != null) {
					status.setContainsError(true);
				}
			}
			
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.setErrorDuringSend(e.getMessage());
		}
		
		try {

			if (sendSyncStatus) {
				status.setSyncs(getSyncStatus());
				if (!status.getSyncs().isEmpty()) {
					status.setContainsError(true);
				}
			}
			
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.setErrorDuringSend(e.getMessage());
		}
			
		try {

			if (sendContractsStatus) {
				status.setContracts(getContractsStatus());
			}

			
		} catch (Exception e) {
			LOG.error("Error during send CzechIdM status.", e);
			status.setErrorDuringSend(e.getMessage());
		}
		
		notificationManager.send(ExtrasModuleDescriptor.TOPIC_STATUS,
				new IdmMessageDto.Builder()
						.setLevel(NotificationLevel.INFO)
						.addParameter("status", status)
						.build(),
				recipients);
		
		if (status.getErrorDuringSend() != null) {
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
			status.setSystemName(system.getName());

			SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
			filter.setSystemId(system.getId());

			filter.setResultState(OperationState.EXCEPTION);
			List<SysProvisioningOperationDto> list = provisioningOperationService.find(filter, null).getContent();

			if (!list.isEmpty()) {
				status.setError((long) list.size());
				status.setErrorNiceLabels(getIdentityNiceLabelForOperations(list));
			}

			filter.setResultState(OperationState.BLOCKED);
			list = provisioningOperationService.find(filter, null).getContent();
			if (!list.isEmpty()) {
				status.setBlocked((long) list.size());
				status.setBlockedNiceLabels(getIdentityNiceLabelForOperations(list));
			}

			filter.setResultState(OperationState.NOT_EXECUTED);
			list = provisioningOperationService.find(filter, null).getContent();
			if (!list.isEmpty()) {
				status.setNotExecuted((long) list.size());
				status.setNotExecutedNiceLabels(getIdentityNiceLabelForOperations(list));
			}

			if (status.getBlocked() != null || status.getError() != null || status.getNotExecuted() != null) {
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
					if (!StringUtils.isBlank(identityDto.getExternalCode())) {
						niceLabel.append(" (");
						niceLabel.append(identityDto.getExternalCode());
						niceLabel.append(')');
					}
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
			status.setType(task.getTaskType());
			// dry run skip
			if (task.isDryRun()) {
				continue;
			}

			if (task.getResultState() == OperationState.EXCEPTION) {
				status.setResult(OperationState.EXCEPTION.name());
			} else if (task.getResultState() == OperationState.CANCELED) {
				status.setResult(OperationState.CANCELED.name());
			} else if (task.getResultState() == OperationState.NOT_EXECUTED) {
				status.setResult(OperationState.NOT_EXECUTED.name());
			}

			if (status.getResult() == null) {
				continue;
			}

			status.setFailedCount(task.getFailedItemCount());
			status.setWarningCount(task.getWarningItemCount());

			if (status.getResult() != null || status.getFailedCount() != null || status.getWarningCount() != null) {
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
			status.setExceptions((long) list.size());
			status.setExceptionsNiceLabels(getIdentityNiceLabelForEvents(list));
		}

		filter.setStates(Lists.newArrayList(OperationState.NOT_EXECUTED));
		list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty()) {
			status.setNotExecuted((long) list.size());
			status.setNotExecutedNiceLabels(getIdentityNiceLabelForEvents(list));
		}

		filter.setStates(Lists.newArrayList(OperationState.BLOCKED));
		list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty()) {
			status.setBlocked((long) list.size());
			status.setBlockedNiceLabels(getIdentityNiceLabelForEvents(list));
		}

		if (status.getBlocked() != null || status.getExceptions() != null || status.getNotExecuted() != null) {
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
						if (!StringUtils.isBlank(identityDto.getExternalCode())) {
							niceLabel.append(" (");
							niceLabel.append(identityDto.getExternalCode());
							niceLabel.append(')');
						}
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
		IdmAuditFilter filter = new IdmAuditFilter();
		filter.setFrom(lastRun);
		filter.setTill(started);
		filter.setType(IdmIdentity.class.getName());
		List<String> changedAttributesList = new ArrayList<String>();
		changedAttributesList.add(IdmIdentity_.state.getName());
		filter.setChangedAttributesList(changedAttributesList);

		List<IdmAuditDto> audits = auditService.find(filter, null).getContent();
		
		List<IdentityStateReportDto> result = new ArrayList<>((int) (audits.size() / 0.75));
		
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
			status.setSystemName(system.getName());

			SysSyncConfigFilter filterSync = new SysSyncConfigFilter();
			filterSync.setSystemId(system.getId());
			List<AbstractSysSyncConfigDto> content = syncConfigService.find(filterSync, null).getContent();

			List<SystemSyncStatusPojo> systemSyncStatus = new ArrayList<>();
			for (AbstractSysSyncConfigDto sync : content) {
				if (!sync.isEnabled()) {
					continue;
				}
				SystemSyncStatusPojo syncStatus = new SystemSyncStatusPojo();
				syncStatus.setSyncName(sync.getName());

				SysSyncLogFilter filter = new SysSyncLogFilter();
				filter.setSynchronizationConfigId(sync.getId());
				filter.setFrom(lastRun);
				List<SysSyncLogDto> logs = syncLongService.find(filter, null).getContent();

				// if synchronization did not run yet
				if (logs.isEmpty()) {
					continue;
				}
				
				List<SysSyncLogDto> collectLogsWithErrors = logs.stream().filter(l -> l.isContainsError()).collect(Collectors.toList());
				List<SysSyncLogDto> collectLogsRunningTooLong = null;
				
				// by default and with wrong values it is disabled
				if(runningTooLong > 0) {
					collectLogsRunningTooLong = logs.stream().filter(l -> hasSyncRunTooLong(l)).collect(Collectors.toList());
				}
				
				// synchronization was running too long
				if(collectLogsRunningTooLong != null && !collectLogsRunningTooLong.isEmpty()) {
					syncStatus.setRunningTooLong(true);
				}
				// sync contains error
				if (!collectLogsWithErrors.isEmpty()) {
					syncStatus.setContainsError(true);
				}

				if (syncStatus.isContainsError() || syncStatus.isRunningTooLong()) {
					systemSyncStatus.add(syncStatus);
				}
			}

			if (!systemSyncStatus.isEmpty()) {
				status.setSyncs(systemSyncStatus);
			}

			if (status.getSyncs() != null) {
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
	
	/**
	 * Return list of lrts, which are running too long, based on parameter 'runningTooLong'
	 * @return
	 */
	private List<LrtStatusPojo> getLongRunningLrt() {
		IdmLongRunningTaskFilter filter = new IdmLongRunningTaskFilter();
		filter.setRunning(true);
		List<IdmLongRunningTaskDto> runningTasks = longRunningTaskService.find(filter, null).getContent();
		
		List<LrtStatusPojo> runningTasksRunningTooLong = new ArrayList<LrtStatusPojo>();
		for(IdmLongRunningTaskDto task : runningTasks) {
			if(task.getTaskStarted() != null && task.getTaskStarted().isBefore(started.minusHours(runningTooLong))) {
				LrtStatusPojo lrt = new LrtStatusPojo();
				lrt.setType(task.getTaskType());
				lrt.setResult(task.getTaskDescription());
				runningTasksRunningTooLong.add(lrt);
			}
		}
		return runningTasksRunningTooLong;
	}
	
	/**
	 * If there are too many events in CREATED state, methods return number of CREATED events else null
	 * @return
	 */
	private Integer getNumberOfTooManyEvents() {
		IdmEntityEventFilter filter = new IdmEntityEventFilter();

		filter.setStates(Lists.newArrayList(OperationState.CREATED));
		List<IdmEntityEventDto> list = entityEventService.find(filter, null).getContent();
		if (!list.isEmpty() && list.size() > numberOfEvents) {
			return list.size();
		}
		return null;
	}
	
	/**
	 * Returns true if synchronization is running or runned too long, otherwise returns false
	 * @param logDto
	 * @return
	 */
	private boolean hasSyncRunTooLong(SysSyncLogDto logDto) {
		if(logDto.isRunning()) {
			return ZonedDateTime.now().minusHours(runningTooLong).isAfter(logDto.getStarted());
		} else {
			return logDto.getEnded().minusHours(runningTooLong).isAfter(logDto.getStarted());
		}
	}

}

