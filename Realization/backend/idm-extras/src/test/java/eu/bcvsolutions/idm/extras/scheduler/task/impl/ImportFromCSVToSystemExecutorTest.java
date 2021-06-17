package eu.bcvsolutions.idm.extras.scheduler.task.impl;


import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;

/**
 * Basic test for virtual system importing
 *
 * @author Peter Sourek <peter.sourek@bcvsolutions.eu>
 */
public class ImportFromCSVToSystemExecutorTest extends AbstractCsvImportTaskTest {

	private static final String FILE_PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importTestFile.csv";

	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;

	@Before
	public void init() {
		loginAsAdmin();
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void importDataDBTest() {
		// create system
		SysSystemDto system = initData();
		Assert.assertNotNull(system);
		//
		ImportFromCSVToSystemExecutor lrt = new ImportFromCSVToSystemExecutor();
		// create attachment
		IdmAttachmentDto attachment = createAttachment(FILE_PATH, "importTestFile.csv");
		// create setting of lrt
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_MULTIVALUED_SEPARATOR, ",");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_NAME_ATTRIBUTE, "NAME");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_UID_ATTRIBUTE, "NAME");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_SYSTEM_NAME, system.getName());
		configOfLRT.put(AbstractCsvImportTask.PARAM_PATH_TO_CSV, attachment.getId());
		lrt.init(configOfLRT);

		longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		Long count = task.getCount();
		Long total = 3L;
		Assert.assertEquals(task.getCounter(), count);
		Assert.assertEquals(total, count);
		//delete system
		systemService.delete(system);
	}

	private SysSystemDto initData() {

		// create test system
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME, null, "status", "NAME", null);
		Assert.assertNotNull(system);

		// generate schema for system
		systemService.generateSchema(system);
		return system;

	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}
}