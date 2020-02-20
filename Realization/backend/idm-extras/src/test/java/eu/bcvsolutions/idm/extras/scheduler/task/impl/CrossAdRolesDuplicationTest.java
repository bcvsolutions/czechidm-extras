package eu.bcvsolutions.idm.extras.scheduler.task.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.collect.ImmutableMap;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.entity.OperationResult;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.service.CodeListManager;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Roman Kucera
 */
public class CrossAdRolesDuplicationTest extends AbstractIntegrationTest {

	@Autowired
	private LongRunningTaskManager lrtManager;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSchemaObjectClassService sysSchemaObjectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private CodeListManager codeListManager;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private IdmRoleService roleService;

	@Test
	@Transactional
	public void testDuplication() {
		getHelper().loginAdmin();

		IcConnectorKeyImpl key = new IcConnectorKeyImpl();
		key.setFramework("connId");
		key.setConnectorName("net.tirasa.connid.bundles.ad.ADConnector");
		key.setBundleName("net.tirasa.connid.bundles.ad");
		key.setBundleVersion("1.3.4.27");

		SysSystemDto ad1 = new SysSystemDto();
		ad1.setName("ad1");
		ad1.setConnectorKey(new SysConnectorKeyDto(key));
		ad1 = systemService.save(ad1);

		SysSystemDto ad2 = new SysSystemDto();
		ad2.setName("ad2");
		ad2.setConnectorKey(new SysConnectorKeyDto(key));
		ad2 = systemService.save(ad2);

		// create env code list
		IdmCodeListDto environment = codeListManager.get("environment");
		if (environment == null) {
			environment = codeListManager.create("environment");
		}
		codeListManager.createItem(environment, "ad1", "ad1");
		codeListManager.createItem(environment, "ad2", "ad2");

		// create schema
		SysSchemaObjectClassDto schemaWithAttrAd1 = createSchemaWithAttr(ad1);
		SysSchemaObjectClassDto schemaWithAttrAd2 = createSchemaWithAttr(ad2);

		// create mapping
		SysSystemMappingDto mapping = createMapping(ad1, schemaWithAttrAd1);
		SysSystemMappingDto mapping1 = createMapping(ad2, schemaWithAttrAd2);

		// create catalogue
		IdmRoleCatalogueDto cat = getHelper().createRoleCatalogue("cat");
		IdmRoleCatalogueDto cat1 = getHelper().createRoleCatalogue("cat1");

		// create role
		IdmRoleDto role = getHelper().createRole(null, "role", "ad1");

		// add to catalogue
		getHelper().createRoleCatalogueRole(role, cat);

		// assign system to role
		SysRoleSystemDto roleSystemDto = new SysRoleSystemDto();
		roleSystemDto.setForwardAccountManagemen(false);
		roleSystemDto.setRole(role.getId());
		roleSystemDto.setSystem(ad1.getId());
		roleSystemDto.setSystemMapping(mapping.getId());
		roleSystemDto = roleSystemService.save(roleSystemDto);

		roleSystemAttributeService.addRoleMappingAttribute(ad1.getId(), role.getId(), "ldapGroups", "return \"CN=role\"", "__ACCOUNT__");

		CrossAdRolesDuplication crossAdRolesDuplication = new CrossAdRolesDuplication();
		crossAdRolesDuplication.init(ImmutableMap.of(CrossAdRolesDuplication.CATALOG_PARAM, cat1.getId(),
				CrossAdRolesDuplication.ENV_PARAM, "ad2",
				CrossAdRolesDuplication.ROLE_CATALOGUE_PARAM, cat.getId(),
				CrossAdRolesDuplication.SYSTEMS_PARAM, ad2.getId()));

		OperationResult operationResult = lrtManager.executeSync(crossAdRolesDuplication);
		assertNotNull(operationResult);

		IdmRoleDto newRole = roleService.getByBaseCodeAndEnvironment("role", "ad2");
		assertNotNull(newRole);

		List<IdmRoleCatalogueDto> allByRole = roleCatalogueService.findAllByRole(newRole.getId());
		assertFalse(allByRole.isEmpty());

		SysRoleSystemFilter roleSystemFilter = new SysRoleSystemFilter();
		roleSystemFilter.setRoleId(newRole.getId());
		roleSystemFilter.setSystemId(ad2.getId());
		List<SysRoleSystemDto> content = roleSystemService.find(roleSystemFilter, null).getContent();
		assertFalse(content.isEmpty());

		getHelper().logout();
	}

	private SysSystemMappingDto createMapping(SysSystemDto ad1, SysSchemaObjectClassDto schemaWithAttrAd1) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(ad1.getId());

		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		syncSystemMapping.setOperationType(SystemOperationType.PROVISIONING);
		syncSystemMapping.setObjectClass(schemaWithAttrAd1.getId());
		final SysSystemMappingDto mapping = systemMappingService.save(syncSystemMapping);

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if ("ldapGroups".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setEntityAttribute(false);
				attributeMapping.setExtendedAttribute(false);
				attributeMapping.setName("ldapGroups");
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(mapping.getId());
				attributeMapping.setStrategyType(AttributeMappingStrategyType.MERGE);
				attributeMapping.setCached(true);
				schemaAttributeMappingService.save(attributeMapping);
			}
		});

		return mapping;
	}

	private SysSchemaObjectClassDto createSchemaWithAttr(SysSystemDto system) {
		// create schema
		SysSchemaObjectClassDto schemaObjectClassDto = new SysSchemaObjectClassDto();
		schemaObjectClassDto.setSystem(system.getId());
		schemaObjectClassDto.setObjectClassName("__ACCOUNT__");
		schemaObjectClassDto = sysSchemaObjectClassService.save(schemaObjectClassDto);

		SysSchemaAttributeDto schemaAttributeDto = new SysSchemaAttributeDto();
		schemaAttributeDto.setClassType("java.lang.String");
		schemaAttributeDto.setReadable(true);
		schemaAttributeDto.setMultivalued(true);
		schemaAttributeDto.setCreateable(true);
		schemaAttributeDto.setUpdateable(true);
		schemaAttributeDto.setReturnedByDefault(true);
		schemaAttributeDto.setRequired(false);
		schemaAttributeDto.setName("ldapGroups");
		schemaAttributeDto.setNativeName("ldapGroups");
		schemaAttributeDto.setObjectClass(schemaObjectClassDto.getId());
		schemaAttributeDto = schemaAttributeService.save(schemaAttributeDto);

		return schemaObjectClassDto;
	}
}