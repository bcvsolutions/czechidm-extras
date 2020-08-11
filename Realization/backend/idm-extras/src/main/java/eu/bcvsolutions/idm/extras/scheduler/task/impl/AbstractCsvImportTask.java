package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.domain.ResultCode;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.utils.Pair;

/**
 * Abstract LRT task for CSV importing. Out of box you will have parameters for CSV file, encoding and separator
 * CSV parsing is handled for you.
 * Just implement your own behavior what you want to do with parsed row and if you want dynamic number of attributes
 *
 * @author Roman Kucera
 */
@Component(AbstractCsvImportTask.TASK_NAME)
public abstract class AbstractCsvImportTask extends AbstractSchedulableTaskExecutor<OperationResult> {

	public static final Logger LOG = LoggerFactory.getLogger(AbstractCsvImportTask.class);

	public static final String TASK_NAME = "extras-abstract-csv-import";
	
	public static final String PARAM_PATH_TO_CSV = "importFile";
	public static final String PARAM_SEPARATOR = "separator";
	public static final String PARAM_ENCODING = "encoding";

	// Defaults
	private static final String DEFAULT_COLUMN_SEPARATOR = ";";
	private static final String DEFAULT_ENCODING = "utf-8";

	private UUID attachmentId;
	private String separator;
	private String encoding;

	@Autowired
	private AttachmentManager attachmentManager;

	/**
	 * Parse the CSV file and create data.
	 * 
	 */
	@Override
	public OperationResult process() {
		LOG.info("Processing started");
		// get data from CSV
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);

		if (StringUtils.isBlank(encoding)) {
			encoding = DEFAULT_ENCODING;
		}

		try (InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
			 Reader attachmentReader = new InputStreamReader(attachmentData, encoding);) {
			LOG.info("Reader is created");

			if (StringUtils.isBlank(separator)) {
				separator = DEFAULT_COLUMN_SEPARATOR;
			}

			// prepare format of CSV
			CSVFormat csvFormat = CSVFormat.DEFAULT
					.withFirstRecordAsHeader()
					.withDelimiter(separator.charAt(0));

			// Load CSV via parser
			try (CSVParser csvParser = csvFormat.parse(attachmentReader)) {
				List<CSVRecord> records = csvParser.getRecords();
				this.count = (long) records.size();
				this.counter = 0L;

				processRecords(records);

			} catch (IOException e) {
				LOG.error("Can't parse csv", e);
				return new OperationResult.Builder(OperationState.EXCEPTION).setCause(e).build();
			}
		} catch (IOException e) {
			LOG.error("Error occurred during input stream preparation", e);
			return new OperationResult.Builder(OperationState.EXCEPTION).setCause(e).build();
		}

		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}

	/**
	 * Process all record from CSV
	 *
	 * @param records
	 */
	protected abstract void processRecords(List<CSVRecord> records);

	/**
	 * Process dynamic attribute
	 *
	 * @param record      current row
	 * @param namePrefix  column prefix for attribute name
	 * @param valuePrefix column prefix for attribute value
	 * @param isEav       if attribute is EAV
	 */
	protected void processDynamicAttribute(CSVRecord record, String namePrefix, String valuePrefix, boolean isEav) {
		if (!StringUtils.isBlank(namePrefix) && !StringUtils.isBlank(valuePrefix)) {
			LOG.info("Prefixes set");
			int suffix = 1;
			String columnName = namePrefix + suffix;
			String columnValue = valuePrefix + suffix;
			while (record.isMapped(columnName) && record.isMapped(columnValue)) {
				LOG.info("Columns mapped: [{}] and [{}]", columnName, columnValue);
				String name = record.get(columnName);
				String value = record.get(columnValue);

				processOneDynamicAttribute(namePrefix, name, valuePrefix, value, isEav);

				++suffix;
				columnName = namePrefix + suffix;
				columnValue = valuePrefix + suffix;
			}
		}
	}
	
	/**
	 * Process pair of name and value for dynamic attribute
	 *
	 * @param namePrefix  column prefix for attribute name
	 * @param name        actual name from the column
	 * @param valuePrefix column prefix for attribute value
	 * @param value       actual value from column
	 * 
	 * @return            List of parsed pairs of name and value
	 */
	protected List<Pair<String, String>> processDynamicAttribute(CSVRecord record, String namePrefix, String valuePrefix) {
		List<Pair<String, String>> result = new ArrayList<>();
		
		if (!StringUtils.isBlank(namePrefix) && !StringUtils.isBlank(valuePrefix)) {
			LOG.info("Prefixes set");
			int suffix = 1;
			String columnName = namePrefix + suffix;
			String columnValue = valuePrefix + suffix;
			while (record.isMapped(columnName) && record.isMapped(columnValue)) {
				LOG.info("Columns mapped: [{}] and [{}]", columnName, columnValue);
				String name = record.get(columnName);
				String value = record.get(columnValue);

				result.add(new Pair<>(name, value));

				++suffix;
				columnName = namePrefix + suffix;
				columnValue = valuePrefix + suffix;
			}
		}
		
		return result;
	}

	/**
	 * Process pair of name and value for dynamic attribute
	 *
	 * @param namePrefix  column prefix for attribute name
	 * @param name        actual name from the column
	 * @param valuePrefix column prefix for attribute value
	 * @param value       actual value from column
	 * @param isEav       if attribute is EAV
	 */
	protected abstract void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value, 
			boolean isEav);

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_PATH_TO_CSV);
		separator = getParameterConverter().toString(properties, PARAM_SEPARATOR);
		encoding = getParameterConverter().toString(properties, PARAM_ENCODING);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_PATH_TO_CSV, attachmentId);
		props.put(PARAM_SEPARATOR, separator);
		props.put(PARAM_ENCODING, encoding);
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

		IdmFormAttributeDto separatorAttribute = new IdmFormAttributeDto(PARAM_SEPARATOR, PARAM_SEPARATOR,
				PersistentType.SHORTTEXT);
		separatorAttribute.setRequired(false);
		separatorAttribute.setPlaceholder("Separator of columns in CSV, default is ;");
		separatorAttribute.setDefaultValue(DEFAULT_COLUMN_SEPARATOR);

		IdmFormAttributeDto encodingAttribute = new IdmFormAttributeDto(PARAM_ENCODING, PARAM_ENCODING,
				PersistentType.SHORTTEXT);
		encodingAttribute.setRequired(false);
		encodingAttribute.setPlaceholder("Encoding of CSV, default is utf-8");
		encodingAttribute.setDefaultValue(DEFAULT_ENCODING);

		return Lists.newArrayList(csvAttachment, separatorAttribute, encodingAttribute);
	}

	protected OperationResult taskCompleted(String message, ResultCode resultCode) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(resultCode,
				ImmutableMap.of("message", message))).build();
	}

	protected OperationResult taskNotCompleted(String message, ResultCode resultCode) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(resultCode,
				ImmutableMap.of("message", message))).build();
	}
}
