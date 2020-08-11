package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.DefaultResultModel;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCompositionDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleFormAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFormAttributeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCompositionService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleFormAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.BaseFaceType;
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
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;
import eu.bcvsolutions.idm.extras.utils.Pair;


/**
 * This LRT imports all roles from CSV to IdM and sets/updates their attributes if they have any in the CSV
 * Creates catalog with name of the connected system
 * Adds role system mapped attribute for merge
 * Fills / updates description
 * Updates can be requested
 *
 * @author Marek Klement
 * @author Petr Hanák
 * @author Tomáš Doischer
 * @author Roman Kučera
 */
@Component(ImportRolesFromCSVExecutor.TASK_NAME)
public class ImportRolesFromCSVExecutor extends AbstractCsvImportTask {

	private static final Logger LOG = LoggerFactory.getLogger(ImportRolesFromCSVExecutor.class);

	public static final String TASK_NAME = "extras-import-roles-from-csv";
	
	public static final String PARAM_ROLES_COLUMN_NAME = "rolenamescolumn";
	public static final String PARAM_ROLE_CODE_COLUMN_NAME = "rolecodescolumn";
	public static final String PARAM_DESCRIPTION_COLUMN_NAME = "descriptioncolumn";
	public static final String PARAM_ATTRIBUTES_COLUMN_NAME = "roleattributescolumn";
	public static final String PARAM_CRITICALITY_COLUMN_NAME = "criticalitycolumn";
	public static final String PARAM_GUARANTEE_COLUMN_NAME = "guaranteescolumn";
	public static final String PARAM_GUARANTEE_TYPE_COLUMN_NAME = "guaranteetypescolumn";
	public static final String PARAM_GUARANTEE_TYPE_UPDATE = "updateguaranteetypes";
	public static final String PARAM_GUARANTEE_ROLE_COLUMN_NAME = "guaranteerolescolumn";
	public static final String PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME = "guaranteeroletypescolumn";
	public static final String PARAM_GUARANTEE_ROLE_TYPE_UPDATE = "updateguaranteeroletypes";
	public static final String PARAM_CATALOGUES_COLUMN_NAME = "cataloguecolumn";
	public static final String PARAM_SUBROLES_COLUMN_NAME = "subrolescolumn";
	public static final String PARAM_EAV_CODE_PREFIX = "eavcodeprefix";
	public static final String PARAM_EAV_VALUE_PREFIX = "eavvalueprefix";
	public static final String PARAM_FORM_DEFINITION_CODE = "formdefinitioncode";
	public static final String PARAM_MULTI_VALUE_SEPARATOR = "multivalueseparator";
	public static final String PARAM_SYSTEM_NAME_PREFIX = "systemnameprefix";
	public static final String PARAM_SYSTEM_ATTRIBUTE_PREFIX = "systemattributeprefix";
	public static final String PARAM_SYSTEM_ATTRIBUTE_VALUE_PREFIX = "systemattributevalueprefix";
	public static final String PARAM_ENVIRONMENT = "roleenvironment";
	public static final String PARAM_CAN_BE_REQUESTED = "canberequested";

	private List<UUID> cataloguesUuid = new ArrayList<>();
	private List<String> oldFormAttributesNames = new ArrayList<>();
	
	// Defaults
	private static final String MULTI_VALUE_SEPARATOR = "\\|";
	private static final Boolean CAN_BE_REQUESTED = Boolean.TRUE;
	private static final String OBJECT_CLASSNAME = "__ACCOUNT__";

	private String rolesColumnName;
	private String roleCodesColumnName;
	private String descriptionColumnName;
	private String attributesColumnName;
	private String criticalityColumnName;
	private String guaranteeColumnName;
	private String guaranteeTypeColumnName;
	private String guaranteeRoleColumnName;
	private String guaranteeRoleTypeColumnName;
	private String catalogueColumnName;
	private String subRoleColumnName;
	private String eavCodePrefix;
	private String eavValuePrefix;
	private String formDefinitionCode;
	private String multiValueSeparator;
	private String systemNamePrefix;
	private String systemAttributePrefix;
	private String systemAttributeValuePrefix;
	private String environmentName;
	private Boolean canBeRequested;
	private Boolean updateGuaranteeType;
	private Boolean updateGuaranteeRoleType;
	private Boolean hasRoleCodes;
	private Boolean hasDescription;
	private Boolean hasAttribute;
	private Boolean hasCriticality;
	private Boolean hasGuarantees;
	private Boolean hasGuaranteeTypes;
	private Boolean hasGuaranteeRoles;
	private Boolean hasGuaranteeRoleTypes;
	private Boolean hasEnvironment;
	private Boolean hasSystemInfo;
	private Boolean hasCatalogue;
	private Boolean hasSubRoles;
	private Boolean hasEavs;
		
	@Autowired
	private SysSystemService sysSystemService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysSystemMappingService mappingService;
	@Autowired
    private FormService formService;
	@Autowired
	private IdmRoleFormAttributeService roleFormAttributeService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private IdmRoleGuaranteeRoleService roleGuaranteeRoleService;
	@Autowired
	private IdmRoleCompositionService roleCompositionService;
	@Autowired
	private IdmCodeListService codeListService;
	@Autowired
	private IdmCodeListItemService codeListItemService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;

	@Override
	public String getName() {
		return TASK_NAME;
	}
	
