package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmCodeListItemFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.scheduler.api.dto.IdmLongRunningTaskDto;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;

/**
 * @author Roman Kucera
 */
@Transactional
public class CsvImportTaskTest extends AbstractCsvImportTaskTest {

	private static final String PATH = System.getProperty("user.dir") + "/src/test/resources/scheduler/task/impl/importCodeListTestFileCreate.csv";

	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private LongRunningTaskManager longRunningTaskManager;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;

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
		configOfLRT.put(ImportCodeListFromCSV.PARAM_EAV_ATTR_NAME_PREFIX, "attName");
		configOfLRT.put(ImportCodeListFromCSV.PARAM_EAV_ATTR_VALUE_PREFIX, "attValue");

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

		IdmCodeListItemFilter itemFilter = new IdmCodeListItemFilter();
		itemFilter.setCodeListId(codeListDto.getId());
		List<IdmCodeListItemDto> items = codeListItemService.find(itemFilter, null).getContent();
		assertNotNull(items);
		assertEquals(3, items.size());

		IdmCodeListItemDto firstItemFromFile = items.stream().filter(idmCodeListItemDto -> idmCodeListItemDto.getCode().equals("klic")).findFirst().orElse(null);
		assertNotNull(firstItemFromFile);

		IdmFormDefinitionDto formDef = formDefinitionService.findOneByTypeAndCode(IdmCodeListItem.class.getName(), codeListDto.getCode());
		assertNotNull(formDef);

		List<IdmFormValueDto> values = formService.getValues(firstItemFromFile, formDef);
		assertNotNull(values);
		assertEquals("eavHodnota", values.get(0).getShortTextValue());

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
}