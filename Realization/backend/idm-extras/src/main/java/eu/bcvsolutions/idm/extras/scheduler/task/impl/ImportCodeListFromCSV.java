package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListItemDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmCodeListItemFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListItemService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmCodeListService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * Task which will create/update defined code list and it's items from CSV file
 *
 * @author Roman Kucera
 */

@Component
@Description("Task which will create/update defined code list and it's items from CSV file")
public class ImportCodeListFromCSV extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportCodeListFromCSV.class);
	private static final String PARAM_PATH_TO_CSV = "CSV with values for import";
	private static final String PARAM_CODE_CODE_LIST = "Code of code list";
	private static final String PARAM_NAME_CODE_LIST = "Name of code list";
	private static final String PARAM_DESCRIPTION_CODE_LIST = "Description of code list";
	private static final String PARAM_SEPARATOR = "Separator of csv file";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";

	private UUID attachmentId;
	private String code;
	private String name;
	private String description;
	private String separator;

	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private IdmCodeListItemService codeListItemService;

	@Override
	public OperationResult process() {
		LOG.info("Processing started");
		// get data from CSV
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);

		InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);

		LOG.info("Attachment is prepared, now transform to reader");

		Reader attachmentReader = new InputStreamReader(attachmentData);

		LOG.info("Reade is created");

		if (StringUtils.isBlank(separator)) {
			separator = COLUMN_SEPARATOR;
		}

		// prepare format of CSV
		CSVFormat csvFormat = CSVFormat.DEFAULT
				.withFirstRecordAsHeader()
				.withDelimiter(separator.charAt(0));

		// Load CSV via parser
		try (CSVParser csvParser = csvFormat.parse(attachmentReader)) {
			AtomicBoolean isNewCodeList = new AtomicBoolean(false);

			this.count = (long) csvParser.getRecords().size();
			this.counter = 0L;

			// load or create Code list
			LOG.info("We will load or create code list");
			IdmCodeListDto codeListDto = codeListService.getByCode(code);
			if (codeListDto == null) {
				LOG.info("Creating new code list");
				codeListDto = new IdmCodeListDto();
				isNewCodeList.set(true);
			}
			codeListDto.setCode(code);
			codeListDto.setName(name);
			codeListDto.setDescription(description);
			codeListDto = codeListService.save(codeListDto);
			LOG.info("Code list saved with values from LR config");

			UUID codeListUuid = codeListDto.getId();

			csvParser.getRecords().forEach(record -> {
				String key = record.get("key");
				String value = record.get("value");
				LOG.info("Processing row [{}]", record.toString());

				IdmCodeListItemDto codeListItemDto = new IdmCodeListItemDto();
				if (!isNewCodeList.get()) {
					LOG.info("Try to find existing item");
					IdmCodeListItemFilter codeListItemFilter = new IdmCodeListItemFilter();
					codeListItemFilter.setCode(key);
					codeListItemFilter.setCodeListId(codeListUuid);
					List<IdmCodeListItemDto> items = codeListItemService.find(codeListItemFilter, null).getContent();

					if (items.size() > 1) {
						LOG.info("Found more then one item with code [{}] skipping", key);
						++this.counter;
						this.logItemProcessed(items.get(0), taskNotCompleted("Found more then one same items"));
						return;
					}
					if (!items.isEmpty()) {
						LOG.info("Item already exists, we will update it");
						codeListItemDto = items.get(0);
					}
				}

				codeListItemDto.setCode(key);
				codeListItemDto.setName(value);
				codeListItemDto.setCodeList(codeListUuid);
				codeListItemService.save(codeListItemDto);
				LOG.info("Code list item [{}] saved", key);

				++this.counter;
				this.logItemProcessed(codeListItemDto, taskCompleted("Item was created/updated"));
			});
		} catch (IOException e) {
			e.printStackTrace();
		}


		return null;
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_PATH_TO_CSV);
		code = getParameterConverter().toString(properties, PARAM_CODE_CODE_LIST);
		name = getParameterConverter().toString(properties, PARAM_NAME_CODE_LIST);
		description = getParameterConverter().toString(properties, PARAM_DESCRIPTION_CODE_LIST);
		separator = getParameterConverter().toString(properties, PARAM_SEPARATOR);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_PATH_TO_CSV, attachmentId);
		props.put(PARAM_CODE_CODE_LIST, code);
		props.put(PARAM_NAME_CODE_LIST, name);
		props.put(PARAM_DESCRIPTION_CODE_LIST, description);
		props.put(PARAM_SEPARATOR, separator);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		// csv file attachment
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_PATH_TO_CSV, PARAM_PATH_TO_CSV, PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		if (attachmentId != null) {
			String attachmentIdString = attachmentId.toString();
			csvAttachment.setDefaultValue(attachmentIdString);
			csvAttachment.setPlaceholder(attachmentIdString);
		}
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
		descriptionCodeListAttribute.setRequired(true);
		descriptionCodeListAttribute.setPlaceholder("Description of code list which will be created/updated");

		IdmFormAttributeDto separatorAttribute = new IdmFormAttributeDto(PARAM_SEPARATOR, PARAM_SEPARATOR,
				PersistentType.SHORTTEXT);
		separatorAttribute.setRequired(true);
		separatorAttribute.setPlaceholder("Separator of columns in CSV");

		return Lists.newArrayList(csvAttachment, codeCodeListAttribute, nameCodeListAttribute, descriptionCodeListAttribute, separatorAttribute);
	}

	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.IMPORT_CODE_LIST_EXECUTED,
				ImmutableMap.of("message", message))).build();
	}

	private OperationResult taskNotCompleted(String message) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.IMPORT_CODE_LIST_ERROR,
				ImmutableMap.of("message", message))).build();
	}
}
