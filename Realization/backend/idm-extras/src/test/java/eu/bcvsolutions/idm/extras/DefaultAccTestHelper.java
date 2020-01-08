package eu.bcvsolutions.idm.extras;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.sql.DataSource;

import org.flywaydb.core.internal.exception.FlywaySqlException;
import org.flywaydb.core.internal.jdbc.JdbcUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import com.google.common.collect.ImmutableMap;
import com.zaxxer.hikari.HikariDataSource;

import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.scheduler.task.impl.SynchronizationSchedulableTaskExecutor;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.AccIdentityAccountService;
import eu.bcvsolutions.idm.acc.service.api.SynchronizationService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.acc.service.impl.DefaultSysSystemMappingService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.exception.CoreException;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import joptsimple.internal.Strings;

/**
 * Acc / Provisioning test helper
 *
 * @author Radek Tomiška
 */
@Primary
@Component("accTestHelper")
public class DefaultAccTestHelper extends eu.bcvsolutions.idm.test.api.DefaultTestHelper implements TestHelper {

	@Autowired private EntityManager entityManager;
	@Autowired private SysSystemService systemService;
	@Autowired private SysSystemMappingService systemMappingService;
	@Autowired private SysSystemAttributeMappingService systemAttributeMappingService;
	@Autowired private SysSchemaAttributeService schemaAttributeService;
	@Autowired private SysRoleSystemService roleSystemService;
	@Autowired private FormService formService;
	@Autowired private DataSource dataSource;
	@Autowired private SysSystemEntityService systemEntityService;
	@Autowired private AccAccountService accountService;
	@Autowired private AccIdentityAccountService identityAccountService;
	@Autowired private DefaultSysSystemMappingService mappingService;
	@Autowired private ApplicationContext context;

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TestResource findResource(String uid) {
		return entityManager.find(TestResource.class, uid);
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public TestResource saveResource(TestResource testResource) {
		entityManager.persist(testResource);
		//
		return testResource;
	}


	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestResource.TABLE_NAME);
		q.executeUpdate();
	}

	/**
	 * Create test system connected to same database (using configuration from dataSource)
	 * Generated system name will be used.
	 *
	 * @return
	 */
	@Override
	public SysSystemDto createSystem(String tableName) {
		return createSystem(tableName, null);
	}

	/**
	 *
	 *
	 * @param tableName
	 * @param systemName
	 * @return
	 */
	@Override
	public SysSystemDto createSystem(String tableName, String systemName) {
		return this.createSystem(tableName, systemName, "status", "name");
	}

	/**
	 *
	 *
	 * @param tableName
	 * @param systemName
	 * @return
	 */
	@Override
	@SuppressWarnings("deprecation")
	public SysSystemDto createSystem(String tableName, String systemName, String statusColumnName, String keyColumnName) {
		// create owner
		SysSystemDto system = new SysSystemDto();
		system.setName(systemName == null ? tableName + "_" + System.currentTimeMillis() : systemName);

		system.setConnectorKey(new SysConnectorKeyDto(systemService.getTestConnectorKey()));

		system = systemService.save(system);

		IdmFormDefinitionDto savedFormDefinition = systemService.getConnectorFormDefinition(system.getConnectorInstance());

		List<IdmFormValueDto> values = new ArrayList<>();

		IdmFormValueDto jdbcUrlTemplate = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcUrlTemplate"));
		jdbcUrlTemplate.setValue(((HikariDataSource) dataSource).getJdbcUrl());
		values.add(jdbcUrlTemplate);
		IdmFormValueDto jdbcDriver = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("jdbcDriver"));
		jdbcDriver.setValue(((HikariDataSource) dataSource).getDriverClassName());
		values.add(jdbcDriver);

		IdmFormValueDto user = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("user"));
		user.setValue(((HikariDataSource) dataSource).getUsername());
		values.add(user);
		IdmFormValueDto password = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("password"));
		password.setValue(((HikariDataSource) dataSource).getPassword());
		values.add(password);
		IdmFormValueDto table = new IdmFormValueDto(savedFormDefinition.getMappedAttributeByCode("table"));
		table.setValue(tableName);
		values.add(table);
		if(!Strings.isNullOrEmpty(keyColumnName)) {
			IdmFormValueDto keyColumn = new IdmFormValueDto(
					savedFormDefinition.getMappedAttributeByCode("keyColumn"));
			keyColumn.setValue(keyColumnName);
			values.add(keyColumn);
		}
		IdmFormValueDto passwordColumn = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("passwordColumn"));
		passwordColumn.setValue("password");
		values.add(passwordColumn);
		IdmFormValueDto allNative = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("allNative"));
		allNative.setValue(true);
		values.add(allNative);
		IdmFormValueDto rethrowAllSQLExceptions = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("rethrowAllSQLExceptions"));
		rethrowAllSQLExceptions.setValue(true);
		values.add(rethrowAllSQLExceptions);
		if(!Strings.isNullOrEmpty(statusColumnName)) {
			IdmFormValueDto statusColumn = new IdmFormValueDto(
					savedFormDefinition.getMappedAttributeByCode("statusColumn"));
			statusColumn.setValue(statusColumnName);
			values.add(statusColumn);
		}
		IdmFormValueDto disabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("disabledStatusValue"));
		disabledStatusValue.setValue("disabled");
		values.add(disabledStatusValue);
		IdmFormValueDto enabledStatusValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("enabledStatusValue"));
		enabledStatusValue.setValue("enabled");
		values.add(enabledStatusValue);
		IdmFormValueDto changeLogColumnValue = new IdmFormValueDto(
				savedFormDefinition.getMappedAttributeByCode("changeLogColumn"));
		changeLogColumnValue.setValue(null);
		values.add(changeLogColumnValue);

		formService.saveValues(system, savedFormDefinition, values);

		return system;
	}

	@Override
	public SysSystemDto createTestResourceSystem(boolean withMapping) {
		return createTestResourceSystem(withMapping, null);
	}

	@Override
	public SysSystemDto createTestResourceSystem(boolean withMapping, String systemName) {
		// create test system
		SysSystemDto system = createSystem(TestResource.TABLE_NAME, systemName);
		//
		if (withMapping) {
			createMapping(system);
		}
		//
		return system;
	}

	@Override
	public SysSystemMappingDto createMapping(SysSystemDto system) {
		//
		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		//
		SysSystemMappingDto systemMapping = new SysSystemMappingDto();
		systemMapping.setName("default_" + System.currentTimeMillis());
		systemMapping.setEntityType(SystemEntityType.IDENTITY);
		systemMapping.setOperationType(SystemOperationType.PROVISIONING);
		systemMapping.setObjectClass(objectClasses.get(0).getId());
		systemMapping = systemMappingService.save(systemMapping);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		for(SysSchemaAttributeDto schemaAttr : schemaAttributesPage) {
			if (ATTRIBUTE_MAPPING_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName(IdmIdentity_.username.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_ENABLE.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(false);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("disabled");
				attributeMapping.setTransformToResourceScript("return String.valueOf(!attributeValue);");
				attributeMapping.setTransformFromResourceScript("return String.valueOf(attributeValue);");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_PASSWORD.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName("password");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setPasswordAttribute(true);
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_FIRSTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.firstName.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_LASTNAME.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.lastName.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			} else if (ATTRIBUTE_MAPPING_EMAIL.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setIdmPropertyName(IdmIdentity_.email.getName());
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(systemMapping.getId());
				systemAttributeMappingService.save(attributeMapping);
			}
		}
		return systemMapping;
	}

	@Override
	public SysSystemMappingDto getDefaultMapping(SysSystemDto system) {
		Assert.notNull(system, "System is required to get mapping.");
		//
		return getDefaultMapping(system.getId());
	}

	@Override
	public SysSystemMappingDto getDefaultMapping(UUID systemId) {
		List<SysSystemMappingDto> mappings = systemMappingService.findBySystemId(systemId, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		if(mappings.isEmpty()) {
			throw new CoreException(String.format("Default mapping for system[%s] not found", systemId));
		}
		//
		return mappings.get(0);
	}

	@Override
	public SysRoleSystemDto createRoleSystem(IdmRoleDto role, SysSystemDto system) {
		SysRoleSystemDto roleSystem = new SysRoleSystemDto();
		roleSystem.setRole(role.getId());
		roleSystem.setSystem(system.getId());
		// default mapping
		List<SysSystemMappingDto> mappings = systemMappingService.findBySystem(system, SystemOperationType.PROVISIONING, SystemEntityType.IDENTITY);
		// required ...
		roleSystem.setSystemMapping(mappings.get(0).getId());
		//
		return roleSystemService.save(roleSystem);
	}

	@Override
	public SysSystemEntityDto createSystemEntity(SysSystemDto system) {
		SysSystemEntityDto systemEntity = new SysSystemEntityDto(createName(), SystemEntityType.IDENTITY);
		systemEntity.setSystem(system.getId());
		systemEntity.setWish(true);
		return systemEntityService.save(systemEntity);
	}

	@Override
	public AccIdentityAccountDto createIdentityAccount(SysSystemDto system, IdmIdentityDto identity) {
		AccAccountDto account = new AccAccountDto();
		account.setSystem(system.getId());
		account.setUid(identity.getUsername());
		account.setAccountType(AccountType.PERSONAL);
		account.setEntityType(SystemEntityType.IDENTITY);
		account = accountService.save(account);

		AccIdentityAccountDto accountIdentity = new AccIdentityAccountDto();
		accountIdentity.setIdentity(identity.getId());
		accountIdentity.setOwnership(true);
		accountIdentity.setAccount(account.getId());

		return identityAccountService.save(accountIdentity);
	}

	@Override
	public SysSystemMappingDto createMappingSystem(SystemEntityType type, SysSchemaObjectClassDto objectClass) {
		// system mapping
		SysSystemMappingDto mapping = new SysSystemMappingDto();
		mapping.setName(createName());
		mapping.setEntityType(type);
		mapping.setObjectClass(objectClass.getId());
		mapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		//
		return mappingService.save(mapping);
	}

	@Override
	public void startSynchronization(AbstractSysSyncConfigDto config) {
		Assert.notNull(config, "Sync config is required to be start.");
		SynchronizationSchedulableTaskExecutor lrt = context.getAutowireCapableBeanFactory()
				.createBean(SynchronizationSchedulableTaskExecutor.class);
		lrt.init(ImmutableMap.of(SynchronizationService.PARAMETER_SYNCHRONIZATION_ID, config.getId().toString()));
		lrt.process();
	}

	/**
	 * Schema is generated in lower case for postgresql.
	 *
	 * @param columnName
	 * @return
	 */
	public String getSchemaColumnName(String columnName) {
		if (columnName.equals(ATTRIBUTE_MAPPING_NAME)
				|| columnName.equals(ATTRIBUTE_MAPPING_ENABLE)
				|| columnName.equals(ATTRIBUTE_MAPPING_PASSWORD)) {
			// reserved names
			return columnName;
		}

		Connection connection = JdbcUtils.openConnection(dataSource, 1);
		//
		try {
			String dbName = JdbcUtils.getDatabaseMetaData(connection).getDatabaseProductName().toLowerCase();

			if (dbName.equals("postgresql")) {
				return columnName.toLowerCase();
			}
		} catch (SQLException ex) {
			throw new FlywaySqlException("Error while determining database product name", ex);
		} finally {
			JdbcUtils.closeConnection(connection);
		}
		//
		return columnName;
	}
}