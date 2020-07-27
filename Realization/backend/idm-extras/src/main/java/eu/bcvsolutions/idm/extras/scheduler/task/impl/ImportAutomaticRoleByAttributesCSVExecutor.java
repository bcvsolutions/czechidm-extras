package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.LinkedList;
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
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormAttributeFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles definitions from CSV to IdM
 *
 * @author Roman Kucera
 */
@Component(ImportAutomaticRoleByAttributesCSVExecutor.TASK_NAME)
@Description("Create automatic role definitions from CSV")
public class ImportAutomaticRoleByAttributesCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleByAttributesCSVExecutor.class);

	public static final String TASK_NAME = "extras-import-automatic-roles-definitions-by-attribute";

	public static final String PARAM_CSV_ATTACHMENT = "csvFile";
	public static final String PARAM_CSV_ATTACHMENT_ENCODING = "fileEncoding";
	public static final String PARAM_ROLES_COLUMN_NAME = "rolesColumn";
	public static final String PARAM_DEFINITION_NAME_COLUMN_NAME = "definitionNameColumn";
	public static final String PARAM_COLUMN_SEPARATOR = "separator";
	public static final String PARAM_IDENTITY_ATTR_NAME_PREFIX = "identityAttributeColumnNamePrefix";
	public static final String PARAM_IDENTITY_ATTR_VALUE_PREFIX = "identityAttributeColumnValuePrefix";
	public static final String PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX = "identityEavAttributeColumnNamePrefix";
	public static final String PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX = "identityEavAttributeColumnValuePrefix";
	public static final String PARAM_CONTRACT_ATTR_NAME_PREFIX = "contractAttributeColumnNamePrefix";
	public static final String PARAM_CONTRACT_ATTR_VALUE_PREFIX = "contractAttributeColumnValuePrefix";
	public static final String PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX = "contractEavAttributeColumnNamePrefix";
	public static final String PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX = "contractEavAttributeColumnValuePrefix";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String DEFAULT_ENCODING = "utf-8";

	@Autowired
	private AttachmentManager attachmentManager;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	@Autowired
	private IdmFormAttributeService formAttributeService;

	private UUID attachmentId;
	private String encoding;
	private String rolesColumnName;
	private String definitionNameColumnName;
	private String columnSeparator;
	private String identityAttributeNamePrefix;
	private String identityAttributeValuePrefix;
	private String identityEavAttributeNamePrefix;
	private String identityEavAttributeValuePrefix;
	private String contractAttributeNamePrefix;
	private String contractAttributeValuePrefix;
	private String contractEavAttributeNamePrefix;
	private String contractEavAttributeValuePrefix;

	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		// get data from CSV
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);

		if (StringUtils.isBlank(encoding)) {
			encoding = DEFAULT_ENCODING;
		}
		try (InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
			 Reader attachmentReader = new InputStreamReader(attachmentData, encoding)) {
			LOG.info("Reader is created");
			if (StringUtils.isBlank(columnSeparator)) {
				columnSeparator = COLUMN_SEPARATOR;
			}

			// prepare format of CSV
			CSVFormat csvFormat = CSVFormat.DEFAULT
					.withFirstRecordAsHeader()
					.withDelimiter(columnSeparator.charAt(0));

			// Load CSV via parser
			parseCsv(attachmentReader, csvFormat);
		} catch (IOException e) {
			LOG.error("Error occurred during input stream preparation", e);
			return new OperationResult.Builder(OperationState.EXCEPTION).build();
		}

		return new OperationResult.Builder(OperationState.EXECUTED).build();
	}

	private void parseCsv(Reader attachmentReader, CSVFormat csvFormat) {
		try (CSVParser csvParser = csvFormat.parse(attachmentReader)) {
			List<CSVRecord> records = csvParser.getRecords();
			this.count = (long) records.size();
			this.counter = 0L;

			records.forEach(record -> {
				IdmRoleDto roleDto = roleService.getByCode(record.get(rolesColumnName));
				if(roleDto == null) {
					LOG.info("Role with code [{}] not found", record.get(rolesColumnName));
					return;
				}

				IdmAutomaticRoleAttributeDto automaticRoleAttributeDto = new IdmAutomaticRoleAttributeDto();

				automaticRoleAttributeDto.setRole(roleDto.getId());

				StringBuilder nameBuilder = new StringBuilder();
				nameBuilder.append(roleDto.getName());

				List<IdmAutomaticRoleAttributeRuleDto> rules = new LinkedList<>();
				//iterate through identity attributes and create rules from it
				prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.IDENTITY, false,
						identityAttributeNamePrefix, identityAttributeValuePrefix);

				//iterate through identity EAV attributes and create rules from it
				prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.IDENTITY_EAV, true,
						identityEavAttributeNamePrefix, identityEavAttributeValuePrefix);

				//iterate through contract attributes and create rules from it
				prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.CONTRACT, false,
						contractAttributeNamePrefix, contractAttributeValuePrefix);

				//iterate through contract EAV attributes and create rules from it
				prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.CONTRACT_EAV, true,
						contractEavAttributeNamePrefix, contractEavAttributeValuePrefix);

				String definitionName;
				if (!StringUtils.isBlank(definitionNameColumnName) && !StringUtils.isBlank(record.get(definitionNameColumnName))) {
					definitionName = record.get(definitionNameColumnName);
				} else {
					definitionName = nameBuilder.toString();
				}
				if (definitionName.length() > 255) {
					definitionName = definitionName.substring(0, 255);
				}
				automaticRoleAttributeDto.setName(definitionName);

				// Check if role definition is already exist or not
				IdmAutomaticRoleFilter automaticRoleFilter = new IdmAutomaticRoleFilter();
				automaticRoleFilter.setName(automaticRoleAttributeDto.getName());

				List<IdmAutomaticRoleAttributeDto> automaticRoles = automaticRoleAttributeService.find(automaticRoleFilter, null).getContent();
				if (automaticRoles.size() == 1) {
					automaticRoleAttributeDto = automaticRoles.get(0);
					// delete previous rules before we save the new ones
					LOG.info("Definition: [{}] found we will update it. Deleting old rules and save rules from csv", automaticRoleAttributeDto.getName());
					automaticRoleAttributeRuleService.deleteAllByAttribute(automaticRoleAttributeDto.getId());
				} else if (automaticRoles.size() > 1) {
					LOG.info("Found more then 1 definition: [{}] we will create new one, because we don't know which one should be used for " +
							"updating", automaticRoleAttributeDto.getName());
					automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);
				} else {
					LOG.info("Definition: [{}] not found, we will create it", automaticRoleAttributeDto.getName());
					automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);
				}

				final UUID automaticRoleId = automaticRoleAttributeDto.getId();

				// set id of automatic role to all rules and save it
				rules.forEach(rule -> {
					rule.setAutomaticRoleAttribute(automaticRoleId);
					IdmAutomaticRoleAttributeRuleDto savedRule = automaticRoleAttributeRuleService.save(rule);
					this.logItemProcessed(savedRule, taskCompleted("Rule created for definition - " + nameBuilder.toString()));
					++this.counter;
				});

				automaticRoleAttributeDto.setConcept(false);
				automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);
				this.count = this.counter;
			});
		} catch (IOException e) {
			LOG.error("Can't parse csv", e);
		}
	}

	private void prepareRules(CSVRecord record, List<IdmAutomaticRoleAttributeRuleDto> rules, StringBuilder nameBuilder, AutomaticRoleAttributeRuleType type,
							  boolean isEav, String attrNamePrefix, String attrValuePrefix) {
		if (!StringUtils.isBlank(attrNamePrefix) && !StringUtils.isBlank(attrValuePrefix)) {
			LOG.info("Identity attribute prefix filled, we will search and craete rule for it");
			int attrSuffix = 1;
			while (record.isMapped(attrNamePrefix + attrSuffix)) {
				LOG.info("Identity attribute mapped we can create rule");
				String attrName = record.get(attrNamePrefix + attrSuffix);
				if (!StringUtils.isBlank(attrName)) {
					String attrValue = getValue(record, attrValuePrefix, attrSuffix);

					IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule = new IdmAutomaticRoleAttributeRuleDto();
					automaticRoleAttributeRule.setType(type);
					automaticRoleAttributeRule.setAttributeName(attrName);
					automaticRoleAttributeRule.setValue(attrValue);
					automaticRoleAttributeRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);

					handleEav(nameBuilder, isEav, attrName, automaticRoleAttributeRule, type);

					nameBuilder.append(attrName);
					nameBuilder.append("-");
					nameBuilder.append(attrValue);

					rules.add(automaticRoleAttributeRule);
				}
				++attrSuffix;
			}
		}
	}

	private String getValue(CSVRecord record, String attrValuePrefix, int attrSuffix) {
		String attrValue = "";
		if (record.isMapped(attrValuePrefix + attrSuffix)) {
			attrValue = record.get(attrValuePrefix + attrSuffix);
		}
		return attrValue;
	}

	private void handleEav(StringBuilder nameBuilder, boolean isEav, String attrName, IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule,
						   AutomaticRoleAttributeRuleType type) {
		if (isEav) {
			nameBuilder.append("|EAV-");

			IdmFormAttributeFilter filter = new IdmFormAttributeFilter();
			filter.setCode(attrName);
			if (type == AutomaticRoleAttributeRuleType.IDENTITY_EAV) {
				filter.setDefinitionType(IdmIdentity.class.getName());
			} else if (type == AutomaticRoleAttributeRuleType.CONTRACT_EAV) {
				filter.setDefinitionType(IdmIdentityContract.class.getName());
			} else {
				LOG.info("Type is other type then identity EAV or identity contract EAV, filtering only by code");
			}

			List<IdmFormAttributeDto> attributes = formAttributeService.find(filter, null).getContent();
			if (!attributes.isEmpty()) {
				automaticRoleAttributeRule.setFormAttribute(attributes.get(0).getId());
			} else {
				LOG.error("EAV attribute: [{}] not found can't create rule by EAV", attrName);
			}
		} else {
			nameBuilder.append("|");
		}
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		encoding = getParameterConverter().toString(properties, PARAM_CSV_ATTACHMENT_ENCODING);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		definitionNameColumnName = getParameterConverter().toString(properties, PARAM_DEFINITION_NAME_COLUMN_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		identityAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_ATTR_NAME_PREFIX);
		identityAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_ATTR_VALUE_PREFIX);
		identityEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX);
		identityEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX);
		contractAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_ATTR_NAME_PREFIX);
		contractAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_ATTR_VALUE_PREFIX);
		contractEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX);
		contractEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_CSV_ATTACHMENT_ENCODING, encoding);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_DEFINITION_NAME_COLUMN_NAME, definitionNameColumnName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_IDENTITY_ATTR_NAME_PREFIX, identityAttributeNamePrefix);
		props.put(PARAM_IDENTITY_ATTR_VALUE_PREFIX, identityAttributeValuePrefix);
		props.put(PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX, identityEavAttributeNamePrefix);
		props.put(PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX, identityEavAttributeValuePrefix);
		props.put(PARAM_CONTRACT_ATTR_NAME_PREFIX, contractAttributeNamePrefix);
		props.put(PARAM_CONTRACT_ATTR_VALUE_PREFIX, contractAttributeValuePrefix);
		props.put(PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, contractEavAttributeNamePrefix);
		props.put(PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, contractEavAttributeValuePrefix);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		IdmFormAttributeDto encodingAttr = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT_ENCODING, PARAM_CSV_ATTACHMENT_ENCODING,
				PersistentType.SHORTTEXT);
		encodingAttr.setRequired(true);
		encodingAttr.setDefaultValue(DEFAULT_ENCODING);
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto definitionNameColumnNameAttribute = new IdmFormAttributeDto(PARAM_DEFINITION_NAME_COLUMN_NAME, PARAM_DEFINITION_NAME_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		// params for colums for definition rules
		IdmFormAttributeDto identityAttrNameAttribute = new IdmFormAttributeDto(PARAM_IDENTITY_ATTR_NAME_PREFIX, PARAM_IDENTITY_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		identityAttrNameAttribute.setRequired(false);

		IdmFormAttributeDto identityAttrValueAttribute = new IdmFormAttributeDto(PARAM_IDENTITY_ATTR_VALUE_PREFIX, PARAM_IDENTITY_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		identityAttrValueAttribute.setRequired(false);

		IdmFormAttributeDto identityEavAttrNameAttribute = new IdmFormAttributeDto(PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX, PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		identityEavAttrNameAttribute.setRequired(false);

		IdmFormAttributeDto identityEavAttrValueAttribute = new IdmFormAttributeDto(PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX, PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		identityEavAttrValueAttribute.setRequired(false);

		IdmFormAttributeDto contractAttrNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_ATTR_NAME_PREFIX, PARAM_CONTRACT_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		contractAttrNameAttribute.setRequired(false);

		IdmFormAttributeDto contractAttrValueAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_ATTR_VALUE_PREFIX, PARAM_CONTRACT_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		contractAttrValueAttribute.setRequired(false);

		IdmFormAttributeDto contractEavAttrNameAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		contractEavAttrNameAttribute.setRequired(false);

		IdmFormAttributeDto contractEavAttrValueAttribute = new IdmFormAttributeDto(PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		contractEavAttrValueAttribute.setRequired(false);
		//
		return Lists.newArrayList(csvAttachment, encodingAttr, rolesColumnNameAttribute, definitionNameColumnNameAttribute, columnSeparatorAttribute,
				identityAttrNameAttribute, identityAttrValueAttribute, identityEavAttrNameAttribute, identityEavAttrValueAttribute, contractAttrNameAttribute,
				contractAttrValueAttribute, contractEavAttrNameAttribute, contractEavAttrValueAttribute);
	}

	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}

}
