package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.test.api.AbstractNotificationTest;

public class CzechIdMStatusNotificationTaskTest extends AbstractNotificationTest {

	@Autowired
	private TestHelper testHelper;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private CzechIdMStatusNotificationTask lrt;
	@Autowired
	private SysSyncConfigService configService;
	@Autowired
	private SysSystemMappingService mappingService;

	@Before
	public void init(){
		testHelper.loginAdmin();
	}

	@After
	public void logout(){
		testHelper.logout();
	}

	@Test
	public void testSendNotification(){
		IdmIdentityDto identity = testHelper.createIdentity();
		// create setting of lrt
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(CzechIdMStatusNotificationTask.SEND_CONTRACTS_STATUS_PARAM, "true");
		configOfLRT.put(CzechIdMStatusNotificationTask.SEND_EVENT_STATUS_PARAM, "true");
		configOfLRT.put(CzechIdMStatusNotificationTask.SEND_LRT_STATUS_PARAM, "true");
		configOfLRT.put(CzechIdMStatusNotificationTask.SEND_PROVISIONING_STATUS_PARAM, "true");
		configOfLRT.put(CzechIdMStatusNotificationTask.SEND_SYNC_STATUS_PARAM, "true");
		configOfLRT.put(CzechIdMStatusNotificationTask.RECIPIENTS_PARAM, identity.getId().toString());
		lrt.init(configOfLRT);
		Boolean obj = longRunningTaskManager.executeSync(lrt);
		assertTrue(obj);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		assertEquals(OperationState.EXECUTED, task.getResult().getState());
	}

}