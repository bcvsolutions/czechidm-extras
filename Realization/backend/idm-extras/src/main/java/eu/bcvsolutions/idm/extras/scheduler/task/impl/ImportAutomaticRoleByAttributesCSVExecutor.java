package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleComparison;
import eu.bcvsolutions.idm.core.api.domain.AutomaticRoleAttributeRuleType;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmAutomaticRoleAttributeRuleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmAutomaticRoleFilter;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormAttributeFilter;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles definitions from CSV to IdM
 *
 * @author Roman Kucera
 */
@Component(ImportAutomaticRoleByAttributesCSVExecutor.TASK_NAME)
@Description("Create automatic role definitions from CSV")
public class ImportAutomaticRoleByAttributesCSVExecutor extends AbstractCsvImportTask {

	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleByAttributesCSVExecutor.class);

	public static final String TASK_NAME = "extras-import-automatic-roles-definitions-by-attribute";

	public static final String PARAM_ROLES_COLUMN_NAME = "rolesColumn";
	public static final String PARAM_DEFINITION_NAME_COLUMN_NAME = "definitionNameColumn";
	public static final String PARAM_IDENTITY_ATTR_NAME_PREFIX = "identityAttributeColumnNamePrefix";
	public static final String PARAM_IDENTITY_ATTR_VALUE_PREFIX = "identityAttributeColumnValuePrefix";
	public static final String PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX = "identityEavAttributeColumnNamePrefix";
	public static final String PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX = "identityEavAttributeColumnValuePrefix";
	public static final String PARAM_CONTRACT_ATTR_NAME_PREFIX = "contractAttributeColumnNamePrefix";
	public static final String PARAM_CONTRACT_ATTR_VALUE_PREFIX = "contractAttributeColumnValuePrefix";
	public static final String PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX = "contractEavAttributeColumnNamePrefix";
	public static final String PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX = "contractEavAttributeColumnValuePrefix";

	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private IdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	@Autowired
	private IdmFormAttributeService formAttributeService;

	private String rolesColumnName;
	private String definitionNameColumnName;
	private String identityAttributeNamePrefix;
	private String identityAttributeValuePrefix;
	private String identityEavAttributeNamePrefix;
	private String identityEavAttributeValuePrefix;
	private String contractAttributeNamePrefix;
	private String contractAttributeValuePrefix;
	private String contractEavAttributeNamePrefix;
	private String contractEavAttributeValuePrefix;

	private StringBuilder nameBuilder;
	private List<IdmAutomaticRoleAttributeRuleDto> rules;
	private Map<String, AutomaticRoleAttributeRuleType> types = new HashMap<>();

	@Override
	public String getName() {
		return TASK_NAME;
	}

	@Override
	public void processRecords(List<CSVRecord> records) {
		records.forEach(record -> {
			IdmRoleDto roleDto = roleService.getByCode(record.get(rolesColumnName));
			if (roleDto == null) {
				LOG.info("Role with code [{}] not found", record.get(rolesColumnName));
				return;
			}

			IdmAutomaticRoleAttributeDto automaticRoleAttributeDto = new IdmAutomaticRoleAttributeDto();

			automaticRoleAttributeDto.setRole(roleDto.getId());

			nameBuilder = new StringBuilder();
			nameBuilder.append(roleDto.getName());

			rules = new LinkedList<>();
			List<CoreException> exceptions = new ArrayList<>();

			//iterate through identity attributes and create rules from it
			exceptions.addAll(processDynamicAttribute(record, identityAttributeNamePrefix, identityAttributeValuePrefix, false));

			//iterate through identity EAV attributes and create rules from it
			exceptions.addAll(processDynamicAttribute(record, identityEavAttributeNamePrefix, identityEavAttributeValuePrefix, true));

			//iterate through contract attributes and create rules from it
			exceptions.addAll(processDynamicAttribute(record, contractAttributeNamePrefix, contractAttributeValuePrefix, false));

			//iterate through contract EAV attributes and create rules from it
			exceptions.addAll(processDynamicAttribute(record, contractEavAttributeNamePrefix, contractEavAttributeValuePrefix, true));

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

			if (!exceptions.isEmpty()) {
				IdmAutomaticRoleAttributeDto finalAutomaticRoleAttributeDto = automaticRoleAttributeDto;
				exceptions.forEach(e -> {
					this.logItemProcessed(finalAutomaticRoleAttributeDto, taskNotCompleted(e.getMessage(), ExtrasResultCode.AUTO_ROLE_ITEM_ERROR));
				});
			}

			final UUID automaticRoleId = automaticRoleAttributeDto.getId();

			// set id of automatic role to all rules and save it
			rules.forEach(rule -> {
				rule.setAutomaticRoleAttribute(automaticRoleId);
				IdmAutomaticRoleAttributeRuleDto savedRule = automaticRoleAttributeRuleService.save(rule);
				this.logItemProcessed(savedRule, taskCompleted("Rule created for definition - " + nameBuilder.toString(), ExtrasResultCode.AUTO_ROLE_ITEM_COMPLETED));
				++this.counter;
			});

			automaticRoleAttributeDto.setConcept(false);
			automaticRoleAttributeDto = automaticRoleAttributeService.save(automaticRoleAttributeDto);
			this.count = this.counter;
		});
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix,
											  String value, boolean isEav) {
		if (!StringUtils.isBlank(name)) {

			AutomaticRoleAttributeRuleType type = types.getOrDefault(namePrefix, null);

			IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule = new IdmAutomaticRoleAttributeRuleDto();
			automaticRoleAttributeRule.setType(type);
			automaticRoleAttributeRule.setAttributeName(name);
			automaticRoleAttributeRule.setValue(value);
			automaticRoleAttributeRule.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);

			handleEav(nameBuilder, isEav, name, automaticRoleAttributeRule, type);

			nameBuilder.append(name);
			nameBuilder.append('-');
			nameBuilder.append(value);

			rules.add(automaticRoleAttributeRule);
		}
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
				throw new CoreException("Type is other type then identity EAV or identity contract EAV, filtering only by code");
			}

			List<IdmFormAttributeDto> attributes = formAttributeService.find(filter, null).getContent();
			if (!attributes.isEmpty()) {
				automaticRoleAttributeRule.setFormAttribute(attributes.get(0).getId());
			} else {
				LOG.error("EAV attribute: [{}] not found can't create rule by EAV", attrName);
				throw new CoreException(String.format("EAV attribute: [%s] not found can't create rule by EAV", attrName));
			}
		} else {
			nameBuilder.append('|');
		}
	}

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		definitionNameColumnName = getParameterConverter().toString(properties, PARAM_DEFINITION_NAME_COLUMN_NAME);
		identityAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_ATTR_NAME_PREFIX);
		identityAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_ATTR_VALUE_PREFIX);
		identityEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_EAV_ATTR_NAME_PREFIX);
		identityEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_IDENTITY_EAV_ATTR_VALUE_PREFIX);
		contractAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_ATTR_NAME_PREFIX);
		contractAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_ATTR_VALUE_PREFIX);
		contractEavAttributeNamePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_NAME_PREFIX);
		contractEavAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_CONTRACT_EAV_ATTR_VALUE_PREFIX);

		prepareTypeMap(identityAttributeNamePrefix, AutomaticRoleAttributeRuleType.IDENTITY);
		prepareTypeMap(identityEavAttributeNamePrefix, AutomaticRoleAttributeRuleType.IDENTITY_EAV);
		prepareTypeMap(contractAttributeNamePrefix, AutomaticRoleAttributeRuleType.CONTRACT);
		prepareTypeMap(contractEavAttributeNamePrefix, AutomaticRoleAttributeRuleType.CONTRACT_EAV);
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_DEFINITION_NAME_COLUMN_NAME, definitionNameColumnName);
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
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();

		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto definitionNameColumnNameAttribute = new IdmFormAttributeDto(PARAM_DEFINITION_NAME_COLUMN_NAME, PARAM_DEFINITION_NAME_COLUMN_NAME,
				PersistentType.SHORTTEXT);
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
		formAttributes.addAll(Lists.newArrayList(rolesColumnNameAttribute, definitionNameColumnNameAttribute, identityAttrNameAttribute, identityAttrValueAttribute, identityEavAttrNameAttribute, identityEavAttrValueAttribute, contractAttrNameAttribute,
				contractAttrValueAttribute, contractEavAttrNameAttribute, contractEavAttrValueAttribute));
		return formAttributes;
	}

	private void prepareTypeMap(String field, AutomaticRoleAttributeRuleType type) {
		if (!StringUtils.isBlank(field)) {
			types.put(field, type);
		}
	}

}
