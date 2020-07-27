package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmCodeListItemFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.eav.entity.IdmCodeListItem;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * Task which will create/update defined code list and it's items from CSV file
 *
 * @author Roman Kucera
 */

@Component(ImportCodeListFromCSV.TASK_NAME)
@Description("Task which will create/update defined code list and it's items from CSV file")
public class ImportCodeListFromCSV extends AbstractCsvImportTask {

	public static final String TASK_NAME = "extras-import-codelist";

	public static final Logger LOG = LoggerFactory.getLogger(ImportCodeListFromCSV.class);
	public static final String PARAM_CODE_CODE_LIST = "codeOfCodelist";
	public static final String PARAM_NAME_CODE_LIST = "nameOfCodelist";
	public static final String PARAM_DESCRIPTION_CODE_LIST = "descriptionOfCodelist";
	public static final String PARAM_EAV_ATTR_NAME_PREFIX = "eavAttributeNamePrefix";
	public static final String PARAM_EAV_ATTR_VALUE_PREFIX = "eavAttributeValuePrefix";

	private String code;
	private String name;
	private String description;
	private String eavAttributeNamePrefix;
	private String eavAttributeValuePrefix;

	private IdmCodeListItemDto codeListItemDto;

	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private FormService formService;

	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	protected void processRecords(List<CSVRecord> records) {
		// load or create Code list
		LOG.info("We will load or create code list");
		IdmCodeListDto codeListDto = codeListService.getByCode(code);
		if (codeListDto == null) {
			LOG.info("Creating new code list");
			codeListDto = new IdmCodeListDto();
		}
		codeListDto.setCode(code);
		codeListDto.setName(name);
		codeListDto.setDescription(description);
		codeListDto = codeListService.save(codeListDto);
		LOG.info("Code list saved with values from LRT config");

		UUID codeListUuid = codeListDto.getId();

		records.forEach(record -> {
			String key = record.get("key");
			String value = record.get("value");
			LOG.info("Processing row [{}]", record.toString());

			codeListItemDto = new IdmCodeListItemDto();
			LOG.info("Try to find existing item");
			IdmCodeListItemFilter codeListItemFilter = new IdmCodeListItemFilter();
			codeListItemFilter.setCode(key);
			codeListItemFilter.setCodeListId(codeListUuid);
			List<IdmCodeListItemDto> items = codeListItemService.find(codeListItemFilter, null).getContent();

			if (items.size() > 1) {
				LOG.info("Found more then one item with code [{}] skipping", key);
				++this.counter;
				this.logItemProcessed(items.get(0), taskNotCompleted("Found more then one item with code " + key + " skipping", ExtrasResultCode.IMPORT_CODE_LIST_ERROR));
				return;
			}
			if (!items.isEmpty()) {
				LOG.info("Item already exists, we will update it");
				codeListItemDto = items.get(0);
			}

			codeListItemDto.setCode(key);
			codeListItemDto.setName(value);
			codeListItemDto.setCodeList(codeListUuid);
			codeListItemDto = codeListItemService.save(codeListItemDto);
			LOG.info("Code list item [{}] saved", key);

			processDynamicAttribute(record, eavAttributeNamePrefix, eavAttributeValuePrefix);

			++this.counter;
			this.logItemProcessed(codeListItemDto, taskCompleted("Item was created/updated", ExtrasResultCode.IMPORT_CODE_LIST_EXECUTED));
		});
	}

