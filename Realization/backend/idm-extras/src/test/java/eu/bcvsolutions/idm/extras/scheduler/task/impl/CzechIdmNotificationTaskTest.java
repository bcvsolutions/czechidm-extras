package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Service;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.entity.SysSyncLog_;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmEntityEventDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmEntityEventFilter;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmEntityEventService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.api.utils.AutowireHelper;
import eu.bcvsolutions.idm.core.model.event.IdentityEvent.IdentityEventType;
import eu.bcvsolutions.idm.core.notification.api.dto.IdmNotificationLogDto;
import eu.bcvsolutions.idm.core.notification.api.dto.filter.IdmNotificationFilter;
import eu.bcvsolutions.idm.core.notification.api.service.IdmNotificationLogService;
import eu.bcvsolutions.idm.core.notification.entity.IdmEmailLog;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.filter.IdmLongRunningTaskFilter;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractLongRunningTaskExecutor;
import eu.bcvsolutions.idm.core.scheduler.api.service.IdmLongRunningTaskService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.core.scheduler.entity.IdmLongRunningTask_;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.extras.entity.TestRoleResource;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Test for: Lrt, which removes historic workflow
 *
 * @author stloukalp
 *
 */
@Service
public class CzechIdmNotificationTaskTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper helper;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private IdmLongRunningTaskService longRunningTaskService;
	@Autowired
	private IdmEntityEventService entityEventService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private SysSyncLogService syncLogService;
	@Autowired
	private SysSyncActionLogService syncActionLogService;
	@Autowired
	private IdmNotificationLogService notificationLogService;
	@Autowired 
	private LookupService lookupService;
	//
	private static final String APPROVE_BY_HELPDESK_ENABLE = "idm.sec.core.wf.approval.helpdesk.enabled";
	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String ATTRIBUTE_LASTNAME = "lastName";
	private static final String ATTRIBUTE_FIRSTNAME = "firstName";
	private static final String SYNC_CONFIG_NAME = "syncConfigName";
	private static final String username = "myTestUser";
	private static final String firstName = "test";
	private static final String lastName = "user";
	private static final String LRT_IS_RUNNING_TOO_LONG_PARAM = "lrtIsRunningTooLongParam";
	private static final String RECIPIENTS_PARAM = "recipients";
	private static final String SEND_SYNC_STATUS_PARAM = "sendSyncStatusParam";
	private static final String SEND_LRT_STATUS_PARAM = "sendLrtStatusParam";
	private static final String SEND_EVENT_STATUS_PARAM = "sendEventStatusParam";
	private static final String TOO_MANY_EVENTS_PARAM = "tooManyEventsParam";
	//
	private static String USER_SYSTEM_NAME = "TestSystemName" + System.currentTimeMillis();

	@Before
	public void login() {
		super.loginAsAdmin();
		configurationService.setBooleanValue(APPROVE_BY_HELPDESK_ENABLE, true);
		configurationService.setBooleanValue("idm.pub.acc.enabled", Boolean.TRUE);
		configurationService.setBooleanValue("idm.pub.core.enabled", Boolean.TRUE);
		configurationService.setBooleanValue("idm.pub.extras.enabled", Boolean.TRUE);
		configurationService.setValue("idm.sec.core.status.lastRun", ZonedDateTime.now().toString());
		
		SysSystemDto userSystem = systemService.getByCode(USER_SYSTEM_NAME);
		if (userSystem == null) {
			userSystem = initData(USER_SYSTEM_NAME);
			systemService.save(userSystem);
		}
	}

	@After
	public void logout() {
		super.logout();
		
		//deleteAllResourceData();
	}

	@Test
	public void syncIsRunningTooLong() {
		//SysSystemDto system = initData(USER_SYSTEM_NAME);
		
		SysSystemDto system = systemService.getByCode(USER_SYSTEM_NAME);
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = createSyncConfig(system);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.CREATE_ENTITY, 1,
				OperationResultType.WF);
		
		moveStartTime(log, 1);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_SYNC_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(username);
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertTrue(content.get(0).getMessage().getHtmlMessage().contains("Synchronization <b>syncConfigName:</b> runs too long!!"));
		notificationLogService.delete(content.get(0));
		cleanTest(username, config);
	}
	
	@Test
	public void checkSyncIsRunningTooLongIsDisabled() {
		//SysSystemDto system = initData(USER_SYSTEM_NAME);
		
		SysSystemDto system = systemService.getByCode(USER_SYSTEM_NAME);
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = createSyncConfig(system);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.CREATE_ENTITY, 1,
				OperationResultType.WF);
		
		moveStartTime(log, 1);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, null);
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_SYNC_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(username);
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains("syncConfigName"));
		notificationLogService.delete(content.get(0));
		cleanTest(username, config);
	}
	
	@Test
	public void checkSyncDisabled() {
		//SysSystemDto system = initData(USER_SYSTEM_NAME);
		
		SysSystemDto system = systemService.getByCode(USER_SYSTEM_NAME);
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = createSyncConfig(system);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.CREATE_ENTITY, 1,
				OperationResultType.WF);
		
		moveStartTime(log, 1);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_SYNC_STATUS_PARAM, false);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(username);
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains("syncConfigName"));
		notificationLogService.delete(content.get(0));
		cleanTest(username, config);
	}
	
	@Test
	public void syncIsNotRunningTooLong() {
		//SysSystemDto system = initData(USER_SYSTEM_NAME);
		
		SysSystemDto system = systemService.getByCode(USER_SYSTEM_NAME);
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = createSyncConfig(system);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.CREATE_ENTITY, 1,
				OperationResultType.WF);
		
		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_SYNC_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(username);
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains("syncConfigName"));
		notificationLogService.delete(content.get(0));
		cleanTest(username, config);
	}
	
	@Test
	public void oneOfSyncIsRunningTooLong() {
		//SysSystemDto system = initData(USER_SYSTEM_NAME);
		
		SysSystemDto system = systemService.getByCode(USER_SYSTEM_NAME);
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = createSyncConfig(system);

		// Start sync
		helper.startSynchronization(config);

		checkSyncLog(config, SynchronizationActionType.CREATE_ENTITY, 1,
				OperationResultType.WF);
		
		//update - too long
		helper.startSynchronization(config);

		SysSyncLogDto logLongUpdate = checkSyncLog(config, SynchronizationActionType.UPDATE_ENTITY, 1,
				OperationResultType.WF);
		
		moveStartTime(logLongUpdate, 1);
		
		//update
		helper.startSynchronization(config);

		checkSyncLog(config, SynchronizationActionType.UPDATE_ENTITY, 1,
				OperationResultType.WF);

		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_SYNC_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(username);
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertTrue(content.get(0).getMessage().getHtmlMessage().contains("Synchronization <b>syncConfigName:</b> runs too long!!"));
		notificationLogService.delete(content.get(0));
		cleanTest(username, config);
	}
	
	@Test
	public void lrtIsRunningTooLong() {
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		
		LrtInfinityTest infinityTest = new LrtInfinityTest("running", (long) 0);
		longRunningTaskManager.execute(infinityTest);

		IdmLongRunningTaskFilter filterLrt = new IdmLongRunningTaskFilter();
		//filterLrt.setRunning(true);
		filterLrt.setTaskType(infinityTest.getName());
		List<IdmLongRunningTaskDto> runningTasks = longRunningTaskService.find(filterLrt, PageRequest.of(0, 1, new Sort(Direction.DESC, IdmLongRunningTask_.taskStarted.getName()))).getContent();
		Assert.assertNotEquals(0, runningTasks.size());
		runningTasks.get(0).setRunning(true);
		runningTasks.get(0).setTaskStarted(runningTasks.get(0).getTaskStarted().minusHours(1));
		longRunningTaskService.save(runningTasks.get(0));
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_LRT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertTrue(content.get(0).getMessage().getHtmlMessage().contains(infinityTest.getName()));
		notificationLogService.delete(content.get(0));
	}
	
	@Test
	public void disabledCheckOflrtIsRunningTooLong() {
		helper.createIdentity(username);
		
		LrtInfinityTest infinityTest = new LrtInfinityTest("running", (long) 0);
		longRunningTaskManager.execute(infinityTest);

		IdmLongRunningTaskFilter filterLrt = new IdmLongRunningTaskFilter();
		//filterLrt.setRunning(true);
		filterLrt.setTaskType(infinityTest.getName());
		List<IdmLongRunningTaskDto> runningTasks = longRunningTaskService.find(filterLrt, PageRequest.of(0, 1, new Sort(Direction.DESC, IdmLongRunningTask_.taskStarted.getName()))).getContent();
		Assert.assertNotEquals(0, runningTasks.size());
		runningTasks.get(0).setRunning(true);
		runningTasks.get(0).setTaskStarted(runningTasks.get(0).getTaskStarted().minusHours(1));
		longRunningTaskService.save(runningTasks.get(0));
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, null);
		properties.put(RECIPIENTS_PARAM, username);
		properties.put(SEND_LRT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient("myTestUser");
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains(infinityTest.getName()));
		notificationLogService.delete(content.get(0));
	}
	
	@Test
	public void checkOfLrtIsDisabled() {
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		
		LrtInfinityTest infinityTest = new LrtInfinityTest("running", (long) 0);
		longRunningTaskManager.execute(infinityTest);

		IdmLongRunningTaskFilter filterLrt = new IdmLongRunningTaskFilter();
		//filterLrt.setRunning(true);
		filterLrt.setTaskType(infinityTest.getName());
		List<IdmLongRunningTaskDto> runningTasks = longRunningTaskService.find(filterLrt, PageRequest.of(0, 1, new Sort(Direction.DESC, IdmLongRunningTask_.taskStarted.getName()))).getContent();
		Assert.assertNotEquals(0, runningTasks.size());
		runningTasks.get(0).setRunning(true);
		runningTasks.get(0).setTaskStarted(runningTasks.get(0).getTaskStarted().minusHours(1));
		longRunningTaskService.save(runningTasks.get(0));
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_LRT_STATUS_PARAM, false);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains(infinityTest.getName()));
		notificationLogService.delete(content.get(0));
	}
	
	@Test
	public void lrtIsNotRunningTooLong() {
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		
		LrtInfinityTest infinityTest = new LrtInfinityTest("running", (long) 0);
		longRunningTaskManager.execute(infinityTest);

		IdmLongRunningTaskFilter filterLrt = new IdmLongRunningTaskFilter();
		//filterLrt.setRunning(true);
		filterLrt.setTaskType(infinityTest.getName());
		List<IdmLongRunningTaskDto> runningTasks = longRunningTaskService.find(filterLrt, PageRequest.of(0, 1, new Sort(Direction.DESC, IdmLongRunningTask_.taskStarted.getName()))).getContent();
		Assert.assertNotEquals(0, runningTasks.size());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(LRT_IS_RUNNING_TOO_LONG_PARAM, "1");
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_LRT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains(infinityTest.getName()));
		notificationLogService.delete(content.get(0));
	}
	
	@Test
	public void tooMuchEvents() {
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", true);
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", true);
		
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		
		IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setEventType(IdentityEventType.NOTIFY.name());
		eventFilter.setStates(Lists.newArrayList(OperationState.CREATED));
		List<IdmEntityEventDto> list = entityEventService.find(eventFilter, null).getContent();
		Assert.assertTrue(3 < list.size());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(TOO_MANY_EVENTS_PARAM, "3");
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_EVENT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertTrue(content.get(0).getMessage().getHtmlMessage().contains("In IdM there are too many created events"));
		notificationLogService.delete(content.get(0));
		
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", false);
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", false);
	}
	
	@Test
	public void tooLittleEvents() {
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", true);
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", true);
		
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		
		IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setEventType(IdentityEventType.NOTIFY.name());
		eventFilter.setStates(Lists.newArrayList(OperationState.CREATED));
		List<IdmEntityEventDto> list = entityEventService.find(eventFilter, null).getContent();
		Assert.assertTrue(3 < list.size());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(TOO_MANY_EVENTS_PARAM, "100");
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_EVENT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains("In IdM there are too many created events"));
		notificationLogService.delete(content.get(0));
		
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", false);
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", false);
	}
	
	@Test
	public void disabledEvents() {
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", true);
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", true);
		
		IdmIdentityDto identity = helper.createIdentity(username + System.currentTimeMillis());
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		identityService.save(identity);
		
		IdmEntityEventFilter eventFilter = new IdmEntityEventFilter();
		eventFilter.setEventType(IdentityEventType.NOTIFY.name());
		eventFilter.setStates(Lists.newArrayList(OperationState.CREATED));
		List<IdmEntityEventDto> list = entityEventService.find(eventFilter, null).getContent();
		Assert.assertTrue(3 < list.size());
		
		Map<String, Object> properties = new HashMap<String, Object>();
		properties.put(TOO_MANY_EVENTS_PARAM, null);
		properties.put(RECIPIENTS_PARAM, identity.getUsername());
		properties.put(SEND_EVENT_STATUS_PARAM, true);
		
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
		AutowireHelper.autowire(lrt);
		lrt.init(properties);
		longRunningTaskManager.executeSync(lrt);
		
		IdmNotificationFilter filter = new IdmNotificationFilter();
		filter.setRecipient(identity.getUsername());
		filter.setNotificationType(IdmEmailLog.class);

		// we should find 1 email notification on testRecipient
		List<IdmNotificationLogDto> content = notificationLogService.find(filter, null).getContent();
		Assert.assertEquals(1, content.size());
		Assert.assertFalse(content.get(0).getMessage().getHtmlMessage().contains("In IdM there are too many created events"));
		notificationLogService.delete(content.get(0));
		
		configurationService.setBooleanValue("idm.sec.core.event.asynchronous.enabled", false);
		configurationService.setBooleanValue("idm.sec.extras.processor.test-sleep-identity-processor.enabled", false);
	}
			
	private void moveStartTime(SysSyncLogDto log, int hours) {
		log.setStarted(log.getStarted().minusHours(hours));
		syncLogService.save(log);
	}
	
	private SysSyncIdentityConfigDto createSyncConfig(SysSystemDto system) {
		SysSyncConfigFilter filter = new SysSyncConfigFilter();
		filter.setSystemId(system.getId());
		List<AbstractSysSyncConfigDto> content = syncConfigService.find(filter, null).getContent();
		if(!content.isEmpty()) {
			return (SysSyncIdentityConfigDto) content.get(0);
		}
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		return (SysSyncIdentityConfigDto) syncConfigService.save(config);
	}
	
	private void cleanTest(String username, SysSyncIdentityConfigDto config){
		identityService.delete((IdmIdentityDto) lookupService.lookupDto(IdmIdentityDto.class, username));
		
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(config.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		logs.stream().forEach(l -> syncLogService.delete(l));
	}
	
	private SysSystemDto initData(String systemName) {

		// create test system
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME, systemName);
		Assert.assertNotNull(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);
		createMapping(system, syncMapping);
		this.getBean().initIdentityData(username, firstName, lastName);
		return system;
	}
	
	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("username");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (ATTRIBUTE_LASTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_LASTNAME);
				attributeMappingTwo.setEntityAttribute(true);
				attributeMappingTwo.setName("lastName");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);

			} else if (ATTRIBUTE_FIRSTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_FIRSTNAME);
				attributeMappingTwo.setEntityAttribute(true);
				attributeMappingTwo.setName("firstName");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);

			}
		});
	}
	
	private CzechIdmNotificationTaskTest getBean() {
		return applicationContext.getAutowireCapableBeanFactory().createBean(this.getClass());
	}
	
	@Transactional
	public void initIdentityData(String username, String firstname, String lastname) {
		TestResource resourceUserOne = new TestResource();
		resourceUserOne.setName(username);
		resourceUserOne.setFirstname(firstname);
		resourceUserOne.setLastname(lastname);
		entityManager.persist(resourceUserOne);
	}
	
	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestRoleResource.TABLE_NAME);
		q.executeUpdate();
	}
	
	private SysSyncIdentityConfigDto doCreateSyncConfig(SysSystemDto system) {

		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.IDENTITY);
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto uidAttribute = attributes.stream().filter(attribute -> {
			return attribute.isUid();
		}).findFirst().orElse(null);

		// Create default synchronization config
		SysSyncIdentityConfigDto syncConfigCustom = new SysSyncIdentityConfigDto();
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setEnabled(true);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setName(SYNC_CONFIG_NAME);
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK_AND_UPDATE_ENTITY);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.DELETE_ENTITY);

		syncConfigCustom = (SysSyncIdentityConfigDto) syncConfigService.save(syncConfigCustom);

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setSystemId(system.getId());
		Assert.assertEquals(1, syncConfigService.find(configFilter, null).getTotalElements());
		return syncConfigCustom;
	}
	
	private SysSyncLogDto checkSyncLog(AbstractSysSyncConfigDto config, SynchronizationActionType actionType, int count,
			OperationResultType resultType) {
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(config.getId());
		Page<SysSyncLogDto> logs = syncLogService.find(logFilter, PageRequest.of(0, 1, new Sort(Direction.DESC, SysSyncLog_.started.getName())));
		SysSyncLogDto log = logs.get().findFirst().orElse(null);
		if (actionType == null) {
			return log;
		}

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();

		actions.stream().filter(action -> {
			return actionType == action.getSyncAction();
		}).findFirst().orElse(null);

		return log;
	}
	
	private class LrtInfinityTest extends AbstractLongRunningTaskExecutor<String> {

		private final String result;
		
		public LrtInfinityTest(String result, Long count) {
			this.result = result;
			this.count = count;
			counter = 0L;
		}

		@Override
		public String process() {
			this.counter = 0L;
			//

			count = 0L;
			while(count < 10) {
				System.currentTimeMillis();
				count ++;
				
				boolean canContinue = updateState();
				if (!canContinue) {
					return result;
				}
			}
			return result;
		}
	}

}