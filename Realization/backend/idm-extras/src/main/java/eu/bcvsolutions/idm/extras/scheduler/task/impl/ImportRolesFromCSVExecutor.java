package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import java.io.InputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.hibernate.mapping.Array;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.data.domain.Page;

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
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleFormAttributeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFormAttributeFilter;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleFormAttributeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
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
 * @author Tomas Doischer
 * @author Marek Klement
 * @author Petr Hanák
 */
@Component
@Description("Get all roles on mapping - system and import from CSV to IDM")
public class ImportRolesFromCSVExecutor extends AbstractSchedulableTaskExecutor<OperationResult> {

	private static final Logger LOG = LoggerFactory.getLogger(ImportRolesFromCSVExecutor.class);

	public static final String PARAM_CSV_ATTACHMENT = "Import csv file";
	public static final String PARAM_SYSTEM_NAME = "System name";
	public static final String PARAM_ROLES_COLUMN_NAME = "Column with roles";
	public static final String PARAM_DESCRIPTION_COLUMN_NAME = "Column with description";
	public static final String PARAM_ATTRIBUTES_COLUMN_NAME = "Column with attributes";
	public static final String PARAM_FORM_DEFINITION_CODE = "Form definition code";
	public static final String PARAM_COLUMN_SEPARATOR = "Column separator";
	public static final String PARAM_MULTI_VALUE_SEPARATOR = "Multi value separator";
	public static final String PARAM_MEMBER_OF_ATTRIBUTE = "MemberOf attribute name";
	public static final String PARAM_CAN_BE_REQUESTED = "Can be requested";
	
	// Defaults
	private static final String COLUMN_SEPARATOR = ";";
	private static final String MULTI_VALUE_SEPARATOR = "\\r?\\n"; // new line separator
	private static final String MEMBER_OF_ATTRIBUTE = "rights";
	private static final Boolean CAN_BE_REQUESTED = true;
	private static final String OBJECT_CLASSNAME = "__ACCOUNT__";

	private UUID attachmentId;
	private String systemName;
	private String rolesColumnName;
	private String descriptionColumnName;
	private String attributesColumnName;
	private String formDefinitionCode;
	private String columnSeparator;
	private String multiValueSeparator;
	private String memberOfAttribute;
	private Boolean canBeRequested;
	private Boolean hasDescription;
	private Boolean hasAttribute;
		
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

