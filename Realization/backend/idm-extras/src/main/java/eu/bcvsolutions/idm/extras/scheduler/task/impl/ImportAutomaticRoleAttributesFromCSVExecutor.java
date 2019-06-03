package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

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
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmAutomaticRoleAttributeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmAutomaticRoleAttributeRuleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles definitions from CSV to IdM
 *
 * @author Petr Hanák
 */
@Component
@Description("Create automatic role definitons from CSV")
public class ImportAutomaticRoleAttributesFromCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportAutomaticRoleAttributesFromCSVExecutor.class);
	
	private static final String PARAM_CSV_FILE_PATH = "Path to file";
	private static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	private static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	private static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	private static final String PARAM_COLUMN_FIRST_ATTRIBUTE = "Column with first attribute";
	private static final String PARAM_FIRST_CONTRACT_EAV_NAME = "First contract eav name";
	private static final String PARAM_COLUMN_SECOND_ATTRIBUTE = "Column with second attribute";
	private static final String PARAM_SECOND_CONTRACT_EAV_NAME = "Second contract eav name";
	
	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator

	private String pathToFile;
	private String rolesColumnName;
	private String columnSeparator;
	private String multiValueSeparator;
	private String firstAttributeColumnName;
	private String firstContractEavAttributeName;
	private String secondAttributeColumnName;
	private String secondContractEavAttributeName;
	private Boolean hasSecondAttribute;
	
	@Autowired
	private DefaultIdmRoleService roleService;
	@Autowired
	private IdmAutomaticRoleAttributeService automaticRoleAttributeService;
	@Autowired
	private DefaultIdmAutomaticRoleAttributeRuleService automaticRoleAttributeRuleService;
	
	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		validatePathToFile();
		Map<String, List<String>> roleAttributeValues = parseCSV();

		if (!roleAttributeValues.isEmpty()) {
			this.count = (long) roleAttributeValues.size();
			this.counter = 0L;

			for (String roleName : roleAttributeValues.keySet()) {
				System.out.println(roleAttributeValues.get(roleName));
				IdmRoleDto role = roleService.getByCode(roleNameToCode(roleName));
				
				if (role != null) {
					List<String> attributeValues = roleAttributeValues.get(roleName);
					// Handle first attribute
					String firstAttributeValue = attributeValues.get(0);
					IdmAutomaticRoleAttributeRuleDto firstAutomaticRoleAttributeRule = findAutomaticAttributeRule(role, firstContractEavAttributeName, firstAttributeValue);
					// Create automatic role
					IdmAutomaticRoleAttributeDto automaticRoleAttribute = createAutomaticRoleAttribute(role, firstContractEavAttributeName, attributeValues);
					if (firstAutomaticRoleAttributeRule == null) {
						createAutomaticRoleAttributeRule(automaticRoleAttribute, firstContractEavAttributeName, firstAttributeValue);
					}
					
					// Handle second attribute
					if (hasSecondAttribute) {
						String secondAttributeValue = attributeValues.get(1);
						IdmAutomaticRoleAttributeRuleDto secondAutomaticRoleAttributeRule = findAutomaticAttributeRule(role, secondContractEavAttributeName, secondAttributeValue);
						if (secondAutomaticRoleAttributeRule == null) {
							createAutomaticRoleAttributeRule(automaticRoleAttribute, secondContractEavAttributeName, secondAttributeValue);
						}
					}
					this.logItemProcessed(role, taskCompleted("Automatic role: " + automaticRoleAttribute.getName() + " created"));
				} else {
					throw new ResultCodeException(ExtrasResultCode.ROLE_NOT_FOUND, ImmutableMap.of("role name", roleName));
				}
				
				++this.counter;
				if (!this.updateState()) {
					break;
				}
			}
		} else {
			throw new ResultCodeException(ExtrasResultCode.ROLES_NOT_FOUND);
		}
		//
		return new OperationResult.Builder(OperationState.CREATED).build();
	}
	
	/**
	 * Create automatic role
	 * 
	 * @param role
	 * @param attributeName
	 * @param attributeValue
	 * @return
	 */
	private IdmAutomaticRoleAttributeDto createAutomaticRoleAttribute(IdmRoleDto role, String attributeName, List<String> attributeValues) {
		IdmAutomaticRoleAttributeDto automaticRoleAttribute = new IdmAutomaticRoleAttributeDto();
		if (hasSecondAttribute) {
			automaticRoleAttribute.setName(role.getName() + " | " + attributeValues.get(0) + " | " + attributeValues.get(1));
		} else {
			automaticRoleAttribute.setName(role.getName() + " | " + attributeValues.get(0));
		}
		automaticRoleAttribute.setRole(role.getId());
		return automaticRoleAttributeService.save(automaticRoleAttribute);
	}

	/**
	 * Create automatic role attribute rule
	 * 
	 * @param automaticRoleAttribute
	 * @param attributeName
	 * @param attributeValue
	 */
	private void createAutomaticRoleAttributeRule(IdmAutomaticRoleAttributeDto automaticRoleAttribute, String attributeName, String attributeValue) {
		IdmAutomaticRoleAttributeRuleDto automaticRoleAttributeRule = new IdmAutomaticRoleAttributeRuleDto();
		automaticRoleAttributeRule.setAutomaticRoleAttribute(automaticRoleAttribute.getId());
		automaticRoleAttributeRule.setType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
		automaticRoleAttributeRule.setAttributeName(attributeName);
		automaticRoleAttributeRule.setValue(attributeValue);
		automaticRoleAttributeRuleService.save(automaticRoleAttributeRule);
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
		roleFilter.setHasRules(true);
		Page<IdmAutomaticRoleAttributeDto> result = automaticRoleAttributeService.find(roleFilter, null);
		if (result.getContent().size() > 0) {
			IdmAutomaticRoleAttributeDto automaticRoleAttribute = result.getContent().get(0);
			IdmAutomaticRoleAttributeRuleFilter ruleFilter = new IdmAutomaticRoleAttributeRuleFilter();
			ruleFilter.setAutomaticRoleAttributeId(automaticRoleAttribute.getId());
			ruleFilter.setComparison(AutomaticRoleAttributeRuleComparison.EQUALS);
			ruleFilter.setValue(attributeValue);
			ruleFilter.setAttributeName(attributeName);
			ruleFilter.setType(AutomaticRoleAttributeRuleType.CONTRACT_EAV);
			Page<IdmAutomaticRoleAttributeRuleDto> rules = automaticRoleAttributeRuleService.find(ruleFilter, null);
			if (rules.getContent().size() > 0) {
				return rules.getContent().get(0);
			}
		}
		
		return null;
	}
	
	/**
	 * this method parse CSV file - read selected CSV column and return list of roles
	 * 
	 * @return
	 */
	private Map<String, List<String>> parseCSV() {
		CSVParser parser = new CSVParserBuilder().withEscapeChar(CSVParser.DEFAULT_ESCAPE_CHARACTER).withQuoteChar('"')
				.withSeparator(columnSeparator.charAt(0)).build();
		CSVReader reader = null;
		try {
			reader = new CSVReaderBuilder(new FileReader(pathToFile)).withCSVParser(parser).build();
			String[] header = reader.readNext();
			
			// find number of column with role name
			int roleColumnNumber = findColumnNumber(header, rolesColumnName);
			if (roleColumnNumber == -1) {
				throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", rolesColumnName));
			}
			// find number of column with first attribute
			int firstAttributeColumnNumber = findColumnNumber(header, firstAttributeColumnName);
			if (firstAttributeColumnNumber == -1) {
				throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", firstAttributeColumnName));
			}
			//	find number of column with description name
			int secondAttributeColumnNumber = -1;
			if (hasSecondAttribute) {
				secondAttributeColumnNumber = findColumnNumber(header, secondAttributeColumnName);
				if (secondAttributeColumnNumber == -1) {
					throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", secondAttributeColumnName));
				}
			}

			Map<String, List<String>> roleAttributeValues = new HashMap<>();
			for (String[] line : reader) {
				String[] roles = line[roleColumnNumber].split(multiValueSeparator);
				String firstAttributeValue = line[firstAttributeColumnNumber];
				
				if (hasSecondAttribute) {
					String secondAttributeValue = line[secondAttributeColumnNumber];
					for (String role : roles) {
						if (role.length() > 0) {
							roleAttributeValues.put(role, Arrays.asList(firstAttributeValue, secondAttributeValue));
						}
					}
				} else {
					for (String role : roles) {
						if (role.length() > 0) {
							roleAttributeValues.put(role, Arrays.asList(firstAttributeValue, ""));
						}
					}
				}
			}
			return roleAttributeValues;
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	/**
	 * Check if path to CSV file is valid
	 */
	private void validatePathToFile() {
		File fl = new File(pathToFile);
		if (!fl.canRead()) {
			throw new ResultCodeException(ExtrasResultCode.IMPORT_CANT_READ_FILE_PATH,
					ImmutableMap.of("path", pathToFile));
		}
	}
	
	/**
	 * finds number of column we need for name and code of role
	 * 
	 * @param header
	 * @return
	 */
	private int findColumnNumber(String[] header, String columnName) {
		int counterHeader = 0;
		for (String item : header){
			if(item.equals(columnName)){
				return counterHeader;
			}
			counterHeader++;
		}
		return -1;
	}
	
	/**
	 * Gets role name and return role code
	 * 
	 * @param roleName
	 * @return
	 */
	private String roleNameToCode(String roleName) {
		// role code should not contain spaces
		return roleName.replace(' ', '_');
	}
	
	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		pathToFile = getParameterConverter().toString(properties, PARAM_CSV_FILE_PATH);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		firstAttributeColumnName = getParameterConverter().toString(properties, PARAM_COLUMN_FIRST_ATTRIBUTE);
		firstContractEavAttributeName = getParameterConverter().toString(properties, PARAM_FIRST_CONTRACT_EAV_NAME);
		secondAttributeColumnName = getParameterConverter().toString(properties, PARAM_COLUMN_SECOND_ATTRIBUTE);
		secondContractEavAttributeName = getParameterConverter().toString(properties, PARAM_SECOND_CONTRACT_EAV_NAME);
		// if not filled, init multiValueSeparator and check if csv has description		
		if (multiValueSeparator == null) {
			multiValueSeparator = MULTI_VALUE_SEPARATOR;
		}
		if (secondAttributeColumnName != null && secondContractEavAttributeName != null) {
			hasSecondAttribute = true;
		} else {
			hasSecondAttribute = false;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_FILE_PATH, pathToFile);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_COLUMN_FIRST_ATTRIBUTE, firstAttributeColumnName);
		props.put(PARAM_FIRST_CONTRACT_EAV_NAME, firstContractEavAttributeName);
		props.put(PARAM_COLUMN_SECOND_ATTRIBUTE, secondAttributeColumnName);
		props.put(PARAM_SECOND_CONTRACT_EAV_NAME, secondContractEavAttributeName);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto pathToFileAttribute = new IdmFormAttributeDto(PARAM_CSV_FILE_PATH, PARAM_CSV_FILE_PATH,
				PersistentType.SHORTTEXT);
		pathToFileAttribute.setRequired(true);
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setRequired(false);
		multiValueSeparatorAttribute.setPlaceholder("default is new line");
		IdmFormAttributeDto columnFirstAttrAttribute = new IdmFormAttributeDto(PARAM_COLUMN_FIRST_ATTRIBUTE, PARAM_COLUMN_FIRST_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		columnFirstAttrAttribute.setRequired(true);
		IdmFormAttributeDto columnFirstContractEavAttribute = new IdmFormAttributeDto(PARAM_FIRST_CONTRACT_EAV_NAME, PARAM_FIRST_CONTRACT_EAV_NAME,
				PersistentType.SHORTTEXT);
		columnFirstContractEavAttribute.setRequired(true);
		IdmFormAttributeDto columnSecondAttrAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SECOND_ATTRIBUTE, PARAM_COLUMN_SECOND_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		columnSecondAttrAttribute.setRequired(false);
		IdmFormAttributeDto columnSecondContractEavAttribute = new IdmFormAttributeDto(PARAM_SECOND_CONTRACT_EAV_NAME, PARAM_SECOND_CONTRACT_EAV_NAME,
				PersistentType.SHORTTEXT);
		columnSecondContractEavAttribute.setRequired(false);
		//
		return Lists.newArrayList(pathToFileAttribute, rolesColumnNameAttribute, columnSeparatorAttribute, multiValueSeparatorAttribute, columnFirstAttrAttribute, columnFirstContractEavAttribute, columnSecondAttrAttribute, columnSecondContractEavAttribute);
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