	@Override
	protected void processRecords(List<CSVRecord> records) {
		LOG.debug("Start process");
		this.counter = 0L;

		// get data from CSV
		records.forEach(record -> {
			String roleName = getRecordValue(record, rolesColumnName);
			
			// try to find the role
			String roleCodeToSearch = "";
			if (hasRoleCodes) {
				String rc = getRecordValue(record, roleCodesColumnName);
				if (StringUtils.isEmpty(rc)) {
					roleCodeToSearch = roleNameToCode(roleName);
				} else {
					roleCodeToSearch = rc;
				}
			} else {
				roleCodeToSearch = roleNameToCode(roleName);
			}

			IdmRoleDto role = roleService.getByCode(roleCodeToSearch);
			if (role == null) {
				role = roleService.getByCode(roleNameToCode(roleName));
			}
			
			if (role == null && hasEnvironment) {
				role = roleService.getByBaseCodeAndEnvironment(roleCodeToSearch, environmentName);
			}
			
			role = handleRole(role, roleName, roleCodeToSearch, record);
			
			// creates role catalogues
			if(hasCatalogue) {
				List<String> cataloguesForRole = getRecordValues(record, catalogueColumnName);
				for(String catalogue : cataloguesForRole) {
					if(catalogue != null && !StringUtils.isEmpty(catalogue.trim())) {
						UUID catalogueId = createCatalogue(catalogue);
						cataloguesUuid.add(catalogueId);
					}
				}
				if(!cataloguesUuid.isEmpty()) {
					for(UUID catalogueUuid : cataloguesUuid) {
						if (!roleIsInCatalogue(role.getId(), catalogueUuid)) {
							addRoleToCatalogue(role, catalogueUuid);
						}
					}
				}
				cataloguesUuid.clear();
			}
			
			++this.counter;
		});
		this.count = this.counter;
	}
	
	private IdmRoleDto handleRole(IdmRoleDto role, String roleName, String roleCode, CSVRecord record) {
		String description = getRecordValue(record, descriptionColumnName);
		List<String> roleAttributes = getRecordValues(record, attributesColumnName);
		String criticality = getRecordValue(record, criticalityColumnName);
		List<String> guarantees = getRecordValues(record, guaranteeColumnName);
		String guaranteeType = getRecordValue(record, guaranteeTypeColumnName);
		List<String> guaranteesRoles = getRecordValues(record, guaranteeRoleColumnName);
		String guaranteeRoleType = getRecordValue(record, guaranteeRoleTypeColumnName);
		List<String> subRoles = getRecordValues(record, subRoleColumnName);

		
		if (role == null) {
			role = createRole(roleName, description, roleAttributes, criticality, guarantees, guaranteeType, 
					guaranteesRoles, guaranteeRoleType, subRoles, roleCode, environmentName, record);
		} else {
			updateRole(roleName, role, description, roleAttributes, criticality,
					guarantees, guaranteeType, guaranteesRoles, guaranteeRoleType, 
					subRoles, record);
		}
		
		return role;
	}
	
	private String getRecordValue(CSVRecord record, String columnName) {
		if (!StringUtils.isEmpty(columnName) && record.isMapped(columnName)) {
			return record.get(columnName);
		} else {
			return "";
		}
	}
	
	private List<String> getRecordValues(CSVRecord record, String columnName) {
		String value = getRecordValue(record, columnName);
		return splitStringToList(value);
	}
	
	private Boolean handleEavs(CSVRecord record, String eavCodePrefix, String eavValuePrefix, IdmRoleDto role) {
		Boolean eavsUpdated = Boolean.FALSE;
		
		List<Pair<String, String>> eavs = processDynamicAttribute(record, eavCodePrefix, eavValuePrefix);
		for (Pair<String, String> eav : eavs) {
			eavsUpdated = setEav(role, eav.getFirst(), eav.getSecond());
		}
		
		return eavsUpdated;
	}

	@Override
	protected void processOneDynamicAttribute(String namePrefix, String name, String valuePrefix, String value,
			boolean isEav) {
		throw new UnsupportedOperationException("No dynamic attributes present");
	}
	
	private List<String> splitStringToList(String s) {
		if (StringUtils.isEmpty(s)) {
			return new ArrayList<>();
		}
		return Arrays.asList(s.split(multiValueSeparator));
	}

