package eu.bcvsolutions.idm.extras.service.impl;

import com.google.common.collect.ImmutableMap;
import eu.bcvsolutions.idm.acc.domain.AccResultCode;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.*;
import eu.bcvsolutions.idm.acc.dto.filter.*;
import eu.bcvsolutions.idm.acc.repository.SysRoleSystemAttributeRepository;
import eu.bcvsolutions.idm.acc.service.api.*;
import eu.bcvsolutions.idm.core.api.exception.ResultCodeException;
import eu.bcvsolutions.idm.extras.service.api.ExtrasSyncRoleLdapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.util.List;
import java.util.UUID;

/**
 * Helping service for synchronization of roles from system ldap/AD with workflow
 * 
 * @author pstloukal
 *
 */
@Service
public class DefaultExtrasSyncRoleLdapService implements ExtrasSyncRoleLdapService {

	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSchemaObjectClassService schemaObjectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	

	@Autowired
	public DefaultExtrasSyncRoleLdapService(SysRoleSystemAttributeRepository repository) {
	}
	
	@Transactional
	@Override
	public SysRoleSystemAttributeDto addRoleMappingAttribute(UUID systemId, UUID roleId, String attributeName,
			String transformationScript, String objectClassName) { // ObjectClassName "__ACCOUNT__"
		Assert.notNull(systemId, "SystemId cannot be null!");
		Assert.notNull(roleId, "RoleId cannot be null!");
		Assert.notNull(attributeName, "Attribute name cannot be null");
		Assert.hasLength(attributeName, "Attribute name cannot be blank");

		UUID roleSystemId = getSysRoleSystem(systemId, roleId, objectClassName);
		if (roleSystemId == null) {
			roleSystemId = createSysRoleSystem(systemId, roleId, objectClassName);
		}
		SysRoleSystemAttributeDto systemAttribute = getSystemAttribute(roleSystemId, attributeName);
		if (systemAttribute == null) {
			systemAttribute = new SysRoleSystemAttributeDto();
		}
		
		//if role has unchanged transformationScript, do not save, or ControlledValues would be recalculated
		if (shouldBeScriptSaved(transformationScript, systemAttribute)) {
			systemAttribute.setEntityAttribute(false);
			systemAttribute.setStrategyType(AttributeMappingStrategyType.MERGE);
	
			UUID systemAttributeMappingId = getSystemAttributeMapping(systemId, attributeName, objectClassName).getId();
	
			systemAttribute.setName(attributeName);
			systemAttribute.setRoleSystem(roleSystemId);
			systemAttribute.setSystemAttributeMapping(systemAttributeMappingId);
			systemAttribute.setTransformScript(transformationScript);
			//
			return roleSystemAttributeService.save(systemAttribute);
		}
		return systemAttribute;
	}

