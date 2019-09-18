package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.*;

import java.time.chrono.ChronoZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import org.joda.time.DateTime;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;

public class CzechIdMStatusNotificationTaskTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper testHelper;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private ConfigurationService configurationService;

	@Test
	public void testSendNotification(){
		configurationService.setValue(CzechIdMStatusNotificationTask.LAST_RUN_DATE_TIME, DateTime.now().minusDays(10).toString());
		IdmIdentityDto identity = testHelper.createIdentity();
		CzechIdMStatusNotificationTask lrt = new CzechIdMStatusNotificationTask();
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
	}

}