	@Override
	protected void processOneDynamicAttribute(String name, String value) {
		IdmFormDefinitionDto formDef = formDefinitionService.findOneByTypeAndCode(IdmCodeListItem.class.getName(), code);
		if (formDef != null) {
			IdmFormAttributeDto attribute = formAttributeService.findAttribute(IdmCodeListItem.class.getName(), code, name);
			createAndSaveEav(codeListItemDto, name, value, formDef, attribute);
		} else {
			LOG.info("No EAV definitions was found, can't create EAV");
		}
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		code = getParameterConverter().toString(properties, PARAM_CODE_CODE_LIST);
		name = getParameterConverter().toString(properties, PARAM_NAME_CODE_LIST);
		description = getParameterConverter().toString(properties, PARAM_DESCRIPTION_CODE_LIST);
		eavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_EAV_ATTR_NAME_PREFIX);
		eavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_EAV_ATTR_VALUE_PREFIX);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CODE_CODE_LIST, code);
		props.put(PARAM_NAME_CODE_LIST, name);
		props.put(PARAM_DESCRIPTION_CODE_LIST, description);
		props.put(PARAM_EAV_ATTR_NAME_PREFIX, eavAttributeNamePrefix);
		props.put(PARAM_EAV_ATTR_VALUE_PREFIX, eavAttributeValuePrefix);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();

		IdmFormAttributeDto codeCodeListAttribute = new IdmFormAttributeDto(PARAM_CODE_CODE_LIST, PARAM_CODE_CODE_LIST,
				PersistentType.SHORTTEXT);
		codeCodeListAttribute.setRequired(true);
		codeCodeListAttribute.setPlaceholder("Code of code list which will be created/updated");

		IdmFormAttributeDto nameCodeListAttribute = new IdmFormAttributeDto(PARAM_NAME_CODE_LIST, PARAM_NAME_CODE_LIST,
				PersistentType.SHORTTEXT);
		nameCodeListAttribute.setRequired(true);
		nameCodeListAttribute.setPlaceholder("Name of code list which will be created/updated");

		IdmFormAttributeDto descriptionCodeListAttribute = new IdmFormAttributeDto(PARAM_DESCRIPTION_CODE_LIST, PARAM_DESCRIPTION_CODE_LIST,
				PersistentType.SHORTTEXT);
		descriptionCodeListAttribute.setRequired(false);
		descriptionCodeListAttribute.setPlaceholder("Description of code list which will be created/updated");

		IdmFormAttributeDto eavAttributeNameAttribute = new IdmFormAttributeDto(PARAM_EAV_ATTR_NAME_PREFIX, PARAM_EAV_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		eavAttributeNameAttribute.setRequired(false);
		eavAttributeNameAttribute.setPlaceholder("Prefix of EAV attribute name column");

		IdmFormAttributeDto eavAttributeValueAttribute = new IdmFormAttributeDto(PARAM_EAV_ATTR_VALUE_PREFIX, PARAM_EAV_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		eavAttributeValueAttribute.setRequired(false);
		eavAttributeValueAttribute.setPlaceholder("Prefix of EAV attribute value column");

		formAttributes.addAll(Lists.newArrayList(codeCodeListAttribute, nameCodeListAttribute, descriptionCodeListAttribute, eavAttributeNameAttribute,
				eavAttributeValueAttribute));
		return formAttributes;
	}

	private void createAndSaveEav(IdmCodeListItemDto codeListItemDto, String eavName, String eavValue, IdmFormDefinitionDto formDef, IdmFormAttributeDto attribute) {
		if (attribute == null) {
			LOG.info("EAV doesn't exists, we will create it");
			IdmFormAttributeDto formAttributeDto = new IdmFormAttributeDto(eavName, eavName);
			formAttributeDto.setFormDefinition(formDef.getId());
			formAttributeService.save(formAttributeDto);
			LOG.info("Save value now");
			saveEav(codeListItemDto.getId(), formDef, eavValue, eavName);
		} else {
			LOG.info("EAV already exists, just save value");
			saveEav(codeListItemDto.getId(), formDef, eavValue, eavName);
		}
	}

	private void saveEav(UUID entityId, IdmFormDefinitionDto formDefinition, String value, String attributeName) {
		List<String> values = new ArrayList<>();
		values.add(value);
		formService.saveValues(entityId, IdmCodeListItem.class, formDefinition, attributeName, Lists.newArrayList(values));
	}
}
