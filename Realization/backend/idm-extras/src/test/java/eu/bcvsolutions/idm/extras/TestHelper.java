package eu.bcvsolutions.idm.extras;

import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.dto.*;
import eu.bcvsolutions.idm.acc.entity.SysSystem;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.ic.service.api.IcConnectorFacade;

import java.util.UUID;

/**
 * Reuses core TestHelper and adds acc spec. methods
 * Copy from acc module
 *
 * @author Peter Sourek <peter.sourek@bcvsolutions.eu>
 */
public interface TestHelper extends eu.bcvsolutions.idm.test.api.TestHelper {

	String ATTRIBUTE_MAPPING_NAME = "__NAME__";
	String ATTRIBUTE_MAPPING_ENABLE = "__ENABLE__";
	String ATTRIBUTE_MAPPING_PASSWORD = IcConnectorFacade.PASSWORD_ATTRIBUTE_NAME;
	String ATTRIBUTE_MAPPING_FIRSTNAME = "FIRSTNAME";
	String ATTRIBUTE_MAPPING_LASTNAME = "LASTNAME";
	String ATTRIBUTE_MAPPING_EMAIL = "EMAIL";

	/**
	 * Prepares conntector and system for fiven table name. Test database is used.
	 * Generated system name will be used.
	 *
	 * @param tableName
	 *            see {@link AbstractTestResource#TABLE_NAME}
	 * @return
	 */
	SysSystemDto createSystem(String tableName);

	/**
	 * Prepares conntector and system for fiven table name. Test database is used.
	 *
	 * @param tableName
	 *            see {@link AbstractTestResource#TABLE_NAME}
	 * @param systemName
	 * @return
	 */
	SysSystemDto createSystem(String tableName, String systemName);

	/**
	 * Prepares conntector and system for fiven table name. Test database is used.
	 * @param tableName
	 * @param systemName
	 * @param statusColumnName
	 * @param keyColumnName
	 * @param changeLogColumnName
	 * @return
	 */
	SysSystemDto createSystem(String tableName, String systemName, String statusColumnName, String keyColumnName, String changeLogColumnName);

	/**
	 * Creates system for {@link AbstractTestResource} with schema generated by given table.
	 * Test database is used. Generated system name will be used.
	 *
	 * @see AbstractTestResource#TABLE_NAME
	 * @param withMapping
	 *            default mapping will be included
	 * @return
	 */
	SysSystemDto createTestResourceSystem(boolean withMapping);

	/**
	 * Creates system for {@link AbstractTestResource} with schema generated by given table.
	 * Test database is used.
	 *
	 * see AbstractTestResource#TABLE_NAME
	 *
	 * @param withMapping
	 *            default mapping will be included
	 * @param systemName
	 * @return
	 */
	SysSystemDto createTestResourceSystem(boolean withMapping, String systemName);

	/**
	 * Creates default provisioning mapping for the given system
	 *
	 * @param system
	 * @return
	 */
	SysSystemMappingDto createMapping(SysSystemDto system);

	/**
	 * Returns default mapping - provisioning, identity
	 *
	 * @see #createSystem(String, boolean)
	 * @param system
	 * @return
	 */
	SysSystemMappingDto getDefaultMapping(SysSystemDto system);

	/**
	 * Returns default mapping - provisioning, identity
	 *
	 * @see #createSystem(String, boolean)
	 * @param systemId
	 * @return
	 */
	SysSystemMappingDto getDefaultMapping(UUID systemId);

	/**
	 * Assing system to given role with default mapping (provisioning, identity)
	 *
	 * @see #getDefaultMapping(SysSystem)
	 * @param role
	 * @param system
	 * @return
	 */
	SysRoleSystemDto createRoleSystem(IdmRoleDto role, SysSystemDto system);

	/**
	 * Find account on target system
	 *
	 * @param uid
	 * @return
	 */
	TestResource findResource(String uid);

	/**
	 * Delete all data from the target system
	 */
	void deleteAllResourceData();

	/**
	 * Saves resource on target system
	 *
	 * TODO: support merge (persist only now)
	 *
	 * @param uid
	 * @return
	 */
	TestResource saveResource(TestResource testResource);

	/**
	 * Creates system entity (IDENTITY) with random name on given system
	 *
	 * @param system
	 * @return
	 */
	SysSystemEntityDto createSystemEntity(SysSystemDto system);

	/**
	 * Create {@link AccAccountDto} and {@link AccIdentityAccountDto} for given
	 * system and identity.
	 *
	 * @param system
	 * @param identity
	 * @return
	 */
	AccIdentityAccountDto createIdentityAccount(SysSystemDto system, IdmIdentityDto identity);

	/**
	 * Create {@link AccAccountDto} and {@link AccIdentityAccountDto} for given
	 * system and identity.
	 *
	 * @param type
	 * @param objectClass
	 * @return
	 */
	SysSystemMappingDto createMappingSystem(SystemEntityType type, SysSchemaObjectClassDto objectClass);

	/**
	 * Start synchronization by given sync config
	 *
	 * @param syncConfigCustom
	 */
	void startSynchronization(AbstractSysSyncConfigDto syncConfigCustom);

}
