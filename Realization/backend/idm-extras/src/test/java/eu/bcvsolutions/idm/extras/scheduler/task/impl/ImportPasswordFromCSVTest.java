package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmPasswordDto;
import eu.bcvsolutions.idm.core.api.service.IdmPasswordService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.test.api.TestHelper;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Peter Štrunc
 */
@Transactional
public class ImportPasswordFromCSVTest extends AbstractCsvImportTaskTest {

	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importPassword.csv";

	@Autowired
	private IdmPasswordService passwordService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private TestHelper testHelper;


	@Test
	public void testImportPassword() {
		IdmAttachmentDto attachment = createAttachment(PATH, "importPassword.csv");

		IdmIdentityDto identity = testHelper.createIdentity("testImportPasswordUser1");
		IdmIdentityDto identity2 = testHelper.createIdentity("testImportPasswordUser2");

		IdmPasswordDto pwd1 = passwordService.findOrCreateByIdentity(identity.getCode());
		pwd1.setValidFrom(LocalDate.EPOCH);
		passwordService.save(pwd1);

		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportIdMPasswordFromCSV.PARAM_PATH_TO_CSV, attachment.getId());
		configOfLRT.put(ImportIdMPasswordFromCSV.PARAM_USERNAME_COLUMN_NAME, "username");
		configOfLRT.put(ImportIdMPasswordFromCSV.PARAM_DATE_FORMAT, "yyyyMMdd");

		ImportIdMPasswordFromCSV lrt = new ImportIdMPasswordFromCSV();
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		IdmLongRunningTaskDto task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Long count = task.getCount();
		Long total = 3L;
		Assert.assertEquals(total, count);

		pwd1 = passwordService.findOneByIdentity(identity.getUsername());
		IdmPasswordDto pwd2 = passwordService.findOneByIdentity(identity2.getUsername());

		Assert.assertNotNull(pwd1);
		Assert.assertNotNull(pwd2);

		// valid from was not changed
		Assert.assertEquals(pwd1.getValidFrom(), LocalDate.EPOCH);
		Assert.assertEquals(pwd1.getValidTill(), LocalDate.of(2020,2,3));
		Assert.assertEquals(pwd2.getValidTill(), LocalDate.of(2020,2,4));
		Assert.assertTrue(pwd1.isMustChange());
		Assert.assertFalse(pwd2.isMustChange());



	}
}