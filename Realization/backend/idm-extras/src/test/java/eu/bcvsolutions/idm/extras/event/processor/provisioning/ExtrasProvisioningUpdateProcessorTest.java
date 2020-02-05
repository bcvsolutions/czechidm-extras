package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.extras.service.api.ExtrasCrossDomainService;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.czechidm.service.impl.CzechIdMIcConfigurationService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.vs.dto.VsSystemDto;
import eu.bcvsolutions.idm.vs.service.api.VsSystemService;

/**
 * @author Roman Kucera
 */
public class ExtrasProvisioningUpdateProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private SysSystemService systemService;
	@Autowired
	private VsSystemService vsSystemService;
	@Autowired
	private ExtrasCrossDomainService extrasCrossDomainService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private CzechIdMIcConfigurationService czechIdMIcConfigurationService;

	@Test
	public void testGetConfiguration() {

		VsSystemDto vsSystemDto = new VsSystemDto();
		List<String> attributes = new ArrayList<>();
		attributes.add("user");
		attributes.add("password");
		vsSystemDto.setAttributes(attributes);
		vsSystemDto.setName("VS-AD-1");
		SysSystemDto systemDto = vsSystemService.create(vsSystemDto);


		// Find and update attribute for implementers
		IdmFormDefinitionDto connectorFormDef = systemService
				.getConnectorFormDefinition(systemDto.getConnectorInstance());

		// Add new attributes into definition
		IdmFormAttributeDto userAttribute = new IdmFormAttributeDto("user", "user", PersistentType.SHORTTEXT);
		userAttribute.setFormDefinition(connectorFormDef.getId());
		IdmFormAttributeDto passwordAttribute = new IdmFormAttributeDto("password", "password", PersistentType.SHORTTEXT);
		passwordAttribute.setConfidential(true);
		passwordAttribute.setFormDefinition(connectorFormDef.getId());

		userAttribute = formAttributeService.save(userAttribute);
		passwordAttribute = formAttributeService.save(passwordAttribute);

		connectorFormDef.addFormAttribute(userAttribute);
		connectorFormDef.addFormAttribute(passwordAttribute);

		connectorFormDef = formDefinitionService.save(connectorFormDef);

		IdmFormValueDto userValue = new IdmFormValueDto();
		userValue.setShortTextValue("test");
		userValue.setFormAttribute(userAttribute.getId());

		IdmFormValueDto passwordValue = new IdmFormValueDto();
		passwordValue.setConfidential(true);
		passwordValue.setShortTextValue("Test1234");
		passwordValue.setFormAttribute(passwordAttribute.getId());

		formService.saveValues(systemDto, connectorFormDef, Collections.singletonList(userValue));
		formService.saveValues(systemDto, connectorFormDef, Collections.singletonList(passwordValue));

//		IcConnectorConfiguration connectorConfiguration = systemService.getConnectorConfiguration(systemDto);
		List<UUID> systems = Collections.singletonList(systemDto.getId());
////		IcConfigurationProperty pass = new IcConfigurationPropertyImpl("password", new GuardedString("Test1234".toCharArray()));
////		IcConfigurationProperty user = new IcConfigurationPropertyImpl("user", "test");
////		connectorConfiguration.getConfigurationProperties().getProperties().add(pass);
////		connectorConfiguration.getConfigurationProperties().getProperties().add(user);
////
////		VsVirtualConnector virtualConnector = vsSystemService.getVirtualConnector(systemDto.getId(), systemDto.getConnectorKey().getFullName());
////		vsSystemService.updateSystemConfiguration(connectorConfiguration, virtualConnector.getClass());


		IcConnectorConfiguration connectorConfiguration = systemService.getConnectorConfiguration(systemDto);
		IcConnectorConfiguration configuration = extrasCrossDomainService.getConfiguration(connectorConfiguration, systems);
		Assert.assertNotNull(configuration);

	}
}