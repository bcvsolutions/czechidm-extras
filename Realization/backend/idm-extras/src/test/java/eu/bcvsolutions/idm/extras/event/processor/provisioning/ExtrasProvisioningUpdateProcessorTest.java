package eu.bcvsolutions.idm.extras.event.processor.provisioning;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.identityconnectors.common.security.GuardedString;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.extras.service.api.ExtrasCrossDomainService;
import eu.bcvsolutions.idm.ic.api.IcConfigurationProperty;
import eu.bcvsolutions.idm.ic.api.IcConnectorConfiguration;
import eu.bcvsolutions.idm.ic.impl.IcConfigurationPropertyImpl;
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
	private ExtrasCrossDomainService additionalCredentialsService;

	@Test
	public void processInternal() {

		VsSystemDto vsSystemDto = new VsSystemDto();
		vsSystemDto.setName("VS-AD-1");
		SysSystemDto systemDto = vsSystemService.create(vsSystemDto);

		IcConnectorConfiguration connectorConfiguration = systemService.getConnectorConfiguration(systemDto);
		List<UUID> systems = Collections.singletonList(systemDto.getId());
		IcConfigurationProperty pass = new IcConfigurationPropertyImpl("password", new GuardedString("Test1234".toCharArray()));
		IcConfigurationProperty user = new IcConfigurationPropertyImpl("user", "test");
		connectorConfiguration.getConfigurationProperties().getProperties().add(pass);
		connectorConfiguration.getConfigurationProperties().getProperties().add(user);

		IcConnectorConfiguration configuration = additionalCredentialsService.getConfiguration(connectorConfiguration, systems);
		Assert.assertNotNull(configuration);

	}
}