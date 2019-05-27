package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.impl.DefaultSysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.impl.DefaultSysRoleSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.model.service.impl.DefaultIdmRoleService;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;

/**
 * This LRT imports all roles from CSV to IdM
 * Creates catalog with name of the connected system
 * Adds role system mapped attribute for merge
 * Fills / updates description
 * Updates can be requested
 *
 * @author Marek Klement
 * @author Petr Hanák
 */
@Component
@Description("Get all roles on mapping - system and import from CSV to IDM")
public class ImportRolesFromCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportRolesFromCSVExecutor.class);

	private static final String PARAM_CSV_FILE_PATH = "Path to file";
	private static final String PARAM_SYSTEM_NAME = "System name";
	private static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	private static final String PARAM_DESCRIPTION_COLUMN_NAME = "Column with description";
	private static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	private static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	private static final String PARAM_MEMBER_OF_ATTRIBUTE = "MemberOf attribute name";
	private static final String PARAM_CAN_BE_REQUESTED = "Can be requested";	

	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator
	private static final String MEMBER_OF_ATTRIBUTE = "rights";
	private static final Boolean CAN_BE_REQUESTED = true;
	private static final String OBJECT_CLASSNAME = "__ACCOUNT__";

	private String pathToFile;
	private String systemName;
	private String rolesColumnName;
	private String descriptionColumnName;
	private String columnSeparator;
	private String multiValueSeparator;
	private String memberOfAttribute;
	private Boolean canBeRequested;
	private Boolean hasDescription;

	@Autowired
	private SysSystemService sysSystemService;
	@Autowired
	private DefaultIdmRoleService roleService;
	@Autowired
	private DefaultIdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private DefaultIdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private DefaultSysRoleSystemService roleSystemService;
	@Autowired
	private DefaultSysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysSystemMappingService mappingService;

	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		validatePathToFile();
		SysSystemDto system = findSystem();
		Map<String, String> roleDescriptions = parseCSV();

		if (!roleDescriptions.isEmpty()) {
			this.count = (long) roleDescriptions.size();
			this.counter = 0L;

			UUID catalogueId = createCatalogue(systemName);
			for (String roleName : roleDescriptions.keySet()) {
				IdmRoleDto role = roleService.getByCode(roleNameToCode(roleName));
				if (role == null) {
					role = createRole(roleName, system, roleDescriptions.get(roleName));
					if (!roleIsInCatalogue(role.getId(), catalogueId)) {
						addRoleToCatalogue(role, catalogueId);
					}
				} else {
					updateRole(role, roleDescriptions.get(roleName));
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

	private void validatePathToFile() {
		File fl = new File(pathToFile);
		if (!fl.canRead()) {
			throw new ResultCodeException(ExtrasResultCode.IMPORT_CANT_READ_FILE_PATH,
					ImmutableMap.of("path", pathToFile));
		}
	}

	/**
	 * @return
	 */
	private SysSystemDto findSystem() {
		SysSystemFilter systemFilter = new SysSystemFilter();
		systemFilter.setCodeableIdentifier(systemName);
		List<SysSystemDto> systems = sysSystemService.find(systemFilter, null).getContent();
		//
		if (systems.isEmpty()) {
			throw new ResultCodeException(ExtrasResultCode.SYSTEM_NAME_NOT_FOUND,
					ImmutableMap.of("system", systemName));
		}
		return systems.get(0);
	}

	/**
	 * this method parse CSV file - read selected CSV column and return list of roles
	 * @return
	 */
	private Map<String, String> parseCSV() {
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
			//	find number of column with description name
			int descriptionColumnNumber = -1;
			if (hasDescription) {
				descriptionColumnNumber = findColumnNumber(header, descriptionColumnName);
				if (descriptionColumnNumber == -1) {
					throw new ResultCodeException(ExtrasResultCode.COLUMN_NOT_FOUND, ImmutableMap.of("column name", descriptionColumnName));
				}
			}

			Map<String, String> roleDescriptions = new HashMap<>();
			for (String[] line : reader) {
				String[] roles = line[roleColumnNumber].split(multiValueSeparator);
				if (hasDescription) {					
					String description = line[descriptionColumnNumber];
					for (String role : roles) {
						if (role.length() > 0) {
							roleDescriptions.put(role, description);
						}
					}
				} else {
					for (String role : roles) {
						if (role.length() > 0) {
							roleDescriptions.put(role, "");
						}
					}
				}
			}
			return roleDescriptions;
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
	 * finds number of column we need for name and code of role
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
	 * creates roles from column given in input
	 * assigns system to role
	 * @param roleName
	 * @param system
	 * @return
	 */
	private IdmRoleDto createRole(String roleName, SysSystemDto system, String description) {
		IdmRoleDto role = new IdmRoleDto();
		role.setCode(roleNameToCode(roleName));
		role.setName(roleName);
		role.setPriority(0);
		role.setCanBeRequested(canBeRequested);
		if (hasDescription) {
			role.setDescription(description);			
		}
		role = roleService.save(role);

		SysSystemMappingDto systemMapping = getSystemMapping(system);
		SysRoleSystemDto roleSystem = new SysRoleSystemDto();
		roleSystem.setSystem(system.getId());
		roleSystem.setRole(role.getId());
		roleSystem.setSystemMapping(systemMapping.getId());
		roleSystemService.save(roleSystem);

		String transformationScript = MessageFormat.format("\"{0}\"", roleName);		
		roleSystemAttributeService.addRoleMappingAttribute(system.getId(), role.getId(), memberOfAttribute, transformationScript, OBJECT_CLASSNAME);

		this.logItemProcessed(role, taskCompleted("Role " + roleName + " created"));
		return role;
	}

	/**
	 * Gets role name and return role code
	 * @param roleName
	 * @return
	 */
	private String roleNameToCode(String roleName) {
		// role code should not contain spaces
		return roleName.replace(' ', '_');
	}

	/**
	 * Updates canBeRequested and description
	 * @param role
	 */
	private void updateRole(IdmRoleDto role, String description) {
		Boolean canBeReqUpdated = false, descriptionUpdated = false;
		if (role.isCanBeRequested() != canBeRequested) {
			role.setCanBeRequested(canBeRequested);
			canBeReqUpdated = true;
		}
		if (!role.getDescription().equals(description)) {
			role.setDescription(description);
			descriptionUpdated = true;
		}
		if (canBeReqUpdated || descriptionUpdated) {
			role = roleService.save(role);
			this.logItemProcessed(role, taskCompleted("Role " + role.getName() + " updated"));			
		} else {
			this.logItemProcessed(role, taskNotCompleted("Nothing to update! Role name: " + role.getName()));
		}
	}

	/**
	 * gets system mapping from system
	 * @param system
	 * @return
	 */
	private SysSystemMappingDto getSystemMapping(SysSystemDto system) {
		List<SysSystemMappingDto> systemMappings = mappingService.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		return systemMappings.get(0);
	}

	/**
	 * Check if catalog already exists otherwise create new one
	 * @param catalogueName
	 * @return
	 */
	private UUID createCatalogue(String catalogueName) {
		IdmRoleCatalogueDto catalog = getCatalogueByCode(catalogueName);
		if (catalog != null) {
		  return catalog.getId();
		} else {
		  catalog = new IdmRoleCatalogueDto();
		  catalog.setCode(catalogueName);
		  catalog.setName(catalogueName);
		  catalog.setParent(null);
		  
		  return roleCatalogueService.save(catalog).getId();
		}
	}

	/**
	 * @param code
	 * @return
	 */
	private IdmRoleCatalogueDto getCatalogueByCode(String code) {
	    IdmRoleCatalogueFilter filter = new IdmRoleCatalogueFilter();
	    filter.setCode(code);
	    Page<IdmRoleCatalogueDto> result = roleCatalogueService.find(filter, null);
	    if (result.getTotalElements() != 1) {
	        return null;
	    }
	    return result.getContent().get(0);
	}

    /**
     * @param role
     * @param catalogueId
     */
    private void addRoleToCatalogue(IdmRoleDto role, UUID catalogueId) {
	    if (catalogueId == null) {
	    	return;
	    }
	    IdmRoleCatalogueRoleDto catRoleDto = new IdmRoleCatalogueRoleDto();
	    catRoleDto.setRole(role.getId());
	    catRoleDto.setRoleCatalogue(catalogueId);
	    LOG.info("Putting role ${catRoleDto.getRole()} into catalogue ${catRoleDto.getRoleCatalogue()}");
	    catRoleDto = roleCatalogueRoleService.save(catRoleDto);
	    role = roleService.save(role);
    }

    /**
     * @param roleId
     * @param catalogueId
     * @return
     */
    private boolean roleIsInCatalogue(UUID roleId, UUID catalogueId) {
	    IdmRoleCatalogueRoleFilter filter = new IdmRoleCatalogueRoleFilter();
	    filter.setRoleId(roleId);
	    filter.setRoleCatalogueId(catalogueId);
	    Page<IdmRoleCatalogueRoleDto> result = roleCatalogueRoleService.find(filter, null);
	    if (result.getTotalElements() != 1) {
	        return false;
	    }
	    return true;
    }

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		pathToFile = getParameterConverter().toString(properties, PARAM_CSV_FILE_PATH);
		systemName = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		descriptionColumnName = getParameterConverter().toString(properties, PARAM_DESCRIPTION_COLUMN_NAME);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		memberOfAttribute = getParameterConverter().toString(properties, PARAM_MEMBER_OF_ATTRIBUTE);
		canBeRequested = getParameterConverter().toBoolean(properties, PARAM_CAN_BE_REQUESTED);
		// if not filled, init multiValueSeparator and check if csv has description		
		if (multiValueSeparator == null) {
			multiValueSeparator = MULTI_VALUE_SEPARATOR;
		}
		if (descriptionColumnName != null) {
			hasDescription = true;
		} else {
			hasDescription = false;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_FILE_PATH, pathToFile);
		props.put(PARAM_SYSTEM_NAME, systemName);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_DESCRIPTION_COLUMN_NAME, descriptionColumnName);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_MEMBER_OF_ATTRIBUTE, memberOfAttribute);
		props.put(PARAM_CAN_BE_REQUESTED, canBeRequested);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto pathToFileAttribute = new IdmFormAttributeDto(PARAM_CSV_FILE_PATH, PARAM_CSV_FILE_PATH,
				PersistentType.SHORTTEXT);
		pathToFileAttribute.setRequired(true);
		IdmFormAttributeDto systemNameAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_NAME, PARAM_SYSTEM_NAME,
				PersistentType.SHORTTEXT);
		systemNameAttribute.setRequired(true);
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		IdmFormAttributeDto descriptionColumnNameAttribute = new IdmFormAttributeDto(PARAM_DESCRIPTION_COLUMN_NAME, PARAM_DESCRIPTION_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(false);
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setRequired(false);
		multiValueSeparatorAttribute.setPlaceholder("default is new line");
		IdmFormAttributeDto memberOfAttribute = new IdmFormAttributeDto(PARAM_MEMBER_OF_ATTRIBUTE, PARAM_MEMBER_OF_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		memberOfAttribute.setDefaultValue(MEMBER_OF_ATTRIBUTE);
		memberOfAttribute.setRequired(true);
		IdmFormAttributeDto canBeRequestedAttribute = new IdmFormAttributeDto(PARAM_CAN_BE_REQUESTED, PARAM_CAN_BE_REQUESTED,
				PersistentType.BOOLEAN);
		canBeRequestedAttribute.setDefaultValue(String.valueOf(CAN_BE_REQUESTED));
		canBeRequestedAttribute.setRequired(false);
		//
		return Lists.newArrayList(pathToFileAttribute, systemNameAttribute, rolesColumnNameAttribute, descriptionColumnNameAttribute, columnSeparatorAttribute, multiValueSeparatorAttribute, memberOfAttribute, canBeRequestedAttribute);
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
