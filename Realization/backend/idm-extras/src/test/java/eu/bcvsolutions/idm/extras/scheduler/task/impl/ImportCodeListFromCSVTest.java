package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmProfileDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Roman Kucera
 */
@Transactional
public class ImportCodeListFromCSVTest extends AbstractIntegrationTest {
	
	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importCodeListTestFileCreate.csv";

	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private AttachmentManager attachmentManager;

	@Test
	public void testImportCreateCodeList() {
		IdmAttachmentDto attachment = createAttachment(PATH, "importCodeListTestFileCreate.csv");

		// create
		String code = "Code create";
		String name = "Name create";
		String description = "Description create";

		Map<String, Object> configOfLRT = new HashMap<>();
		configOfLRT.put(ImportCodeListFromCSV.PARAM_PATH_TO_CSV, attachment.getId());
		configOfLRT.put(ImportCodeListFromCSV.PARAM_CODE_CODE_LIST, code);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_NAME_CODE_LIST, name);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_DESCRIPTION_CODE_LIST, description);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_SEPARATOR, ";");

		ImportCodeListFromCSV lrt = new ImportCodeListFromCSV();
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
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		IdmCodeListDto codeListDto = codeListService.getByCode(code);
		assertNotNull(codeListDto);
		assertEquals(code, codeListDto.getCode());
		assertEquals(name, codeListDto.getName());
		assertEquals(description, codeListDto.getDescription());

		// update
		String nameUpdate = "Name update";
		String descriptionUpdate = "Description update";

		configOfLRT = new HashMap<>();
		configOfLRT.put(ImportCodeListFromCSV.PARAM_PATH_TO_CSV, attachment.getId());
		configOfLRT.put(ImportCodeListFromCSV.PARAM_CODE_CODE_LIST, code);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_NAME_CODE_LIST, nameUpdate);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_DESCRIPTION_CODE_LIST, descriptionUpdate);
		configOfLRT.put(ImportCodeListFromCSV.PARAM_SEPARATOR, ";");

		lrt = new ImportCodeListFromCSV();
		lrt.init(configOfLRT);
		longRunningTaskManager.executeSync(lrt);
		task = longRunningTaskManager.getLongRunningTask(lrt);
		while (task.isRunning()) {
			try {
				Thread.sleep(20);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		count = task.getCount();
		total = 3L;
		assertEquals(task.getCounter(), count);
		assertEquals(total, count);

		IdmCodeListDto codeListDtoUpdated = codeListService.getByCode(code);
		assertNotNull(codeListDtoUpdated);
		assertEquals(code, codeListDtoUpdated.getCode());
		assertEquals(nameUpdate, codeListDtoUpdated.getName());
		assertEquals(descriptionUpdate, codeListDtoUpdated.getDescription());
	}

	private IdmAttachmentDto createAttachment(String path, String name) {
		File file = new File(path);
		DataInputStream stream = null;
		try {
			stream = new DataInputStream(new FileInputStream(file));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		Assert.assertNotNull(stream);
		eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto attachment = new IdmAttachmentDto();
		attachment.setInputData(stream);
		attachment.setName(name);
		attachment.setMimetype("text/csv");
		//
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmProfileDto profile = getHelper().createProfile(identity);
		return attachmentManager.saveAttachment(profile, attachment);

	}
}