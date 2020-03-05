package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
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
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.scheduler.api.service.AbstractSchedulableTaskExecutor;
import eu.bcvsolutions.idm.extras.domain.ExtrasResultCode;


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
 */
@Component
@Description("Get all roles on mapping - system and import from CSV to IDM")
public class ImportRolesFromCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportRolesFromCSVExecutor.class);

	public static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	public static final String PARAM_SYSTEM_NAME = "System name";
	public static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	public static final String PARAM_ROLE_CODE_COLUMN_NAME = "Column with role codes";
	public static final String PARAM_DESCRIPTION_COLUMN_NAME = "Column with description";
	public static final String PARAM_ATTRIBUTES_COLUMN_NAME = "Column with role attributes";
	public static final String PARAM_CRITICALITY_COLUMN_NAME = "Column with criticality";
	public static final String PARAM_GUARANTEE_COLUMN_NAME = "Column with guarantees";
	public static final String PARAM_GUARANTEE_ROLE_COLUMN_NAME = "Column with guarantee roles";
	public static final String PARAM_CATALOGUES_COLUMN_NAME = "Column with catalogue names";
	public static final String PARAM_SUBROLES_COLUMN_NAME = "Column with sub roles codes";
	public static final String PARAM_FORM_DEFINITION_CODE = "Form definition code";
	public static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	public static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	public static final String PARAM_MEMBER_OF_ATTRIBUTE = "MemberOf attribute name";
	public static final String PARAM_ENVIRONMENT = "Role environment";
	public static final String PARAM_CAN_BE_REQUESTED = "Can be requested";
	
	private List<UUID> cataloguesUuid = new ArrayList<>();
	private List<String> oldFormAttributesNames = new ArrayList<>();
	
	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator
	private static final Boolean CAN_BE_REQUESTED = Boolean.TRUE;
	private static final String OBJECT_CLASSNAME = "__ACCOUNT__";

	private UUID attachmentId;
	private String systemName;
	private String rolesColumnName;
	private String roleCodesColumnName;
	private String descriptionColumnName;
	private String attributesColumnName;
	private String criticalityColumnName;
	private String guaranteeColumnName;
	private String guaranteeRoleColumnName;
	private String catalogueColumnName;
	private String subRoleColumnName;
	private String formDefinitionCode;
	private String columnSeparator;
	private String multiValueSeparator;
	private String memberOfAttribute;
	private String environmentName;
	private Boolean canBeRequested;
	private Boolean hasRoleCodes;
	private Boolean hasDescription;
	private Boolean hasAttribute;
	private Boolean hasCriticality;
	private Boolean hasGuarantees;
	private Boolean hasGuaranteeRoles;
	private Boolean hasSystem;
	private Boolean hasEnvironment;
	private Boolean hasMemberOf;
	private Boolean hasCatalogue;
	private Boolean hasSubRoles;
		
	@Autowired
	private AttachmentManager attachmentManager;
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

	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		SysSystemDto system = new SysSystemDto();
		if(hasSystem) {
			system = findSystem();
		}

		// get data from CSV
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);
		
		InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
		
		CSVToIdM myParser = new CSVToIdM(attachmentData, rolesColumnName, roleCodesColumnName, descriptionColumnName, 
				attributesColumnName, criticalityColumnName, guaranteeColumnName, 
				guaranteeRoleColumnName, catalogueColumnName, subRoleColumnName, columnSeparator, multiValueSeparator, hasDescription, 
				hasAttribute, hasCriticality, hasGuarantees, hasGuaranteeRoles, hasCatalogue, hasSubRoles, hasRoleCodes);
		Map<String, String> roleDescriptions = myParser.getRoleDescriptions();
		Map<String, String> roleCodes = myParser.getRoleCodes();
		Map<String, List<String>> roleAttributes = myParser.getRoleAttributes();
		Map<String, String> criticalities = myParser.getCriticalities();
		Map<String, List<String>> guarantees = myParser.getGuarantees();
		Map<String, List<String>> guaranteeRoles = myParser.getGuaranteeRoles();
		Map<String, List<String>> catalogues = myParser.getCatalogues();
		Map<String, List<String>> subRoles = myParser.getSubRoles();

		// 
		if ((!roleDescriptions.isEmpty()) || (!roleAttributes.isEmpty()) || (!criticalities.isEmpty()) ||
				(!guarantees.isEmpty()) || (!guaranteeRoles.isEmpty())) {
			this.count = (long) roleDescriptions.size();
			this.counter = 0L;
			//**
			for (Map.Entry<String, String> entry : roleDescriptions.entrySet()) {
				String roleName = entry.getKey();
				String roleDescription = entry.getValue();
				
				// try to find the role
				String roleCodeToSearch = "";
				if(hasRoleCodes) {
					String rc = roleCodes.get(roleName);
					if(rc.equals("")) {
						roleCodeToSearch = roleNameToCode(roleName);
					} else {
						roleCodeToSearch = rc;
					}
				} else {
					roleCodeToSearch = roleNameToCode(roleName);
				}

				IdmRoleDto role = roleService.getByCode(roleCodeToSearch);
				if(role == null) {
					role = roleService.getByCode(roleNameToCode(roleName));
				}

				if (role == null) {
					role = createRole(roleName, system, roleDescription, roleAttributes.get(roleName), 
							criticalities.get(roleName), guarantees.get(roleName), guaranteeRoles.get(roleName), subRoles.get(roleName),
							roleCodeToSearch, environmentName);
				} else {
					updateRole(roleName, system, role, roleDescription, roleAttributes.get(roleName), criticalities.get(roleName),
							guarantees.get(roleName), guaranteeRoles.get(roleName), subRoles.get(roleName));
				}
				
				// creates role catalogues
				if(hasCatalogue) {
					List<String> cataloguesForRole = catalogues.get(roleName);
					for(String catalogue : cataloguesForRole) {
						if(catalogue != null && catalogue.trim().length() != 0) {
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
	 * @return
	 */
	private SysSystemDto findSystem() {
//		System.lineSeparator
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
	 * @return
	 */
	private IdmRoleDto createRole(String roleName, SysSystemDto system, String description, List<String> roleAttributes, 
			String criticality, List<String> guarantees, List<String> guaranteesRoles, List<String> subRoles,
				String roleCode, String environmentName) {
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
		if(hasAttribute) {
			if(!roleAttributes.get(0).trim().equals("")) {
				createAttribute(roleAttributes, role);
			}
		}
		
		// Set the guarantee
		if(hasGuarantees) {
			setGuarantees(guarantees, role);
		}
		
		// Set the guarantee by role
		if(hasGuaranteeRoles) {
			setGuaranteeRoles(guaranteesRoles, role);
		}
		
		// Create role system
		if(hasSystem) {
			SysSystemMappingDto systemMapping = getSystemMapping(system);
			SysRoleSystemDto roleSystem = new SysRoleSystemDto();
			roleSystem.setSystem(system.getId());
			roleSystem.setRole(role.getId());
			roleSystem.setSystemMapping(systemMapping.getId());
			roleSystemService.save(roleSystem);

			// Create role system mapping with role name	
			if(hasMemberOf) {
				String transformationScript = MessageFormat.format("\"{0}\"", roleName);		
				roleSystemAttributeService.addRoleMappingAttribute(system.getId(), role.getId(), memberOfAttribute, transformationScript, OBJECT_CLASSNAME);
			}
		}
		
		// Assign subroles
		if(hasSubRoles) {
			assignSubRoles(subRoles, role);
		}


		this.logItemProcessed(role, taskCompleted("Role " + roleName + " created"));
		return role;
	}
	
	/**
	 * Updates canBeRequested and description, role attributes, criticality and guarantees. Only criticality, 
	 * canBerequested and description can be deleted (by changing), nothing else is deleted. Description 
	 * will not be deleted if the new value is empty
	 * 
	 * @param role
	 * @param description
	 */
	private void updateRole(String roleName, SysSystemDto system, IdmRoleDto role, String description, List<String> roleAttributes, String criticality,
			List<String> guarantees, List<String> guaranteesRoles, List<String> subRoles) {
		Boolean canBeReqUpdated = Boolean.FALSE, descriptionUpdated = Boolean.FALSE;
		if (canBeRequested != null) {
			if(role.isCanBeRequested() != canBeRequested) {
				role.setCanBeRequested(canBeRequested);
				canBeReqUpdated = Boolean.TRUE;
			}
		}
		
		// update description if 1) the description is in CSV and 2) if it is different from the current description
		if (hasDescription && description.trim().length() != 0) {
			if (role.getDescription() == null || !role.getDescription().equals(description)) {
				role.setDescription(description);
				descriptionUpdated = Boolean.TRUE;
			}
		}

		
		// update the criticality
		Boolean criticalityUpdated = Boolean.FALSE;
		criticalityUpdated = setCriticality(criticality, role, Boolean.TRUE);
		
		// update the guarantee
		Boolean guaranteeUpdated = Boolean.FALSE;
		if(hasGuarantees) {
			guaranteeUpdated = setGuarantees(guarantees, role);
		}
		
		// update the guarantee by role
		Boolean guaranteeRoleUpdated = Boolean.FALSE;
		if(hasGuaranteeRoles) {
			guaranteeRoleUpdated = setGuaranteeRoles(guaranteesRoles, role);
		}
		
		// update attributes
		Boolean attributesUpdated = Boolean.FALSE;
		if(hasAttribute) {
			if(!roleAttributes.get(0).trim().equals("")) {
				createAttribute(roleAttributes, role);
			}
		}
		
		// update subroles
		Boolean subRolesUpdated = Boolean.FALSE;
		if(hasSubRoles) {
			subRolesUpdated = assignSubRoles(subRoles, role);
		}

		// update role system
		if(hasSystem) {
			SysSystemMappingDto systemMapping = getSystemMapping(system);
			SysRoleSystemDto roleSystem = new SysRoleSystemDto();
			roleSystem.setSystem(system.getId());
			roleSystem.setRole(role.getId());
			roleSystem.setSystemMapping(systemMapping.getId());
			roleSystemService.save(roleSystem);

			// update role system mapping with role name	
			if(hasMemberOf) {
				String transformationScript = MessageFormat.format("\"{0}\"", roleName);		
				roleSystemAttributeService.addRoleMappingAttribute(system.getId(), role.getId(), memberOfAttribute, transformationScript, OBJECT_CLASSNAME);
			}
		}
		
		roleService.save(role);

		if (canBeReqUpdated || descriptionUpdated || attributesUpdated || criticalityUpdated || 
				guaranteeUpdated || guaranteeRoleUpdated || subRolesUpdated) {
			role = roleService.save(role);
			this.logItemProcessed(role, taskCompleted("Role " + role.getName() + " updated"));			
		} else {
			this.logItemProcessed(role, taskNotCompleted("Nothing to update! Role name: " + roleName));
		}
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
		List<IdmRoleCompositionDto> currentSubRolesComp = roleCompositionService.findAllSubRoles(role.getId(), null);
		List<String> currentSubRoles = new ArrayList<>();
		if(currentSubRolesComp != null && currentSubRolesComp.size() != 0) {
			for (IdmRoleCompositionDto composition : currentSubRolesComp) {
				UUID uuid = composition.getSub();
				IdmRoleFilter rf = new IdmRoleFilter();
				rf.setId(uuid);
				IdmRoleDto current = roleService.find(rf, null, null).getContent().get(0);
				currentSubRoles.add(current.getCode());
			}
		}
		
		if(subRoles != null && subRoles.size() != 0) {
			for(String subRoleCode : subRoles) {
				if(subRoleCode.trim().length() > 0 && !currentSubRoles.contains(subRoleCode)) {
					// Find role to assign as subrole
					IdmRoleFilter roleFilter = new IdmRoleFilter();
					roleFilter.setBaseCode(subRoleCode);
					List<IdmRoleDto> foundSubRoles = roleService.find(roleFilter, null, null).getContent();
					if(foundSubRoles != null && foundSubRoles.size() != 0) {
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
	private Boolean setGuarantees(List<String> guarantees, IdmRoleDto role) {
		// Set the guarantee by identity
		// Find current guarantees
		Boolean updated = Boolean.FALSE;
		IdmRoleGuaranteeFilter filterGuaranteeRole = new IdmRoleGuaranteeFilter();
		filterGuaranteeRole.setRole(role.getId());
		List<IdmRoleGuaranteeDto> currentGarantsLinks = roleGuaranteeService.find(filterGuaranteeRole, null, null).getContent();
		List<String> currentGuaranteesLogin = new ArrayList<>((int) (currentGarantsLinks.size() / 0.75));
		for(IdmRoleGuaranteeDto garantLink : currentGarantsLinks) {
			IdmIdentityDto garant = identityService.get(garantLink.getGuarantee());
			currentGuaranteesLogin.add(garant.getUsername());
		}
		for(String guarantee : guarantees) {
			if(!guarantee.equals("")) {
				if(!currentGuaranteesLogin.contains(guarantee)) {
					// Finds the identity of the guarantee, only if the role doesn't yet have the guarantee set
					IdmIdentityDto identityGuarantee = identityService.getByUsername(guarantee);
					
					// Creates a guarantee and sets it to the role
					if(identityGuarantee != null) { 
						IdmRoleGuaranteeDto guar = new IdmRoleGuaranteeDto();
						guar.setGuarantee(identityGuarantee.getId());
						guar.setRole(role.getId());
						
						roleGuaranteeService.save(guar);
						updated = Boolean.TRUE;
					} else {
						logItemProcessed(role, taskNotCompleted("Role " + role.getCode() + " did not get a guarantee "
								+ guarantee + " set; no such identity was not found!"));
					}
				}
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
	private Boolean setGuaranteeRoles(List<String> guaranteesRoles, IdmRoleDto role) {
		// Set the guarantee by role
		// Find current role guarantees
		Boolean updated = Boolean.FALSE;
		IdmRoleGuaranteeRoleFilter filterRoleGuaranteeRole = new IdmRoleGuaranteeRoleFilter();
		filterRoleGuaranteeRole.setRole(role.getId());
		List<IdmRoleGuaranteeRoleDto> currentGarantsRoleLinks = roleGuaranteeRoleService.find(filterRoleGuaranteeRole, null, null).getContent();
		List<String> currentGuaranteesRoleCodes = new ArrayList<>((int) (currentGarantsRoleLinks.size() / 0.75));
		for(IdmRoleGuaranteeRoleDto garantRoleLink : currentGarantsRoleLinks) {
			IdmRoleDto garantRole = roleService.get(garantRoleLink.getGuaranteeRole());
			currentGuaranteesRoleCodes.add(garantRole.getCode());
		}
		
		for(String guaranteeRole : guaranteesRoles) {
			if(!guaranteeRole.equals("")) {
				if(!currentGuaranteesRoleCodes.contains(guaranteeRole)) {
					// Finds the role if the role doesn't have this guarantee role yet
					IdmRoleDto roleGuarantee = roleService.getByCode(guaranteeRole);
					
					// Creates a guarantee and sets it to the role
					if(roleGuarantee != null) {
						IdmRoleGuaranteeRoleDto guar = new IdmRoleGuaranteeRoleDto();
						guar.setGuaranteeRole(roleGuarantee.getId());
						guar.setRole(role.getId());
						
						roleGuaranteeRoleService.save(guar);
						updated = Boolean.TRUE;
					} else {
						logItemProcessed(role, taskNotCompleted("Role " + role.getCode() + " did not get a guarantee role "
								+ guaranteeRole + " set; no such role was not found!"));
					}
				}
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
			    	attr = formAttributeService.save(attr); 
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
			// TODO the code bellow deletes those attributes that the roles don't have in the source csv, 
			// uncomment if desired
//			else {
//				// if the role is not supposed to have an attribute, we check if it does have it, if so, we delete it
//				IdmRoleFormAttributeDto relatedRoleFormAttribute = allRolesParams.stream().filter(p -> {
//					IdmFormAttributeDto formAttributeDto = formAttributeService.get(p.getFormAttribute());
//					return formAttributeDto.getCode().equals(a.getName());
//				}).findFirst().orElse(null);
//				
//				if(relatedRoleFormAttribute != null) {
//					roleFormAttributeService.delete(relatedRoleFormAttribute);
//					updated = Boolean.TRUE;
//				}
//			}
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
		if(systemMappings.size()==0){
			// TODO improve exception - result code
			throw new IllegalArgumentException("Size of system mapping should not be 0.");
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
	    catRoleDto = roleCatalogueRoleService.save(catRoleDto);
	    role = roleService.save(role);
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

	@Override
	public void init(Map<String, Object> properties) {
		LOG.debug("Start init");
		super.init(properties);
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		roleCodesColumnName = getParameterConverter().toString(properties, PARAM_ROLE_CODE_COLUMN_NAME);
		descriptionColumnName = getParameterConverter().toString(properties, PARAM_DESCRIPTION_COLUMN_NAME);
		attributesColumnName = getParameterConverter().toString(properties, PARAM_ATTRIBUTES_COLUMN_NAME);
		criticalityColumnName = getParameterConverter().toString(properties, PARAM_CRITICALITY_COLUMN_NAME);
		guaranteeColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_COLUMN_NAME);
		guaranteeRoleColumnName = getParameterConverter().toString(properties, PARAM_GUARANTEE_ROLE_COLUMN_NAME);
		catalogueColumnName = getParameterConverter().toString(properties, PARAM_CATALOGUES_COLUMN_NAME);
		subRoleColumnName = getParameterConverter().toString(properties, PARAM_SUBROLES_COLUMN_NAME);
		formDefinitionCode = getParameterConverter().toString(properties, PARAM_FORM_DEFINITION_CODE);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		systemName = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME);
		environmentName = getParameterConverter().toString(properties, PARAM_ENVIRONMENT);
		memberOfAttribute = getParameterConverter().toString(properties, PARAM_MEMBER_OF_ATTRIBUTE);
		canBeRequested = getParameterConverter().toBoolean(properties, PARAM_CAN_BE_REQUESTED);
		// if not filled, init multiValueSeparator and check if csv has description
		if (multiValueSeparator == null) {
			multiValueSeparator = MULTI_VALUE_SEPARATOR;
		}
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
		if (guaranteeRoleColumnName != null) {
			hasGuaranteeRoles = Boolean.TRUE;
		} else {
			hasGuaranteeRoles = Boolean.FALSE;
		}
		if (systemName != null) {
			hasSystem = Boolean.TRUE;
		} else {
			hasSystem = Boolean.FALSE;
		}
		if (environmentName != null) {
			hasEnvironment = Boolean.TRUE;
		} else {
			hasEnvironment = Boolean.FALSE;
		}
		if (memberOfAttribute != null) {
			hasMemberOf = Boolean.TRUE;
		} else {
			hasMemberOf = Boolean.FALSE;
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
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_ROLE_CODE_COLUMN_NAME, roleCodesColumnName);
		props.put(PARAM_DESCRIPTION_COLUMN_NAME, descriptionColumnName);
		props.put(PARAM_ATTRIBUTES_COLUMN_NAME, attributesColumnName);
		props.put(PARAM_CRITICALITY_COLUMN_NAME, criticalityColumnName);
		props.put(PARAM_GUARANTEE_COLUMN_NAME, guaranteeColumnName);
		props.put(PARAM_GUARANTEE_ROLE_COLUMN_NAME, guaranteeRoleColumnName);
		props.put(PARAM_CATALOGUES_COLUMN_NAME, catalogueColumnName);
		props.put(PARAM_SUBROLES_COLUMN_NAME, subRoleColumnName);
		props.put(PARAM_FORM_DEFINITION_CODE, formDefinitionCode);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_SYSTEM_NAME, systemName);
		props.put(PARAM_ENVIRONMENT, environmentName);
		props.put(PARAM_MEMBER_OF_ATTRIBUTE, memberOfAttribute);
		props.put(PARAM_CAN_BE_REQUESTED, canBeRequested);
		return props;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		// csv file attachment
		IdmFormAttributeDto csvAttachment = new IdmFormAttributeDto(PARAM_CSV_ATTACHMENT, PARAM_CSV_ATTACHMENT,
				PersistentType.ATTACHMENT);
		csvAttachment.setRequired(true);
		if (attachmentId != null) {
			String attachmentIdString = attachmentId.toString();
			csvAttachment.setDefaultValue(attachmentIdString);
			csvAttachment.setPlaceholder(attachmentIdString);
		}
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		rolesColumnNameAttribute.setPlaceholder("The name of the column with the names of roles to import");
		
		IdmFormAttributeDto roleCodeColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLE_CODE_COLUMN_NAME, PARAM_ROLE_CODE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		roleCodeColumnNameAttribute.setRequired(false);
		roleCodeColumnNameAttribute.setPlaceholder("The name of the column with the codes of roles to import.");
		
		IdmFormAttributeDto descriptionColumnNameAttribute = new IdmFormAttributeDto(PARAM_DESCRIPTION_COLUMN_NAME, PARAM_DESCRIPTION_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(true);
		descriptionColumnNameAttribute.setPlaceholder("The name of the column with the description of roles");
	
		IdmFormAttributeDto attributesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ATTRIBUTES_COLUMN_NAME, PARAM_ATTRIBUTES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		attributesColumnNameAttribute.setRequired(false);
		attributesColumnNameAttribute.setPlaceholder("The name of the column with the role attributes");
		
		
		IdmFormAttributeDto criticalityColumnNameAttribute = new IdmFormAttributeDto(PARAM_CRITICALITY_COLUMN_NAME, PARAM_CRITICALITY_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		criticalityColumnNameAttribute.setRequired(false);
		criticalityColumnNameAttribute.setPlaceholder("The name of the column with role criticality values");
		
		IdmFormAttributeDto guaranteeColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_COLUMN_NAME, PARAM_GUARANTEE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeColumnNameAttribute.setRequired(false);
		guaranteeColumnNameAttribute.setPlaceholder("The name of the column with role guarantees by login");
		
		IdmFormAttributeDto guaranteeRolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_GUARANTEE_ROLE_COLUMN_NAME, PARAM_GUARANTEE_ROLE_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		guaranteeRolesColumnNameAttribute.setRequired(false);
		guaranteeRolesColumnNameAttribute.setPlaceholder("The name of the column with role guarantees by roles");
		
		IdmFormAttributeDto cataloguesColumnNameAttribute = new IdmFormAttributeDto(PARAM_CATALOGUES_COLUMN_NAME, PARAM_CATALOGUES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		cataloguesColumnNameAttribute.setRequired(false);
		cataloguesColumnNameAttribute.setPlaceholder("The name of the column with catalogue names");
		
		IdmFormAttributeDto subRolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_SUBROLES_COLUMN_NAME, PARAM_SUBROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		subRolesColumnNameAttribute.setRequired(false);
		subRolesColumnNameAttribute.setPlaceholder("The name of the column with subordinate roles codes");
		
		IdmFormAttributeDto formDefinitionAttribute = new IdmFormAttributeDto(PARAM_FORM_DEFINITION_CODE, PARAM_FORM_DEFINITION_CODE,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(false);
		descriptionColumnNameAttribute.setPlaceholder("If you are importing role attributes, set the code of the definition");
		
		IdmFormAttributeDto columnSeparatorAttribute = new IdmFormAttributeDto(PARAM_COLUMN_SEPARATOR, PARAM_COLUMN_SEPARATOR,
				PersistentType.CHAR);
		columnSeparatorAttribute.setDefaultValue(COLUMN_SEPARATOR);
		columnSeparatorAttribute.setRequired(true);
		
		IdmFormAttributeDto multiValueSeparatorAttribute = new IdmFormAttributeDto(PARAM_MULTI_VALUE_SEPARATOR, PARAM_MULTI_VALUE_SEPARATOR,
				PersistentType.CHAR);
		multiValueSeparatorAttribute.setRequired(false);
		multiValueSeparatorAttribute.setPlaceholder("Default: new line");
		
		IdmFormAttributeDto systemNameAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_NAME, PARAM_SYSTEM_NAME,
				PersistentType.SHORTTEXT);
		systemNameAttribute.setRequired(false);
		systemNameAttribute.setPlaceholder("The name of your system, leave empty if you don't want to set a system");
		
		IdmFormAttributeDto memberOfAttribute = new IdmFormAttributeDto(PARAM_MEMBER_OF_ATTRIBUTE, PARAM_MEMBER_OF_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		memberOfAttribute.setRequired(false);
		memberOfAttribute.setPlaceholder("Leave empty if you don't want to set a memberOf");
		
		IdmFormAttributeDto environment = new IdmFormAttributeDto(PARAM_ENVIRONMENT, PARAM_ENVIRONMENT,
				PersistentType.SHORTTEXT);
		environment.setRequired(false);
		environment.setPlaceholder("The name of the environment to which you want to add the roles.");
		
		IdmFormAttributeDto canBeRequestedAttribute = new IdmFormAttributeDto(PARAM_CAN_BE_REQUESTED, PARAM_CAN_BE_REQUESTED,
				PersistentType.BOOLEAN);
		canBeRequestedAttribute.setDefaultValue(String.valueOf(CAN_BE_REQUESTED));
		canBeRequestedAttribute.setFaceType(BaseFaceType.BOOLEAN_SELECT);
		canBeRequestedAttribute.setRequired(false);
		//
		return Lists.newArrayList(csvAttachment, rolesColumnNameAttribute, roleCodeColumnNameAttribute, descriptionColumnNameAttribute, 
				attributesColumnNameAttribute, criticalityColumnNameAttribute, guaranteeColumnNameAttribute, 
				guaranteeRolesColumnNameAttribute, cataloguesColumnNameAttribute, subRolesColumnNameAttribute, 
				formDefinitionAttribute, columnSeparatorAttribute, multiValueSeparatorAttribute, 
				systemNameAttribute, memberOfAttribute, environment, canBeRequestedAttribute);
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
