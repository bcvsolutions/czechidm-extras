package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.ProvisioningContext;
import eu.bcvsolutions.idm.acc.domain.ProvisioningEventType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.ProvisioningAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysConnectorKeyDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.scheduler.task.impl.CancelProvisioningQueueTaskExecutor;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmConfigurationDto;
import eu.bcvsolutions.idm.core.api.event.CoreEvent;
import eu.bcvsolutions.idm.core.api.event.EventResult;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmCodeListDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.CodeListManager;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.scheduler.api.service.LongRunningTaskManager;
import eu.bcvsolutions.idm.ic.api.IcConnectorObject;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.ic.impl.IcConnectorObjectImpl;
import eu.bcvsolutions.idm.ic.impl.IcObjectClassImpl;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * @author Roman Kucera
 */
@Service
public class ExtrasCrossAdGroupsProvisioningProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private ExtrasCrossAdGroupsProvisioningProcessor crossAdGroupsProvisioningProcessor;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private CodeListManager codeListManager;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private SysSystemEntityService systemEntityService;
	@Autowired
	private FormService formService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSchemaObjectClassService sysSchemaObjectClassService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private LongRunningTaskManager lrtManager;

	@Test
	public void process() {
		InMemoryDirectoryServer ds = null;
		LDAPConnection conn = null;
		String dn = "uid=admin";
		String adminPassword = "password";
		String context = "dc=bcv,dc=cz";
		try {
			// Create the configuration to use for the server.
			InMemoryDirectoryServerConfig config = new InMemoryDirectoryServerConfig(context);

			// load schema
			config.addAdditionalBindCredentials(dn, adminPassword);
			InputStream inputStream = new FileInputStream("src/test/resources/event.processor.provisioning/schema.ldif");
			final LDIFReader ldifReader = new LDIFReader(inputStream);
			final Entry schemaEntry = ldifReader.readEntry();
			ldifReader.close();

			Schema newSchema = new Schema(schemaEntry);
			config.setSchema(newSchema);

			// Create the directory server instance, populate it with data from the
			// "crossLdapDemoData.ldif" file, and start listening for client connections.
			ds = new InMemoryDirectoryServer(config);
			ds.importFromLDIF(true, "src/test/resources/event.processor.provisioning/crossLdapDemoData.ldif");
			ds.startListening();

			// Get a client connection to the server and use it to perform various
			// operations.
			conn = ds.getConnection();
		} catch (LDAPException | IOException | LDIFException e) {
			e.printStackTrace();
			Assert.fail();
		}

		IcConnectorKeyImpl key = new IcConnectorKeyImpl();

		key.setFramework("connId");
		key.setConnectorName("net.tirasa.connid.bundles.ldap.LdapConnector");
		key.setBundleName("net.tirasa.connid.bundles.ldap");
		key.setBundleVersion("1.5.1");

		SysSystemDto ad1 = new SysSystemDto();
		ad1.setName("ad1");
		ad1.setConnectorKey(new SysConnectorKeyDto(key));
		ad1 = systemService.save(ad1);

		IdmFormDefinitionDto connectorFormDef = systemService
				.getConnectorFormDefinition(ad1.getConnectorInstance());

		// set some config for system
		List<IdmFormValueDto> values = new ArrayList<>();

		IdmFormValueDto ssl = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("ssl"));
		ssl.setBooleanValue(false);
		values.add(ssl);

		IdmFormValueDto host = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("host"));
		host.setValue(conn.getConnectedIPAddress());
		values.add(host);

		IdmFormValueDto port = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("port"));
		port.setValue(conn.getConnectedPort());
		values.add(port);

		IdmFormValueDto user = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("principal"));
		user.setValue(dn);
		values.add(user);

		IdmFormValueDto password = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("credentials"));
		password.setValue(adminPassword);
		password.setConfidential(true);
		values.add(password);

		IdmFormValueDto baseContextsToSynchronize = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("baseContextsToSynchronize"));
		baseContextsToSynchronize.setValue(context);
		values.add(baseContextsToSynchronize);

		IdmFormValueDto userBaseContexts = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("baseContexts"));
		userBaseContexts.setValue(context);
		values.add(userBaseContexts);

		IdmFormValueDto vlv = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("useVlvControls"));
		vlv.setBooleanValue(true);
		values.add(vlv);

		IdmFormValueDto uidAttribute = new IdmFormValueDto(connectorFormDef.getMappedAttributeByCode("uidAttribute"));
		uidAttribute.setValue("uid");
		values.add(uidAttribute);

		formService.saveValues(ad1, connectorFormDef, values);

		// create schema for system
		SysSchemaObjectClassDto schemaObjectClassDto = new SysSchemaObjectClassDto();
		schemaObjectClassDto.setSystem(ad1.getId());
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

		SysSchemaAttributeDto schemaAttributeDtoName = new SysSchemaAttributeDto();
		schemaAttributeDtoName.setClassType("java.lang.String");
		schemaAttributeDtoName.setReadable(true);
		schemaAttributeDtoName.setMultivalued(false);
		schemaAttributeDtoName.setCreateable(true);
		schemaAttributeDtoName.setUpdateable(true);
		schemaAttributeDtoName.setReturnedByDefault(true);
		schemaAttributeDtoName.setRequired(true);
		schemaAttributeDtoName.setName("__NAME__");
		schemaAttributeDtoName.setNativeName("__NAME__");
		schemaAttributeDtoName.setObjectClass(schemaObjectClassDto.getId());
		schemaAttributeDtoName = schemaAttributeService.save(schemaAttributeDtoName);

		// create provisioning mapping
		createMapping(ad1, schemaObjectClassDto);

		// create env code list
		IdmCodeListDto crossSystems = codeListManager.get("crossSystems");
		if (crossSystems == null) {
			crossSystems = codeListManager.create("crossSystems");
		}
		codeListManager.createItem(crossSystems, ad1.getId().toString(), "ad1");

		// set app confg property
		IdmConfigurationDto codeListConfig = new IdmConfigurationDto();
		codeListConfig.setConfidential(false);
		codeListConfig.setSecured(true);
		codeListConfig.setPublic(false);
		codeListConfig.setName("idm.sec.extras.configuration.cross.codeList");
		codeListConfig.setValue(crossSystems.getCode());
		configurationService.saveConfiguration(codeListConfig);

		// prepare system entity
		SysSystemEntityDto systemEntityDto = new SysSystemEntityDto();
		systemEntityDto.setWish(false);
		systemEntityDto.setUid("javatest");
		systemEntityDto.setSystem(ad1.getId());
		systemEntityDto.setEntityType(SystemEntityType.IDENTITY);
		systemEntityDto = systemEntityService.save(systemEntityDto);

		SysProvisioningOperationDto content = prepareContent(ad1, systemEntityDto, new ArrayList<>());
		SysProvisioningOperationDto contentWithChange = prepareContent(ad1, systemEntityDto, Collections.singletonList("some role"));

		CoreEvent<SysProvisioningOperationDto> event = new CoreEvent<>(CoreEvent.CoreEventType.UPDATE, content);
		event.setOriginalSource(content);
		CoreEvent<SysProvisioningOperationDto> eventWithChange = new CoreEvent<>(CoreEvent.CoreEventType.UPDATE, contentWithChange);
		eventWithChange.setOriginalSource(contentWithChange);

		EventResult<SysProvisioningOperationDto> process = crossAdGroupsProvisioningProcessor.process(event);
		EventResult<SysProvisioningOperationDto> processWithChange = crossAdGroupsProvisioningProcessor.process(eventWithChange);

		Assert.assertNotNull(process);
		Assert.assertNotNull(processWithChange);

		// cancel provisioning
		CancelProvisioningQueueTaskExecutor cancelProvisioningQueueTaskExecutor = new CancelProvisioningQueueTaskExecutor();
		SysProvisioningOperationFilter provisioningOperationFilter = new SysProvisioningOperationFilter();
		provisioningOperationFilter.setSystemId(ad1.getId());
		lrtManager.executeSync(cancelProvisioningQueueTaskExecutor);

		systemService.delete(ad1);

		conn.close();
		ds.shutDown(true);
	}

	private SysProvisioningOperationDto prepareContent(SysSystemDto ad1, SysSystemEntityDto systemEntityDto, List<Object> values) {
		// prepare connector object
		IcConnectorObject connectorObject = new IcConnectorObjectImpl("javatest", new IcObjectClassImpl("__ACCOUNT__"), new ArrayList<>());

		// prepare account object
		Map<ProvisioningAttributeDto, Object> accountObject = new HashMap<>();
		ProvisioningAttributeDto ldapGroups = new ProvisioningAttributeDto("ldapGroups", AttributeMappingStrategyType.MERGE);
		accountObject.put(ldapGroups, values);

		// prepare provisioning context
		ProvisioningContext provisioningContext = new ProvisioningContext();
		provisioningContext.setConnectorObject(connectorObject);
		provisioningContext.setAccountObject(accountObject);

		// prepare provisioning operation
		SysProvisioningOperationDto content = new SysProvisioningOperationDto();
		content.setOperationType(ProvisioningEventType.UPDATE);
		content.setEntityType(SystemEntityType.IDENTITY);
		content.setSystem(ad1.getId());
		content.setProvisioningContext(provisioningContext);
		content.setSystemEntity(systemEntityDto.getId());
		return content;
	}

	private SysSystemMappingDto createMapping(SysSystemDto system, SysSchemaObjectClassDto schema) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		SysSystemMappingDto provMapping = new SysSystemMappingDto();
		provMapping.setName("default_" + System.currentTimeMillis());
		provMapping.setEntityType(SystemEntityType.IDENTITY);
		provMapping.setOperationType(SystemOperationType.PROVISIONING);
		provMapping.setObjectClass(schema.getId());
		final SysSystemMappingDto mapping = systemMappingService.save(provMapping);

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
}