	private SysSystemDto findSystem(String systemName) {
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
	 * creates roles from column given in input
	 * sets their description, attributes, criticality and guarantees
	 * assigns system to role
	 * 
	 * @param roleName
	 * @param system
	 * @param description
	 * @param roleAttributes
	 * @param criticality
	 * @param guarantees
	 * @param guaranteesRoles
	 * @param systemInfos
	 * @return
	 */
	private IdmRoleDto createRole(String roleName, String description, List<String> roleAttributes,
								  String criticality, List<String> guarantees, String guaranteeType, List<String> guaranteesRoles, String guaranteeRoleType,
								  List<String> subRoles, String roleCode, String environmentName, CSVRecord record) {
		// Create role
		IdmRoleDto role = new IdmRoleDto();
		role.setBaseCode(roleCode);
		
		role.setName(roleName);
		
		if(hasEnvironment) {
			role.setEnvironment(environmentName);
		}
		
		setCriticality(criticality, role, Boolean.FALSE);
			
		if(canBeRequested == null) {
			canBeRequested = Boolean.FALSE;
		}
		role.setCanBeRequested(canBeRequested);
		
		if (hasDescription) {
			role.setDescription(description);
		}
		
		role = roleService.save(role);
		
		// Create the attributes
		if(hasAttribute && !roleAttributes.isEmpty() && !StringUtils.isEmpty(roleAttributes.get(0).trim())) {
			createAttribute(roleAttributes, role);
		}
		
		// Set the guarantee
		if(hasGuarantees) {
			setGuarantees(guarantees, role, guaranteeType);
		}
		
		// Set the guarantee by role
		if(hasGuaranteeRoles) {
			setGuaranteeRoles(guaranteesRoles, role, guaranteeRoleType);
		}
		
		// Set EAVs
		if (hasEavs) {
			handleEavs(record, eavCodePrefix, eavValuePrefix, role);
		}
		
		// Create role system
		if(hasSystemInfo) {
			processSystemAttribute(record, systemNamePrefix, systemAttributePrefix, systemAttributeValuePrefix, role, roleName);
		}
		
		// Assign subroles
		if(hasSubRoles) {
			assignSubRoles(subRoles, role);
		}


		this.logItemProcessed(role, taskCompleted("Role " + roleName + " created"));
		return role;
	}

	private Boolean setSystemAndAttributes(String roleName, List<String> systemInfos, IdmRoleDto role) {
		Boolean updated = Boolean.FALSE;
		for (String systemInfo : systemInfos) {
			String[] systemInfoParsed = systemInfo.split("\\:");
			String systemName = "";
			String attributeCode = "";
			String attributeValue = "";
			if (systemInfoParsed.length > 0 && !StringUtils.isEmpty(systemInfoParsed[0])) {
				systemName = systemInfoParsed[0];
			}
			if (systemInfoParsed.length > 1 && !StringUtils.isEmpty(systemInfoParsed[1])) {
				attributeCode = systemInfoParsed[1];
			}
			if (systemInfoParsed.length > 2 && !StringUtils.isEmpty(systemInfoParsed[2])) {
				attributeValue = systemInfoParsed[2];
			}
			
			if (!StringUtils.isEmpty(systemName)) {
				SysSystemDto system = findSystem(systemName);
				SysSystemMappingDto systemMapping = getSystemMapping(system);
				
				
				SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
				roleSystemFilter.setSystemId(system.getId());
				roleSystemFilter.setRoleId(role.getId());
				roleSystemFilter.setSystemMappingId(systemMapping.getId());
				long count = roleSystemService.count(roleSystemFilter);

				if (count == 0) {
					SysRoleSystemDto roleSystem = new SysRoleSystemDto();
					roleSystem.setSystem(system.getId());
					roleSystem.setRole(role.getId());
					roleSystem.setSystemMapping(systemMapping.getId());
					roleSystemService.save(roleSystem);
				}

				// Create role system mapping with role name	
				updated = createUpdateRoleSystemMapping(roleName, system, attributeCode, attributeValue, role);
			}						
		}
		
		return updated;
	}

	/**
	 * Updates canBeRequested and description, role attributes, criticality and guarantees. Only criticality, 
	 * canBerequested and description can be deleted (by changing), nothing else is deleted. Description 
	 * will not be deleted if the new value is empty
	 *  @param role
	 * @param description
	 * @param systemInfos
	 */
	private void updateRole(String roleName, IdmRoleDto role, String description, List<String> roleAttributes, String criticality,
							List<String> guarantees, String guaranteeType, List<String> guaranteesRoles, String guaranteeRoleType, List<String> subRoles, CSVRecord record) {
		
		Boolean canBeReqUpdated = Boolean.FALSE;
		if (canBeRequested != null && role.isCanBeRequested() != canBeRequested) {
			role.setCanBeRequested(canBeRequested);
			canBeReqUpdated = Boolean.TRUE;
		}
		
		Boolean descriptionUpdated = Boolean.FALSE;
		// update description if 1) the description is in CSV and 2) if it is different from the current description
		if (hasDescription && description.trim().length() != 0 && 
				(role.getDescription() == null || !role.getDescription().equals(description))) {
			role.setDescription(description);
			descriptionUpdated = Boolean.TRUE;
		}

		// update the criticality
		Boolean criticalityUpdated = setCriticality(criticality, role, Boolean.TRUE);
		
		// update the guarantee
		Boolean guaranteeUpdated = Boolean.FALSE;
		if(hasGuarantees) {
			guaranteeUpdated = setGuarantees(guarantees, role, guaranteeType);
		}
		
		// update the guarantee by role
		Boolean guaranteeRoleUpdated = Boolean.FALSE;
		if(hasGuaranteeRoles) {
			guaranteeRoleUpdated = setGuaranteeRoles(guaranteesRoles, role, guaranteeRoleType);
		}
		
		// update EAVs
		Boolean eavsUpdated = Boolean.FALSE;
		if (hasEavs) {
			eavsUpdated = handleEavs(record, eavCodePrefix, eavValuePrefix, role);
		}
		
		// update attributes
		Boolean attributesUpdated = Boolean.FALSE;
		if(hasAttribute && !StringUtils.isEmpty(roleAttributes.get(0).trim())) {
			createAttribute(roleAttributes, role);
		}
		
		// update subroles
		Boolean subRolesUpdated = Boolean.FALSE;
		if(hasSubRoles) {
			subRolesUpdated = assignSubRoles(subRoles, role);
		}

		Boolean systemMappingUpdated = Boolean.FALSE;
		// update role system
		if(hasSystemInfo) {
			systemMappingUpdated = processSystemAttribute(record, systemNamePrefix, systemAttributePrefix, systemAttributeValuePrefix, role, roleName);
		}
		
		roleService.save(role);

		if (canBeReqUpdated || descriptionUpdated || attributesUpdated || criticalityUpdated || 
				guaranteeUpdated || guaranteeRoleUpdated || subRolesUpdated || systemMappingUpdated || eavsUpdated) {
			role = roleService.save(role);
			this.logItemProcessed(role, taskCompleted("Role " + role.getName() + " updated"));			
		} else {
			this.logItemProcessed(role, taskNotCompleted("Nothing to update! Role name: " + roleName));
		}
	}
	
	/**
	 * Creates role system mapping.
	 * 
	 * @param roleName
	 * @param system
	 * @param attributeCode
	 * @param attributeValue
	 * @param role
	 * @return
	 */
	private Boolean createUpdateRoleSystemMapping(String roleName, SysSystemDto system, String attributeCode, String attributeValue, IdmRoleDto role) {
		if(!StringUtils.isEmpty(attributeCode)) {
			String transformationScript;
			if (!StringUtils.isEmpty(attributeValue)) {
				transformationScript = MessageFormat.format("\"{0}\"", attributeValue);
			} else {
				transformationScript = MessageFormat.format("\"{0}\"", roleName);
			}
			roleSystemAttributeService.addRoleMappingAttribute(system.getId(), role.getId(), attributeCode, transformationScript, OBJECT_CLASSNAME);
			return true;
		}
		return false;
	}
	
	/**
	 * Assigns sub roles by code to a role, does not remove current ones
	 * 
	 * @param subRoles
	 * @param role
	 * @return
	 */
	private Boolean assignSubRoles(List<String> subRoles, IdmRoleDto role) {
		Boolean updated = Boolean.FALSE;
		// Find current sub roles (cannot be assigned again)
		List<IdmRoleCompositionDto> currentSubRolesComp = roleCompositionService.findAllSubRoles(role.getId());
		List<String> currentSubRoles = new ArrayList<>();
		if(currentSubRolesComp != null && !currentSubRolesComp.isEmpty()) {
			for (IdmRoleCompositionDto composition : currentSubRolesComp) {
				UUID uuid = composition.getSub();
				IdmRoleFilter rf = new IdmRoleFilter();
				rf.setId(uuid);
				IdmRoleDto current = roleService.find(rf, null).getContent().get(0);
				currentSubRoles.add(current.getCode());
			}
		}
		
		if(subRoles != null && !subRoles.isEmpty()) {
			for(String subRoleCode : subRoles) {
				if(subRoleCode.trim().length() > 0 && !currentSubRoles.contains(subRoleCode)) {
					// Find role to assign as subrole
					IdmRoleFilter roleFilter = new IdmRoleFilter();
					roleFilter.setBaseCode(subRoleCode);
					List<IdmRoleDto> foundSubRoles = roleService.find(roleFilter, null).getContent();
					if(!foundSubRoles.isEmpty()) {
						IdmRoleDto subRole = foundSubRoles.get(0);
						
						// Assign the subrole to the role
						IdmRoleCompositionDto roleComposition = new IdmRoleCompositionDto();
				        roleComposition.setSuperior(role.getId());
				        roleComposition.setSub(subRole.getId());
						
				        roleCompositionService.save(roleComposition);
				        updated = Boolean.TRUE;
					} else {
						logItemProcessed(role, taskNotCompleted("Subrole " + subRoleCode + " was not found!"));

					}
				}
			}
		}
		return updated;
	}

	/**
	 * Sets guarantees by login to a role, does not remove current ones
	 * 
	 * @param guarantees
	 * @param role
	 * @return
	 */
	private Boolean setGuarantees(List<String> guarantees, IdmRoleDto role, String guaranteeType) {
		// Set the guarantee by identity
		// Find current guarantees
		Boolean updated = Boolean.FALSE;
		IdmRoleGuaranteeFilter filterGuaranteeRole = new IdmRoleGuaranteeFilter();
		filterGuaranteeRole.setRole(role.getId());
		List<IdmRoleGuaranteeDto> currentGarantsLinks = roleGuaranteeService.find(filterGuaranteeRole, null).getContent();
		List<String> currentGuaranteesLogin = new ArrayList<>((int) (currentGarantsLinks.size() / 0.75));
		for(IdmRoleGuaranteeDto garantLink : currentGarantsLinks) {
			IdmIdentityDto garant = identityService.get(garantLink.getGuarantee());
			currentGuaranteesLogin.add(garant.getUsername());
		}
		for(String guarantee : guarantees) {
			Boolean guaranteeSet = Boolean.FALSE;
			if(!StringUtils.isEmpty(guarantee) && (!currentGuaranteesLogin.contains(guarantee) || updateGuaranteeType)) {
				// Finds the identity of the guarantee, only if the role doesn't yet have the guarantee set
				IdmIdentityDto identityGuarantee = identityService.getByUsername(guarantee);
				
				// Creates a guarantee and sets it to the role
				if(identityGuarantee != null) {
					IdmRoleGuaranteeFilter filterGuarantee = new IdmRoleGuaranteeFilter();
					filterGuarantee.setRole(role.getId());
					filterGuarantee.setGuarantee(identityGuarantee.getId());
					if (checkGuaranteeType(guaranteeType)) {
						filterGuarantee.setType(guaranteeType);
					}
					List<IdmRoleGuaranteeDto> currentGarantsLink = roleGuaranteeService.find(filterGuarantee, null).getContent();
					
					if (currentGarantsLink == null || currentGarantsLink.isEmpty()) {
						IdmRoleGuaranteeDto guar = new IdmRoleGuaranteeDto();
						guar.setGuarantee(identityGuarantee.getId());
						guar.setRole(role.getId());
						// Set type of guarantee
						if (hasGuaranteeTypes && checkGuaranteeType(guaranteeType)) {
							guar.setType(guaranteeType);
						}
						
						roleGuaranteeService.save(guar);
						guaranteeSet = Boolean.TRUE;
						updated = Boolean.TRUE;
					} 
				}
			}
			
			if (!guaranteeSet) {
				logItemProcessed(role, taskNotCompleted("Role " + role.getCode() + " did not get a guarantee set."));
			}
		}
		return updated;
	}
	
	/**
	 * Sets guarantee roles to a role, does not remove current ones
	 * 
	 * @param guaranteesRoles
	 * @param role
	 * @return
	 */
	private Boolean setGuaranteeRoles(List<String> guaranteesRoles, IdmRoleDto role, String guaranteeRoleType) {
		// Set the guarantee by role
		// Find current role guarantees
		Boolean updated = Boolean.FALSE;
		IdmRoleGuaranteeRoleFilter filterRoleGuaranteeRole = new IdmRoleGuaranteeRoleFilter();
		filterRoleGuaranteeRole.setRole(role.getId());
		List<IdmRoleGuaranteeRoleDto> currentGarantsRoleLinks = roleGuaranteeRoleService.find(filterRoleGuaranteeRole, null).getContent();
		List<String> currentGuaranteesRoleCodes = new ArrayList<>((int) (currentGarantsRoleLinks.size() / 0.75));
		for(IdmRoleGuaranteeRoleDto garantRoleLink : currentGarantsRoleLinks) {
			IdmRoleDto garantRole = roleService.get(garantRoleLink.getGuaranteeRole());
			currentGuaranteesRoleCodes.add(garantRole.getCode());
		}
		
		for(String guaranteeRole : guaranteesRoles) {
			Boolean guaranteeRoleSet = Boolean.FALSE;
			if (!StringUtils.isEmpty(guaranteeRole) && (!currentGuaranteesRoleCodes.contains(guaranteeRole) || updateGuaranteeRoleType)) {
				// Finds the role if the role doesn't have this guarantee role yet
				IdmRoleDto roleGuarantee = roleService.getByCode(guaranteeRole);
				
				// Creates a guarantee and sets it to the role
				if (roleGuarantee != null) {
					IdmRoleGuaranteeRoleFilter filterRoleGuaranteeByRole = new IdmRoleGuaranteeRoleFilter();
					filterRoleGuaranteeByRole.setRole(role.getId());
					filterRoleGuaranteeByRole.setGuaranteeRole(roleGuarantee.getId());
					if (checkGuaranteeType(guaranteeRoleType)) {
						filterRoleGuaranteeByRole.setType(guaranteeRoleType);
					}
					List<IdmRoleGuaranteeRoleDto> currentGarantsRoleLink = roleGuaranteeRoleService.find(filterRoleGuaranteeByRole, null).getContent();
					if (currentGarantsRoleLink == null || currentGarantsRoleLink.isEmpty()) {
						IdmRoleGuaranteeRoleDto guar = new IdmRoleGuaranteeRoleDto();
						guar.setGuaranteeRole(roleGuarantee.getId());
						guar.setRole(role.getId());
						if (hasGuaranteeRoleTypes && checkGuaranteeType(guaranteeRoleType)) {
							guar.setType(guaranteeRoleType);
						}
						
						roleGuaranteeRoleService.save(guar);
						guaranteeRoleSet = Boolean.TRUE;
						updated = Boolean.TRUE;
					} 
				} 
			} 
			
			if (!guaranteeRoleSet) {
				logItemProcessed(role, taskNotCompleted("Role " + role.getCode() + " did not get a guarantee role set."));
			}
		}
		return updated;
	}

	/**
	 * Sets criticality to a role, does not change existing values if the CSV value is empty
	 * 
	 * @param guaranteesRoles
	 * @param role
	 * @param updating
	 * @return
	 */
	private Boolean setCriticality(String criticality, IdmRoleDto role, Boolean updating) {
		// set criticality, 
		int criticalityValue = 0;
		
		if(criticality.trim().length() != 0) {
			criticalityValue = Integer.parseInt(criticality);
		}
		
		if(hasCriticality) {
			if(!updating) {
				role.setPriority(criticalityValue);
				return Boolean.TRUE;
			} else {
				if(criticality.trim().length() == 0) {
					return Boolean.FALSE;
				} else {
					role.setPriority(criticalityValue);
					return Boolean.TRUE;
				}
			}
		} else {
			if(!updating) {
				role.setPriority(criticalityValue);
				return Boolean.TRUE;
			} else {
				return Boolean.FALSE;
			}
		}
	}

	/**
	 * Creates form definition and role attribute, sets the attribute to a role, does not remove current attributes
	 * 
	 * @param guaranteesRoles
	 * @param role
	 * @return
	 */
	private Boolean createAttribute(List<String> roleAttributes, IdmRoleDto role) {
		Boolean updated = Boolean.FALSE;
		IdmFormDefinitionDto def = formService.getDefinition(IdmIdentityRole.class, formDefinitionCode);
		for(String roleAttribute : roleAttributes) {
			if((roleAttribute == null) || (roleAttribute.trim().length() == 0)) {
				continue;
			}
			// Creates the IdmFormAttributeDto
			IdmFormAttributeDto roleIdEav = new IdmFormAttributeDto();
		    roleIdEav.setName(roleAttribute);
		    String roleAttributeCode = roleNameToCode(roleAttribute);
		    roleIdEav.setCode(roleAttributeCode);
		    roleIdEav.setPersistentType(PersistentType.SHORTTEXT);
		    
		    if(def == null) {
		    	// If the role definition doesn't exist, we'll create it
		    	List<IdmFormAttributeDto> attrList = new ArrayList<>();
		        attrList.add(roleIdEav);
		    	def = formService.createDefinition(IdmIdentityRole.class, formDefinitionCode, attrList);
		    	
		    } else {
		    	// If the role definition exists, we'll check if it has the roleIdEav, if not, we'll add it
		    	List<IdmFormAttributeDto> oldFormAttributes = def.getFormAttributes();
		    	List<IdmFormAttributeDto> newFormAttributes = new ArrayList<>();
		    	
			    for(IdmFormAttributeDto attr : oldFormAttributes) {
			    	// gets names of all of the old attributes
			    	oldFormAttributesNames.add(attr.getName());
			    }
			    
			    if(!oldFormAttributesNames.contains(roleAttribute)) {
			    	newFormAttributes.add(roleIdEav);
			    }
			    
			    oldFormAttributesNames.clear();
			    	    
			    for(IdmFormAttributeDto attr : newFormAttributes) {
			    	// If the definition doesn't have the roleIdEav, we'll add it
			    	attr.setFormDefinition(def.getId());
			    	formAttributeService.save(attr); 
			    	def = formService.saveDefinition(def);
			    }
			    
		    }
		}
		
		role.setIdentityRoleAttributeDefinition(def.getId());
		roleService.save(role);
			
		// check which attributes of the definition the role is supposed to have and add them to it
		List<IdmFormAttributeDto> tmp = def.getFormAttributes();
		
		IdmRoleFormAttributeFilter roleFormAttributeFilter = new IdmRoleFormAttributeFilter();
		roleFormAttributeFilter.setRole(role.getId());
		roleFormAttributeFilter.setFormDefinition(def.getId());
		List<IdmRoleFormAttributeDto> allRolesParams = roleFormAttributeService.find(roleFormAttributeFilter, null).getContent();

		for(IdmFormAttributeDto a : tmp) {
			if(roleAttributes.contains(a.getName())) {
				IdmRoleFormAttributeDto relatedRoleFormAttribute = allRolesParams.stream().filter(p -> {
					IdmFormAttributeDto formAttributeDto = formAttributeService.get(p.getFormAttribute());
					return formAttributeDto.getCode().equals(a.getName());
				}).findFirst().orElse(null);
				
				if(relatedRoleFormAttribute == null) {
					// if the role doesn't have the attribute set already, set it
					roleFormAttributeService.addAttributeToSubdefintion(role, a);
					updated = Boolean.TRUE;
				}
			}
		}
		return updated;
	}
	
	private Boolean setEav(IdmRoleDto role, String eavCode, String eavValue) {
		Boolean updated = Boolean.FALSE;
		Boolean eavSet = Boolean.FALSE;
		
		IdmFormDefinitionDto definition = formDefinitionService.findOneByMain(IdmRole.class.getCanonicalName());
		List<IdmFormAttributeDto> attributes = definition.getFormAttributes();
		for (IdmFormAttributeDto attribute : attributes) {
			if (attribute.getCode().equals(eavCode)) {
				formService.saveValues(role.getId(), IdmRole.class, eavCode, Lists.newArrayList(eavValue));
				eavSet = Boolean.TRUE;
				updated = Boolean.TRUE;
			}
		} 
		
		if (!eavSet) {
			logItemProcessed(role, taskNotCompleted("Role " + role.getCode() + " did not get an EAV " + eavCode + " set. It does not exist in the main definition!"));
		}
		
		return updated;
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

	/**
	 * gets system mapping from system
	 * 
	 * @param system
	 * @return
	 */
	private SysSystemMappingDto getSystemMapping(SysSystemDto system) {
		List<SysSystemMappingDto> systemMappings = mappingService.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		if(systemMappings.isEmpty()){
			throw new ResultCodeException(ExtrasResultCode.WRONG_SIZE_MAPPING);
		}
		return systemMappings.get(0);
	}

	/**
	 * Check if catalog already exists otherwise create new one
	 * 
	 * @param catalogueCode
	 * @return
	 */
	private UUID createCatalogue(String catalogueCode) {
		IdmRoleCatalogueDto catalog = getCatalogueByCode(catalogueCode);
		if (catalog != null) {
		  return catalog.getId();
		} else {
		  catalog = new IdmRoleCatalogueDto();
		  catalog.setCode(catalogueCode);
		  catalog.setName(catalogueCode);
		  catalog.setParent(null);
		  
		  return roleCatalogueService.save(catalog).getId();
		}
	}

	/**
	 * Finds catalogue
	 * 
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
     * Add role to catalogue
     * 
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
	    roleCatalogueRoleService.save(catRoleDto);
	    roleService.save(role);
    }

    /**
     * @param roleId
     * @param catalogueId
     * @return
     */
    private Boolean roleIsInCatalogue(UUID roleId, UUID catalogueId) {
	    IdmRoleCatalogueRoleFilter filter = new IdmRoleCatalogueRoleFilter();
	    filter.setRoleId(roleId);
	    filter.setRoleCatalogueId(catalogueId);
	    Page<IdmRoleCatalogueRoleDto> result = roleCatalogueRoleService.find(filter, null);
	    if (result.getTotalElements() != 1) {
	        return Boolean.FALSE;
	    }
	    return Boolean.TRUE;
    }
    
	protected Boolean processSystemAttribute(CSVRecord record, String systemNamePrefix, String systemAttributePrefix, String systemAttributeValuePrefix, IdmRoleDto role,
			String roleName) {
		List<String> systemInfos = new ArrayList<>();
		
		if (!StringUtils.isBlank(systemNamePrefix)) {
			int suffix = 1;
			String columnSystemName = systemNamePrefix + suffix;
			String columnAttribute = "";
			if (!StringUtils.isEmpty(systemAttributePrefix)) {
				columnAttribute = systemAttributePrefix + suffix;
			}
			String columnValue = "";
			if (!StringUtils.isEmpty(systemAttributeValuePrefix)) {
				columnValue = systemAttributeValuePrefix + suffix;
			}
			while (record.isMapped(columnSystemName)) {
				++suffix;
				StringBuilder sb = new StringBuilder();
				String name = getRecordValue(record, columnSystemName);
				sb.append(name);
				if (!StringUtils.isEmpty(columnAttribute) && record.isMapped(columnAttribute)) {
					sb.append(":");
					sb.append(record.get(columnAttribute));
					columnAttribute = systemAttributePrefix + suffix; 
				}
				if (!StringUtils.isEmpty(columnValue) && record.isMapped(columnValue)) {
					sb.append(":");
					sb.append(record.get(columnValue));
					columnValue = systemAttributeValuePrefix + suffix;
				}

				
				columnSystemName = systemNamePrefix + suffix;
				
				systemInfos.add(sb.toString());
			}
		}
		
		return setSystemAndAttributes(roleName, systemInfos, role);
	}
    
    /**
     * Check if the supplied guarantee type is present in a code list of guarantee types.
     * 
     * @param guaranteeType
     * @return
     */
    private Boolean checkGuaranteeType(String guaranteeType) {
    	IdmCodeListDto guaranteeTypes = codeListService.getByCode("guarantee-type");
    	if (guaranteeTypes != null && !StringUtils.isEmpty(guaranteeType)) {
    		IdmCodeListItemFilter f = new IdmCodeListItemFilter();
        	f.setCodeListId(guaranteeTypes.getId());
        	f.setCode(guaranteeType);
        	List<IdmCodeListItemDto> codeListItems = codeListItemService.find(f, null).getContent();
        	if (!codeListItems.isEmpty()) {
        		return Boolean.TRUE;
        	} else {
        		return Boolean.FALSE;
        	}
    	} else {
    		return Boolean.FALSE;
    	}
    }
    
    /**
     * Check if the separator is empty, then uses new line, otherwise escapes the character.
     * 
     * @param separator
     * @return
     */
    private String getMultiValueSeparator(String separator) {
    	if (multiValueSeparator == null) {
			return MULTI_VALUE_SEPARATOR;
    	} else {
    		Pattern p = Pattern.compile("[^a-z0-9 ]", Pattern.CASE_INSENSITIVE);
    		Matcher m = p.matcher(separator);
    		
    		if (m.find()) {
    			return "\\" + separator;
    		} else {
    			return separator;
    		}
    	}
    }

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		roleCodesColumnName = getParameterConverter().toString(properties, PARAM_ROLE_CODE_COLUMN_NAME);
		descriptionColumnName = getParameterConverter().toString(properties, PARAM_DESCRIPTION_COLUMN_NAME);
		attributesColumnName = getParameterConverter().toString(properties, PARAM_ATTRIBUTES_COLUMN_NAME);
		criticalityColumnName = getParameterConverter().toString(properties, PARAM_CRITICALITY_COLUMN_NAME);
		guaranteeColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_COLUMN_NAME);
		guaranteeTypeColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_TYPE_COLUMN_NAME);
		updateGuaranteeType = getParameterConverter().toBoolean(properties, PARAM_GUARANTEE_TYPE_UPDATE);
		guaranteeRoleColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_ROLE_COLUMN_NAME);
		guaranteeRoleTypeColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME);
		updateGuaranteeRoleType = getParameterConverter().toBoolean(properties, PARAM_GUARANTEE_ROLE_TYPE_UPDATE);
		catalogueColumnName = getParameterConverter().toString(properties, PARAM_CATALOGUES_COLUMN_NAME);
		subRoleColumnName = getParameterConverter().toString(properties, PARAM_SUBROLES_COLUMN_NAME);
		eavCodePrefix = getParameterConverter().toString(properties, PARAM_EAV_CODE_PREFIX);
		eavValuePrefix = getParameterConverter().toString(properties, PARAM_EAV_VALUE_PREFIX);
		formDefinitionCode = getParameterConverter().toString(properties, PARAM_FORM_DEFINITION_CODE);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		environmentName = getParameterConverter().toString(properties, PARAM_ENVIRONMENT);
		systemNamePrefix = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME_PREFIX);
		systemAttributePrefix = getParameterConverter().toString(properties, PARAM_SYSTEM_ATTRIBUTE_PREFIX);
		systemAttributeValuePrefix = getParameterConverter().toString(properties, PARAM_SYSTEM_ATTRIBUTE_VALUE_PREFIX);
		canBeRequested = getParameterConverter().toBoolean(properties, PARAM_CAN_BE_REQUESTED);
		// if not filled, init multiValueSeparator and check if csv has description and escape it
		multiValueSeparator = getMultiValueSeparator(multiValueSeparator);

		if (roleCodesColumnName != null) {
			hasRoleCodes = Boolean.TRUE;
		} else {
			hasRoleCodes = Boolean.FALSE;
		}
		if (descriptionColumnName != null) {
			hasDescription = Boolean.TRUE;
		} else {
			hasDescription = Boolean.FALSE;
		}
		if (attributesColumnName != null) {
			hasAttribute = Boolean.TRUE;
		} else {
			hasAttribute = Boolean.FALSE;
		}
		if (criticalityColumnName != null) {
			hasCriticality = Boolean.TRUE;
		} else {
			hasCriticality = Boolean.FALSE;
		}
		if (guaranteeColumnName != null) {
			hasGuarantees = Boolean.TRUE;
		} else {
			hasGuarantees = Boolean.FALSE;
		}
		if (guaranteeTypeColumnName != null) {
			hasGuaranteeTypes = Boolean.TRUE;
		} else {
			hasGuaranteeTypes = Boolean.FALSE;
		}
		if (guaranteeRoleTypeColumnName != null) {
			hasGuaranteeRoleTypes = Boolean.TRUE;
		} else {
			hasGuaranteeRoleTypes = Boolean.FALSE;
		}
		if (guaranteeRoleColumnName != null) {
			hasGuaranteeRoles = Boolean.TRUE;
		} else {
			hasGuaranteeRoles = Boolean.FALSE;
		}
		if (environmentName != null) {
			hasEnvironment = Boolean.TRUE;
		} else {
			hasEnvironment = Boolean.FALSE;
		}
		if (systemAttributePrefix != null) {
			hasSystemInfo = Boolean.TRUE;
		} else {
			hasSystemInfo = Boolean.FALSE;
		}
		if (catalogueColumnName != null) {
			hasCatalogue = Boolean.TRUE;
		} else {
			hasCatalogue = Boolean.FALSE;
		}
		if (subRoleColumnName != null) {
			hasSubRoles = Boolean.TRUE;
		} else {
			hasSubRoles = Boolean.FALSE;
		}
		if (eavCodePrefix != null) {
			hasEavs = Boolean.TRUE;
		} else {
			hasEavs = Boolean.FALSE;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_ROLE_CODE_COLUMN_NAME, roleCodesColumnName);
		props.put(PARAM_DESCRIPTION_COLUMN_NAME, descriptionColumnName);
		props.put(PARAM_ATTRIBUTES_COLUMN_NAME, attributesColumnName);
		props.put(PARAM_CRITICALITY_COLUMN_NAME, criticalityColumnName);
		props.put(PARAM_GUARANTEE_COLUMN_NAME, guaranteeColumnName);
		props.put(PARAM_GUARANTEE_TYPE_COLUMN_NAME, guaranteeTypeColumnName);
		props.put(PARAM_GUARANTEE_TYPE_UPDATE, updateGuaranteeType);
		props.put(PARAM_GUARANTEE_ROLE_COLUMN_NAME, guaranteeRoleColumnName);
		props.put(PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME, guaranteeRoleTypeColumnName);
		props.put(PARAM_GUARANTEE_ROLE_TYPE_UPDATE, updateGuaranteeRoleType);
		props.put(PARAM_CATALOGUES_COLUMN_NAME, catalogueColumnName);
		props.put(PARAM_SUBROLES_COLUMN_NAME, subRoleColumnName);
		props.put(PARAM_EAV_CODE_PREFIX, eavCodePrefix);
		props.put(PARAM_EAV_VALUE_PREFIX, eavValuePrefix);
		props.put(PARAM_FORM_DEFINITION_CODE, formDefinitionCode);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_ENVIRONMENT, environmentName);
		props.put(PARAM_SYSTEM_NAME_PREFIX, systemNamePrefix);
		props.put(PARAM_SYSTEM_ATTRIBUTE_PREFIX, systemAttributePrefix);
		props.put(PARAM_SYSTEM_ATTRIBUTE_VALUE_PREFIX, systemAttributeValuePrefix);
		props.put(PARAM_CAN_BE_REQUESTED, canBeRequested);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		List<IdmFormAttributeDto> formAttributes = super.getFormAttributes();
		
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);

		IdmFormAttributeDto roleCodeColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLE_CODE_COLUMN_NAME, PARAM_ROLE_CODE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		roleCodeColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto descriptionColumnNameAttribute = new IdmFormAttributeDto(PARAM_DESCRIPTION_COLUMN_NAME, PARAM_DESCRIPTION_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(true);
		
		IdmFormAttributeDto criticalityColumnNameAttribute = new IdmFormAttributeDto(PARAM_CRITICALITY_COLUMN_NAME, PARAM_CRITICALITY_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		criticalityColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_COLUMN_NAME, PARAM_GUARANTEE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeTypeColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_TYPE_COLUMN_NAME, PARAM_GUARANTEE_TYPE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeTypeColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeTypeUpdateAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_TYPE_UPDATE, PARAM_GUARANTEE_TYPE_UPDATE,
				PersistentType.BOOLEAN);
		guaranteeTypeUpdateAttribute.setDefaultValue(String.valueOf(Boolean.FALSE));
		guaranteeTypeUpdateAttribute.setFaceType(BaseFaceType.BOOLEAN_SELECT);
		guaranteeTypeUpdateAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeRolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_ROLE_COLUMN_NAME, PARAM_GUARANTEE_ROLE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeRolesColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeRoleTypeColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME, PARAM_GUARANTEE_ROLE_TYPE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeRoleTypeColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto guaranteeRoleTypeUpdateAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_ROLE_TYPE_UPDATE, PARAM_GUARANTEE_ROLE_TYPE_UPDATE,
				PersistentType.BOOLEAN);
		guaranteeRoleTypeUpdateAttribute.setDefaultValue(String.valueOf(Boolean.FALSE));
		guaranteeRoleTypeUpdateAttribute.setFaceType(BaseFaceType.BOOLEAN_SELECT);
		guaranteeRoleTypeUpdateAttribute.setRequired(false);
		
		IdmFormAttributeDto cataloguesColumnNameAttribute = new IdmFormAttributeDto(PARAM_CATALOGUES_COLUMN_NAME, PARAM_CATALOGUES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		cataloguesColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto subRolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_SUBROLES_COLUMN_NAME, PARAM_SUBROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		subRolesColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto eavCodePrefixAttribute = new IdmFormAttributeDto(PARAM_EAV_CODE_PREFIX, PARAM_EAV_CODE_PREFIX,
				PersistentType.SHORTTEXT);
		eavCodePrefixAttribute.setRequired(false);
		
		IdmFormAttributeDto eavValuePrefixAttribute = new IdmFormAttributeDto(PARAM_EAV_VALUE_PREFIX, PARAM_EAV_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		eavValuePrefixAttribute.setRequired(false);
		
		IdmFormAttributeDto attributesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ATTRIBUTES_COLUMN_NAME, PARAM_ATTRIBUTES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		attributesColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto formDefinitionAttribute = new IdmFormAttributeDto(PARAM_FORM_DEFINITION_CODE, PARAM_FORM_DEFINITION_CODE,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(false);
		
		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setRequired(false);

		IdmFormAttributeDto systemNamePrefixAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_NAME_PREFIX, PARAM_SYSTEM_NAME_PREFIX,
				PersistentType.SHORTTEXT);
		systemNamePrefixAttribute.setRequired(false);
		
		IdmFormAttributeDto systemAttributePrefixAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_ATTRIBUTE_PREFIX, PARAM_SYSTEM_ATTRIBUTE_PREFIX,
				PersistentType.SHORTTEXT);
		systemAttributePrefixAttribute.setRequired(false);
		
		IdmFormAttributeDto systemAttributeValuePrefixAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_ATTRIBUTE_VALUE_PREFIX, PARAM_SYSTEM_ATTRIBUTE_VALUE_PREFIX,
				PersistentType.SHORTTEXT);
		systemAttributeValuePrefixAttribute.setRequired(false);
		
		IdmFormAttributeDto environment = new IdmFormAttributeDto(PARAM_ENVIRONMENT, PARAM_ENVIRONMENT,
				PersistentType.SHORTTEXT);
		environment.setRequired(false);
		
		IdmFormAttributeDto canBeRequestedAttribute = new IdmFormAttributeDto(PARAM_CAN_BE_REQUESTED, PARAM_CAN_BE_REQUESTED,
				PersistentType.BOOLEAN);
		canBeRequestedAttribute.setDefaultValue(String.valueOf(CAN_BE_REQUESTED));
		canBeRequestedAttribute.setFaceType(BaseFaceType.BOOLEAN_SELECT);
		canBeRequestedAttribute.setRequired(false);
		//
		formAttributes.addAll(Lists.newArrayList(rolesColumnNameAttribute, roleCodeColumnNameAttribute, descriptionColumnNameAttribute, 
				attributesColumnNameAttribute, criticalityColumnNameAttribute, guaranteeColumnNameAttribute, guaranteeTypeColumnNameAttribute, guaranteeTypeUpdateAttribute,
				guaranteeRolesColumnNameAttribute, guaranteeRoleTypeColumnNameAttribute, guaranteeRoleTypeUpdateAttribute, cataloguesColumnNameAttribute, subRolesColumnNameAttribute, 
				eavCodePrefixAttribute, eavValuePrefixAttribute, formDefinitionAttribute, multiValueSeparatorAttribute, 
				systemNamePrefixAttribute, systemAttributePrefixAttribute, systemAttributeValuePrefixAttribute, environment, canBeRequestedAttribute));
		return formAttributes;
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
