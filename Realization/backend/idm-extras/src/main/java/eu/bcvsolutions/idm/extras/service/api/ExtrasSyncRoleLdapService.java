package eu.bcvsolutions.idm.extras.service.api;

import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;

import java.util.UUID;

/**
 * Helping service for synchronization of roles from system ldap/AD with workflow
 * 
 * @author stlooukalp
 *
 */
public interface ExtrasSyncRoleLdapService{
	
	/**
	 * Method creates provisioning of attribute on role to system. 
	 * 
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param transformationScript
	 * @param objectClassName
	 */
	public SysRoleSystemAttributeDto addRoleMappingAttribute(UUID systemId, UUID roleId, String attributeName, String transformationScript,
			String objectClassName);
	
	/**
	 * Returns mapping of system
	 * 
	 * @param systemId
	 * @param objectClassName
	 * @return
	 */
	public SysSystemMappingDto getSystemMapping(UUID systemId, String objectClassName, SystemOperationType operationType);
	
	/**
	 * Get forward management of role system 
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param objectClassName
	 * @return
	 */
	public boolean isForwardAccountManagement(UUID systemId, UUID roleId, String objectClassName);
	
	/**
	 * Set forward management of role system
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param forwardManagement
	 * @param objectClassName
	 */
	public void setForwardAccountManagement(UUID systemId, UUID roleId, boolean forwardManagement, String objectClassName);
	
	/**
	 * Returns true if value of system attribute on excluded contract is skiped
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param objectClassName
	 * @return
	 */
	public boolean isSkipValueIfExcluded(UUID systemId, UUID roleId, String attributeName, String objectClassName);
	
	/**
	 * Set skip exclusion value of system attribute
	 * @param systemId
	 * @param roleId
	 * @param attributeName
	 * @param skipValue
	 * @param objectClassName
	 */
	public void setSkipValueIfExcluded(UUID systemId, UUID roleId, String attributeName, boolean skipValue, String objectClassName);
}
