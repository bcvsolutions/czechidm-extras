package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;

import org.identityconnectors.common.security.GuardedString;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.entity.TestRoleResource;
import eu.bcvsolutions.idm.extras.service.api.ExtrasCrossDomainService;
import eu.bcvsolutions.idm.extras.util.GuardedStringAccessor;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.api.IcObjectClass;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Roman Kucera
 */
@Service
public class ExtrasProvisioningUpdateProcessorTest extends AbstractIntegrationTest {

	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String ATTRIBUTE_DN = "EAV_ATTRIBUTE";
	private static final String ATTRIBUTE_MEMBER = "MEMBER";

	@Autowired
	private SysSystemService systemService;
	@Autowired
	private FormService formService;
	@Autowired
	private ExtrasCrossDomainService extrasCrossDomainService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private ApplicationContext applicationContext;

	@Test
	@Transactional
	public void testGetConfiguration() {
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

		IdmFormDefinitionDto connectorFormDef = systemService
				.getConnectorFormDefinition(ad1.getConnectorInstance());

		List<IdmFormValueDto> values = new ArrayList<>();
		IdmFormValueDto user = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("principal"));
		user.setValue("test");
		values.add(user);

		IdmFormValueDto password = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("credentials"));
		password.setValue("Demo");
		password.setConfidential(true);
		values.add(password);

		IdmFormValueDto baseContextsToSynchronize = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("baseContextsToSynchronize"));
		baseContextsToSynchronize.setValue("CN=ad1");
		values.add(baseContextsToSynchronize);

		formService.saveValues(ad1, connectorFormDef, values);

		List<IdmFormValueDto> values2 = new ArrayList<>();
		IdmFormValueDto user2 = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("principal"));
		user2.setValue("test2");
		values2.add(user2);

		IdmFormValueDto password2 = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("credentials"));
		password2.setValue("Demo2");
		password2.setConfidential(true);
		values2.add(password2);

		IdmFormValueDto baseContextsToSynchronize2 = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("baseContextsToSynchronize"));
		baseContextsToSynchronize2.setValue("CN=ad2");
		values2.add(baseContextsToSynchronize2);

		formService.saveValues(ad2, connectorFormDef, values2);

		IcConnectorConfiguration connectorConfiguration = systemService.getConnectorConfiguration(ad1);

		List<UUID> systems = new ArrayList<>();
		systems.add(ad1.getId());
		systems.add(ad2.getId());
		IcConnectorConfiguration configuration = extrasCrossDomainService.getConfiguration(connectorConfiguration, systems);

		Assert.assertNotNull(configuration);

		IcConfigurationProperty additionalCreds = configuration.getConfigurationProperties().getProperties()
				.stream()
				.filter(icConfigurationProperty -> icConfigurationProperty.getName().equals("additionalCreds"))
				.findFirst()
				.orElse(null);

		Assert.assertNotNull(additionalCreds);

		List<GuardedString> guardedStrings = Arrays.asList((GuardedString[]) additionalCreds.getValue());
		guardedStrings.forEach(guardedString -> {
			GuardedStringAccessor accessor = new GuardedStringAccessor();
			guardedString.access(accessor);
			String additionalCredString = new String(accessor.getArray());
			if (additionalCredString.contains("ad1")) {
				Assert.assertEquals(additionalCredString, "CN=ad1\\test\\Demo");
			} else if (additionalCredString.contains("ad2")) {
				Assert.assertEquals(additionalCredString, "CN=ad2\\test2\\Demo2");
			}
			accessor.clearArray();
		});
	}

	@Test
	public void testGetAllUsersGroups() {

		SysSystemDto ad1 = helper.createSystem(TestRoleResource.TABLE_NAME, "ad1");

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(ad1);

		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(ad1.getId());

		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
		syncSystemMapping.setOperationType(SystemOperationType.PROVISIONING);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("username");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(syncMapping.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (ATTRIBUTE_DN.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_DN);
				attributeMappingTwo.setEntityAttribute(false);
				attributeMappingTwo.setExtendedAttribute(true);
				attributeMappingTwo.setName("distinguishedName");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(syncMapping.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);

			} else if (ATTRIBUTE_MEMBER.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_MEMBER);
				attributeMappingTwo.setEntityAttribute(false);
				attributeMappingTwo.setExtendedAttribute(true);
				attributeMappingTwo.setName("MEMBER");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(syncMapping.getId());
				attributeMappingTwo.setStrategyType(AttributeMappingStrategyType.MERGE);
				schemaAttributeMappingService.save(attributeMappingTwo);
			}
		});

		this.getBean().prepareDateInSystem();

		List<UUID> systems = Collections.singletonList(ad1.getId());

		IcObjectClass objectClass = new IcObjectClassImpl("__ACCOUNT__");
		Set<String> allUsersGroups = extrasCrossDomainService.getAllUsersGroups(systems, "user", "usersid", objectClass);

		Assert.assertFalse(allUsersGroups.isEmpty());
		Assert.assertTrue(allUsersGroups.contains("role"));

		systemService.delete(ad1);
	}

	@Transactional
	public void prepareDateInSystem() {
		TestRoleResource resourceUserOne = new TestRoleResource();
		resourceUserOne.setName("role");
		resourceUserOne.setMember("user");
		entityManager.persist(resourceUserOne);
	}

	private ExtrasProvisioningUpdateProcessorTest getBean() {
		return applicationContext.getBean(this.getClass());
	}
}