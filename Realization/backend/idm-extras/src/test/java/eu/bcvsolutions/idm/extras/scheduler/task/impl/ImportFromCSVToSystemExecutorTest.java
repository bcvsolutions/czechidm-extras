package eu.bcvsolutions.idm.extras.scheduler.task.impl;


import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * //TODO: Write class description
 *
 * @author Peter Sourek <peter.sourek@bcvsolutions.eu>
 */
public class ImportFromCSVToSystemExecutorTest extends AbstractIntegrationTest {

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
		// create setting of lrt
		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_ATTRIBUTE_SEPARATOR, ";");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_CSV_FILE_PATH, FILE_PATH);
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_MULTIVALUED_SEPARATOR, ",");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_NAME_ATTRIBUTE, "NAME");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_UID_ATTRIBUTE, "NAME");
		configOfLRT.put(ImportFromCSVToSystemExecutor.PARAM_SYSTEM_NAME, system.getName());
		lrt.init(configOfLRT);
		Boolean obj = longRunningTaskManager.executeSync(lrt);
		Assert.assertNotNull(obj);
		Assert.assertTrue(obj);
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
		SysSystemDto system = helper.createSystem(TestResource.TABLE_NAME, null, "status", "NAME");
		Assert.assertNotNull(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		return system;

	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}
}