	@Transactional
	@Override
	public SysSystemMappingDto getSystemMapping(UUID systemId, String objectClassName,
			SystemOperationType operationType) {
		Assert.notNull(systemId, "SystemId cannot be null!");
		Assert.notNull(objectClassName, "ObjectClassName cannot be null!");
		Assert.notNull(operationType, "OperationType cannot be null!");

		SysSystemMappingFilter filter = new SysSystemMappingFilter();
		filter.setSystemId(systemId);
		filter.setOperationType(operationType);
		filter.setObjectClassId(getObjectClassId(systemId, objectClassName));

		List<SysSystemMappingDto> systemMappings = systemMappingService.find(filter, null).getContent();
		if (systemMappings.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_MAPPING_NOT_FOUND,
					ImmutableMap.of("systemId", systemId, "objectClassName", objectClassName));
		}
		return systemMappings.get(0);
	}
	
	/**
	 * Set skip exclusion value of system attribute
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param skipValue
	 * @param objectClassName
	 */
	@Override
	public void setSkipValueIfExcluded(UUID systemId, UUID roleId, String attributeName, boolean skipValue, String objectClassName) {
		UUID roleSystemId = getSysRoleSystem(systemId, roleId, objectClassName);
		if (roleSystemId != null) {
			SysRoleSystemAttributeDto sysRoleSystemAttribute = getSystemAttribute(roleSystemId, attributeName);
			if (sysRoleSystemAttribute != null && sysRoleSystemAttribute.isSkipValueIfExcluded() != skipValue) {
				sysRoleSystemAttribute.setSkipValueIfExcluded(skipValue);
				roleSystemAttributeService.save(sysRoleSystemAttribute);
			}
        }
      }
	
	/**
	 * Returns true if value of system attribute on excluded contract is skiped
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param objectClassName
	 * @return
	 */
	@Override
	public boolean isSkipValueIfExcluded(UUID systemId, UUID roleId, String attributeName, String objectClassName) {
		UUID roleSystemId = getSysRoleSystem(systemId, roleId, objectClassName);
		if (roleSystemId != null) {
			SysRoleSystemAttributeDto sysRoleSystemAttribute = getSystemAttribute(roleSystemId, attributeName);
			if (sysRoleSystemAttribute != null) {
				return sysRoleSystemAttribute.isSkipValueIfExcluded();
			}
        }
		return false;
      }
	
	/**
	 * Set forward management of role system
	 * @param systemId
	 * @param roleId
	 * @param forwardManagement
	 * @param objectClassName
	 */
	@Override
	public void setForwardAccountManagement(UUID systemId, UUID roleId, boolean forwardManagement, String objectClassName) {
		UUID roleSystemId = getSysRoleSystem(systemId, roleId, objectClassName);
		if (roleSystemId != null) {
			SysRoleSystemDto sysRoleSystemDto = roleSystemService.get(roleSystemId);
			if(sysRoleSystemDto.isForwardAccountManagemen() != forwardManagement) {
				sysRoleSystemDto.setForwardAccountManagemen(forwardManagement);
				roleSystemService.save(sysRoleSystemDto);
			}
        }
      }
	
	/**
	 * Get forward management of role system 
	 * @param systemId
	 * @param roleId
	 * @param objectClassName
	 * @return
	 */
	@Override
	public boolean isForwardAccountManagement(UUID systemId, UUID roleId, String objectClassName) {
		UUID roleSystemId = getSysRoleSystem(systemId, roleId, objectClassName);
		if (roleSystemId != null) {
			return roleSystemService.get(roleSystemId).isForwardAccountManagemen();
        }
		return false;
      }
	
	
	/**
	 * Retuns true, if transform script in system attribute is different and should be changed
	 * @param transformationScript
	 * @param systemAttribute
	 * @return
	 */
	private boolean shouldBeScriptSaved(String transformationScript, SysRoleSystemAttributeDto systemAttribute) {
		return transformationScript != null && (systemAttribute.getTransformScript() == null || !transformationScript.equals(systemAttribute.getTransformScript()));
	}

	/**
	 * Returns existing role's system.
	 * 
	 * @param systemId
	 * @param roleId
	 * @param objectClassName
	 * @return
	 */
	private UUID getSysRoleSystem(UUID systemId, UUID roleId, String objectClassName) {
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setRoleId(roleId);
		filter.setSystemId(systemId);
		List<SysRoleSystemDto> roleSystem = roleSystemService.find(filter, null).getContent();
		if (roleSystem.size() == 1) {
			return roleSystem.stream().findFirst().get().getId();
		}
		return null;
	}
	
	/**
	 * Returns newly created role's system.
	 * 
	 * @param systemId
	 * @param roleId
	 * @param objectClassName
	 * @return
	 */
	private UUID createSysRoleSystem(UUID systemId, UUID roleId, String objectClassName) {
		SysRoleSystemDto sys = new SysRoleSystemDto();
		sys.setRole(roleId);
		sys.setSystem(systemId);
		sys.setSystemMapping(getSystemMapping(systemId, objectClassName, SystemOperationType.PROVISIONING).getId());
		return roleSystemService.save(sys).getId();
	}

	/**
	 * Returns systems object's scheme
	 * 
	 * @param systemId
	 * @param objectClassName
	 * @return
	 */
	private UUID getObjectClassId(UUID systemId, String objectClassName) {
		SysSchemaObjectClassFilter filter = new SysSchemaObjectClassFilter();
		filter.setSystemId(systemId);
		filter.setObjectClassName(objectClassName);
		List<SysSchemaObjectClassDto> objectClasses = schemaObjectClassService.find(filter, null).getContent();
		if (objectClasses.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_OBJECT_CLASS_NOT_FOUND,
					ImmutableMap.of("objectClassName", objectClassName, "systemId", systemId));
		}
		return objectClasses.get(0).getId();
	}

	/**
	 * Returns system's attribute mapping
	 * 
	 * @param systemId
	 * @param attributeName
	 * @param objectClassName
	 * @return
	 */
	private SysSystemAttributeMappingDto getSystemAttributeMapping(UUID systemId, String attributeName,
			String objectClassName) {
		SysSchemaAttributeDto schemaAttr = getSchemaAttr(systemId, attributeName, objectClassName);
		SysSystemAttributeMappingFilter filter = new SysSystemAttributeMappingFilter();
		filter.setSystemId(systemId);
		filter.setSchemaAttributeId(schemaAttr.getId());

		List<SysSystemAttributeMappingDto> attributeMappings = systemAttributeMappingService.find(filter, null)
				.getContent();
		if (attributeMappings.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_ATTRIBUTE_MAPPING_NOT_FOUND,
					ImmutableMap.of("schemaAttr", schemaAttr.getName(), "systemId", systemId));
		}
		return attributeMappings.get(0);
	}

	/**
	 * Returns schema attribute
	 * 
	 * @param systemId
	 * @param attributeName
	 * @param objectClassName
	 * @return
	 */
	private SysSchemaAttributeDto getSchemaAttr(UUID systemId, String attributeName, String objectClassName) {
		SysSchemaAttributeFilter filter = new SysSchemaAttributeFilter();
		filter.setObjectClassId(getObjectClassId(systemId, objectClassName));
		filter.setName(attributeName);
		List<SysSchemaAttributeDto> schemas = schemaAttributeService.find(filter, null).getContent();
		if (schemas.isEmpty()) {
			throw new ResultCodeException(AccResultCode.SYSTEM_SCHEMA_ATTRIBUTE_NOT_FOUND,
					ImmutableMap.of("objectClassName", objectClassName, "attributeName", attributeName));
		}
		return schemas.get(0);
	}

	/**
	 * Returns existing system attribute or null
	 * 
	 * @param attr
	 * @return
	 */
	private SysRoleSystemAttributeDto getSystemAttribute(UUID roleSystem, String attributeName) {
		SysRoleSystemAttributeFilter filter = new SysRoleSystemAttributeFilter();
		filter.setRoleSystemId(roleSystem);
		List<SysRoleSystemAttributeDto> content = roleSystemAttributeService.find(filter, null).getContent();
		for (SysRoleSystemAttributeDto attribute : content) {
			if (attribute.getName().equals(attributeName)) {
				return attribute;
			}
		}
		return null;
	}
}