	@Override
	public OperationResult process() {
		LOG.debug("Start process");
		SysSystemDto system = findSystem();
		if (!attachmentManager.get(attachmentId).getMimetype().contains("text/csv")) {
			throw new ResultCodeException(ExtrasResultCode.WRONG_FILE_FORMAT);
		}
		// zkusit nasetovat id LRT do owner ID
		IdmAttachmentDto attachment = attachmentManager.get(attachmentId);
		System.out.println(attachment.getOwnerId());
		attachment.setOwnerId(this.getScheduledTaskId());
		attachmentManager.save(attachment);
		System.out.println(attachment.getOwnerId());
		
		InputStream attachmentData = attachmentManager.getAttachmentData(attachmentId);
		
		CSVToIdM myParser = new CSVToIdM(attachmentData, rolesColumnName, descriptionColumnName, attributesColumnName, columnSeparator, multiValueSeparator, hasDescription, hasAttribute);
		Map<String, String> roleDescriptions = myParser.getRoleDescriptions();
		Map<String, List<String>> roleAttributes = myParser.getRoleAttributes();

		if ((!roleDescriptions.isEmpty()) || (!roleAttributes.isEmpty())) {
			this.count = (long) roleDescriptions.size();
			this.counter = 0L;

			UUID catalogueId = createCatalogue(rolesColumnName);
			for (String roleName : roleDescriptions.keySet()) {
				IdmRoleDto role = roleService.getByCode(roleNameToCode(roleName));
				if (role == null) {
					role = createRole(roleName, system, roleDescriptions.get(roleName), roleAttributes.get(roleName));
				} else {
					updateRole(role, roleDescriptions.get(roleName), roleAttributes.get(roleName));
				}
				if (!roleIsInCatalogue(role.getId(), catalogueId)) {
					addRoleToCatalogue(role, catalogueId);
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
	 * assigns system to role
	 * 
	 * @param roleName
	 * @param system
	 * @param description
	 * @return
	 */
	private IdmRoleDto createRole(String roleName, SysSystemDto system, String description, List<String> roleAttributes) {
		// Create role
		IdmRoleDto role = new IdmRoleDto();
		role.setCode(roleNameToCode(roleName));
		role.setName(roleName);
		role.setPriority(0);
		
		role.setCanBeRequested(canBeRequested);
		if (hasDescription) {
			role.setDescription(description);
		}
		
		role = roleService.save(role);
		
		// Create the attributes
		if(hasAttribute) {
			if(!roleAttributes.isEmpty()) {
				createAttribute(roleAttributes, role);
			} 	       
		}

		// Create role system
		SysSystemMappingDto systemMapping = getSystemMapping(system);
		SysRoleSystemDto roleSystem = new SysRoleSystemDto();
		roleSystem.setSystem(system.getId());
		roleSystem.setRole(role.getId());
		roleSystem.setSystemMapping(systemMapping.getId());
		roleSystemService.save(roleSystem);

		// Create role system mapping with role name		
		String transformationScript = MessageFormat.format("\"{0}\"", roleName);		
		roleSystemAttributeService.addRoleMappingAttribute(system.getId(), role.getId(), memberOfAttribute, transformationScript, OBJECT_CLASSNAME);

		this.logItemProcessed(role, taskCompleted("Role " + roleName + " created"));
		return role;
	}

	private Boolean createAttribute(List<String> roleAttributes, IdmRoleDto role) {
		Boolean updated = false;
		IdmFormDefinitionDto def = formService.getDefinition(IdmIdentityRole.class, formDefinitionCode);
		for(String roleAttribute : roleAttributes) {
			if((roleAttribute.trim().length() == 0) || (roleAttribute == null)) {
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
		    	List<String> oldFormAttributesNames = new ArrayList<>();
			    for(IdmFormAttributeDto attr : oldFormAttributes) {
			    	// gets names of all of the old attributes
			    	oldFormAttributesNames.add(attr.getName());
			    }
			    
			    if(!oldFormAttributesNames.contains(roleAttribute)) {
			    	newFormAttributes.add(roleIdEav);
			    }
			    	    
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
					updated = true;
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
//					updated = true;
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
	 * Updates canBeRequested and description
	 * 
	 * @param role
	 * @param description
	 */
	private void updateRole(IdmRoleDto role, String description, List<String> roleAttributes) {
		Boolean canBeReqUpdated = false, descriptionUpdated = false;
		if (role.isCanBeRequested() != canBeRequested) {
			role.setCanBeRequested(canBeRequested);
			canBeReqUpdated = true;
		}
		if (role.getDescription() != null && !role.getDescription().equals(description)) { 
			role.setDescription(description);
			descriptionUpdated = true;
		}
		
		Boolean updated = false;
		if(hasAttribute) {
			updated = createAttribute(roleAttributes, role);
		}
		
		roleService.save(role);

		if (canBeReqUpdated || descriptionUpdated || updated) {
			role = roleService.save(role);
			this.logItemProcessed(role, taskCompleted("Role " + role.getName() + " updated"));			
		} else {
			this.logItemProcessed(role, taskNotCompleted("Nothing to update! Role name: " + role.getName()));
		}
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
		attachmentId = getParameterConverter().toUuid(properties, PARAM_CSV_ATTACHMENT);
		rolesColumnName = getParameterConverter().toString(properties, PARAM_ROLES_COLUMN_NAME);
		descriptionColumnName = getParameterConverter().toString(properties, PARAM_DESCRIPTION_COLUMN_NAME);
		attributesColumnName = getParameterConverter().toString(properties, PARAM_ATTRIBUTES_COLUMN_NAME);
		formDefinitionCode = getParameterConverter().toString(properties, PARAM_FORM_DEFINITION_CODE);
		columnSeparator = getParameterConverter().toString(properties, PARAM_COLUMN_SEPARATOR);
		multiValueSeparator = getParameterConverter().toString(properties, PARAM_MULTI_VALUE_SEPARATOR);
		systemName = getParameterConverter().toString(properties, PARAM_SYSTEM_NAME);
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
		
		if (attributesColumnName != null) {
			hasAttribute = true;
		} else {
			hasAttribute = false;
		}
	}

	@Override
	public Map<String, Object> getProperties() {
		LOG.debug("Start getProperties");
		Map<String, Object> props = super.getProperties();
		props.put(PARAM_CSV_ATTACHMENT, attachmentId);
		props.put(PARAM_ROLES_COLUMN_NAME, rolesColumnName);
		props.put(PARAM_DESCRIPTION_COLUMN_NAME, descriptionColumnName);
		props.put(PARAM_ATTRIBUTES_COLUMN_NAME, attributesColumnName);
		props.put(PARAM_FORM_DEFINITION_CODE, formDefinitionCode);
		props.put(PARAM_COLUMN_SEPARATOR, columnSeparator);
		props.put(PARAM_MULTI_VALUE_SEPARATOR, multiValueSeparator);
		props.put(PARAM_SYSTEM_NAME, systemName);
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
			csvAttachment.setDefaultValue(attachmentId.toString());
			csvAttachment.setPlaceholder(attachmentId.toString());
		}
		IdmFormAttributeDto rolesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ROLES_COLUMN_NAME, PARAM_ROLES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		rolesColumnNameAttribute.setRequired(true);
		
		IdmFormAttributeDto descriptionColumnNameAttribute = new IdmFormAttributeDto(PARAM_DESCRIPTION_COLUMN_NAME, PARAM_DESCRIPTION_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(false);
	
		IdmFormAttributeDto attributesColumnNameAttribute = new IdmFormAttributeDto(PARAM_ATTRIBUTES_COLUMN_NAME, PARAM_ATTRIBUTES_COLUMN_NAME,
				PersistentType.SHORTTEXT);
		descriptionColumnNameAttribute.setRequired(false);
		IdmFormAttributeDto formDefinitionAttribute = new IdmFormAttributeDto(PARAM_FORM_DEFINITION_CODE, PARAM_FORM_DEFINITION_CODE,
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
		IdmFormAttributeDto systemNameAttribute = new IdmFormAttributeDto(PARAM_SYSTEM_NAME, PARAM_SYSTEM_NAME,
				PersistentType.SHORTTEXT);
		systemNameAttribute.setRequired(true);
		IdmFormAttributeDto memberOfAttribute = new IdmFormAttributeDto(PARAM_MEMBER_OF_ATTRIBUTE, PARAM_MEMBER_OF_ATTRIBUTE,
				PersistentType.SHORTTEXT);
		memberOfAttribute.setDefaultValue(MEMBER_OF_ATTRIBUTE);
		memberOfAttribute.setRequired(true);
		IdmFormAttributeDto canBeRequestedAttribute = new IdmFormAttributeDto(PARAM_CAN_BE_REQUESTED, PARAM_CAN_BE_REQUESTED,
				PersistentType.BOOLEAN);
		canBeRequestedAttribute.setDefaultValue(String.valueOf(CAN_BE_REQUESTED));
		canBeRequestedAttribute.setRequired(false);
		//
		return Lists.newArrayList(csvAttachment, rolesColumnNameAttribute, descriptionColumnNameAttribute, attributesColumnNameAttribute, formDefinitionAttribute, columnSeparatorAttribute, multiValueSeparatorAttribute, systemNameAttribute, memberOfAttribute, canBeRequestedAttribute);
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
