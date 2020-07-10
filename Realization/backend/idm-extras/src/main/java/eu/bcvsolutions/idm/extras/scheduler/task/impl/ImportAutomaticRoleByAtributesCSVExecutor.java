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
import org.springframework.data.domain.Page;
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
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleAttributeRuleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles definitions from CSV to IdM
 *
 * @author Roman Kucera
 */
@Component
@Description("Create automatic role definitions from CSV")
public class ImportAutomaticRoleByAtributesCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleByAtributesCSVExecutor.class);

	static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	static final String PARAM_CSV_ATTACHMENT_ENCODING = "Import file encoding";
	static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	static final String PARAM_IDENTITY_ATTR_NAME_PREFIX = "Identity attribute column name prefix";
	static final String PARAM_IDENTITY_ATTR_VALUE_PREFIX = "Identity attribute column value prefix";
	static final String PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX = "Identity EAV attribute column name prefix";
	static final String PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX = "Identity EAV attribute column value prefix";
	static final String PARAM_CONTRACT_ATTR_NAME_PREFIX = "Contract attribute column name prefix";
	static final String PARAM_CONTRACT_ATTR_VALUE_PREFIX = "Contract attribute column value prefix";
	static final String PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX = "Contract EAV attribute column name prefix";
	static final String PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX = "Contract EAV attribute column value prefix";

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";

	private UUID attachmentId;
	private String encoding;
	private String rolesColumnName;
	private String columnSeparator;
	private String identityAttributeNamePrefix;
	private String identityAttributeValuePrefix;
	private String identityEavAttributeNamePrefix;
	private String identityEavAttributeValuePrefix;
	private String contractAttributeNamePrefix;
	private String contractAttributeValuePrefix;
	private String contractEavAttributeNamePrefix;
	private String contractEavAttributeValuePrefix;

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

	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		// get data from CSV
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);

		if (StringUtils.isBlank(encoding)) {
			encoding = "utf-8";
		}
		try (InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
			 Reader attachmentReader = new InputStreamReader(attachmentData, encoding);) {
			LOG.info("Reader is created");
			if (StringUtils.isBlank(columnSeparator)) {
				columnSeparator = COLUMN_SEPARATOR;
			}

			// prepare format of CSV
			CSVFormat csvFormat = CSVFormat.DEFAULT
					.withFirstRecordAsHeader()
					.withDelimiter(columnSeparator.charAt(0));

			// Load CSV via parser
			try (CSVParser csvParser = csvFormat.parse(attachmentReader)) {
				List<CSVRecord> records = csvParser.getRecords();
				this.count = (long) records.size();
				this.counter = 0L;


				records.forEach(record -> {
					List<IdmAutomaticRoleAttributeRuleDto> rules = new LinkedList<>();

					IdmAutomaticRoleAttributeDto automaticRoleAttributeDto = new IdmAutomaticRoleAttributeDto();
					IdmRoleDto roleDto = roleService.getByCode(record.get(rolesColumnName));

					automaticRoleAttributeDto.setRole(roleDto.getId());

					StringBuilder nameBuilder = new StringBuilder();
					nameBuilder.append(roleDto.getName());

					//iterate thru identity attributes and create rules from it
					prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.IDENTITY);

					//iterate thru identity EAV attributes and create rules from it
					// TODO get eav form attribute
					UUID identityId = UUID.randomUUID();
					prepareRulesEav(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.IDENTITY_EAV, identityId);

					//iterate thru contract attributes and create rules from it
					prepareRules(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.CONTRACT);

					//iterate thru contract EAV attributes and create rules from it
					// TODO get eav form attribute
					UUID contractId = UUID.randomUUID();
					prepareRulesEav(record, rules, nameBuilder, AutomaticRoleAttributeRuleType.CONTRACT_EAV, contractId);

					automaticRoleAttributeDto.setName(nameBuilder.toString());
					automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);

					final UUID automaticRoleId = automaticRoleAttributeDto.getId();

					// set id of automatic role to all rules and save it
					rules.forEach(rule -> {
						rule.setAutomaticRoleAttribute(automaticRoleId);
						automaticRoleAttributeRuleService.save(rule);
					});

					automaticRoleAttributeDto.setConcept(false);
					automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);

					++this.counter;
					this.logItemProcessed(automaticRoleAttributeDto, taskCompleted("Item was created/updated"));
				});
			} catch (IOException e) {
				LOG.error("Can't parse csv", e);
			}

		} catch (IOException e) {
			LOG.error("Error occurred during input stream preparation", e);
		}

		return null;
		//
	}

	private void prepareRules(CSVRecord record, List<IdmAutomaticRoleAttributeRuleDto> rules, StringBuilder nameBuilder, AutomaticRoleAttributeRuleType type) {
		if (!StringUtils.isBlank(identityAttributeNamePrefix) && !StringUtils.isBlank(identityAttributeValuePrefix)) {
			LOG.info("Identity attribute prefix filled, we will search and craete rule for it");
			int identityAttrSuffix = 1;
			while (record.isMapped(identityAttributeNamePrefix + identityAttrSuffix)) {
				LOG.info("Identity attribute mapped we can create rule");
				String identityAttrName = record.get(identityAttributeNamePrefix + identityAttrSuffix);
				String identityAttrValue = "";
				if (record.isMapped(identityAttributeValuePrefix + identityAttrSuffix)) {
					identityAttrValue = record.get(identityAttributeValuePrefix + identityAttrSuffix);
				}

				nameBuilder.append("|");
				nameBuilder.append(identityAttrName);
				nameBuilder.append("|");
				nameBuilder.append(identityAttrValue);

				IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule = new IdmAutomaticRoleAttributeRuleDto();
				automaticRoleAttributeRule.setType(type);
				automaticRoleAttributeRule.setAttributeName(identityAttrName);
				automaticRoleAttributeRule.setValue(identityAttrValue);
				automaticRoleAttributeRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
				rules.add(automaticRoleAttributeRule);

				++identityAttrSuffix;
			}
		}
	}

	private void prepareRulesEav(CSVRecord record, List<IdmAutomaticRoleAttributeRuleDto> rules, StringBuilder nameBuilder, AutomaticRoleAttributeRuleType type,
								 UUID attribute) {
		if (!StringUtils.isBlank(identityAttributeNamePrefix) && !StringUtils.isBlank(identityAttributeValuePrefix)) {
			LOG.info("Identity attribute prefix filled, we will search and craete rule for it");
			int identityAttrSuffix = 1;
			while (record.isMapped(identityAttributeNamePrefix + identityAttrSuffix)) {
				LOG.info("Identity attribute mapped we can create rule");
				String identityAttrName = record.get(identityAttributeNamePrefix + identityAttrSuffix);
				String identityAttrValue = "";
				if (record.isMapped(identityAttributeValuePrefix + identityAttrSuffix)) {
					identityAttrValue = record.get(identityAttributeValuePrefix + identityAttrSuffix);
				}

				nameBuilder.append("|");
				nameBuilder.append(identityAttrName);
				nameBuilder.append("|");
				nameBuilder.append(identityAttrValue);

				IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule = new IdmAutomaticRoleAttributeRuleDto();
				automaticRoleAttributeRule.setType(type);
				automaticRoleAttributeRule.setAttributeName(identityAttrName);
				automaticRoleAttributeRule.setValue(identityAttrValue);
				automaticRoleAttributeRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
				automaticRoleAttributeRule.setFormAttribute(attribute);
				rules.add(automaticRoleAttributeRule);

				++identityAttrSuffix;
			}
		}
	}

	/**
	 * Check if role has automatic attributes with requested name
	 *
	 * @param attributeName
	 * @param attributeValue
	 * @return
	 */
	private IdmAutomaticRoleAttributeRuleDto findAutomaticAttributeRule(IdmRoleDto role, String attributeName, String attributeValue) {
		IdmAutomaticRoleFilter roleFilter = new IdmAutomaticRoleFilter();
		roleFilter.setRoleId(role.getId());
		roleFilter.setRuleType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
		roleFilter.setHasRules(Boolean.TRUE);
		List<IdmAutomaticRoleAttributeDto> result = automaticRoleAttributeService.find(roleFilter, null).getContent();
		if (!result.isEmpty()) {
			IdmAutomaticRoleAttributeRuleFilter ruleFilter = new IdmAutomaticRoleAttributeRuleFilter();
			ruleFilter.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
			ruleFilter.setValue(attributeValue);
			ruleFilter.setAttributeName(attributeName);
			ruleFilter.setType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
			for (IdmAutomaticRoleAttributeDto automaticRoleAttribute : result) {
				ruleFilter.setAutomaticRoleAttributeId(automaticRoleAttribute.getId());
				Page<IdmAutomaticRoleAttributeRuleDto> rules = automaticRoleAttributeRuleService.find(ruleFilter, null);
				if (rules.getContent().size() > 0) {
					return rules.getContent().get(0);
				}
			}
		}

		return null;
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		encoding = getParameterConverter().toString(properties, PARAM_CSV_ATTACHMENT_ENCODING);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
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
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
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
		return Lists.newArrayList(csvAttachment, encodingAttr, rolesColumnNameAttribute, columnSeparatorAttribute, identityAttrNameAttribute,
				identityAttrValueAttribute, identityEavAttrNameAttribute, identityEavAttrValueAttribute, contractAttrNameAttribute,
				contractAttrValueAttribute, contractEavAttrNameAttribute, contractEavAttrValueAttribute);
	}

	private OperationResult taskCompleted(String message) {
		return new OperationResult.Builder(OperationState.EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}

	private OperationResult taskNotCompleted(String message) {
		return new OperationResult.Builder(OperationState.NOT_EXECUTED).setModel(new DefaultResultModel(ExtrasResultCode.TEST_ITEM_COMPLETED,
				ImmutableMap.of("message", message))).build();
	}

}
