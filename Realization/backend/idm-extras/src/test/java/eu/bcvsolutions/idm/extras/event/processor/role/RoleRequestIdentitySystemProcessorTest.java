package eu.bcvsolutions.idm.extras.event.processor.role;

import static org.junit.Assert.*;

import java.util.List;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.InitApplicationData;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccIdentityAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningArchiveDto;
import eu.bcvsolutions.idm.acc.dto.SysProvisioningOperationDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysProvisioningOperationFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.service.api.ProvisioningService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningArchiveService;
import eu.bcvsolutions.idm.acc.service.api.SysProvisioningOperationService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.domain.OperationState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleRequestDto;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.extras.config.domain.ExtrasConfiguration;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;


public class RoleRequestIdentitySystemProcessorTest extends AbstractIntegrationTest {

	@Autowired
	private TestHelper testHelper;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private ExtrasConfiguration extrasConfiguration;
	@Autowired
	private SysProvisioningOperationService provisioningOperationService;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService mappingService;
	@Autowired
	private SysSchemaObjectClassService objectClassService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSystemAttributeMappingService attributeMappingService;
	@Autowired
	private IdmRoleRequestService roleRequestService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private ProvisioningService provisioningService;
	@Autowired
	private SysProvisioningArchiveService sysProvisioningArchiveService;

	@Before
	public void init() {
		loginAsAdmin(InitApplicationData.ADMIN_USERNAME);
	}

	@After
	public void logout() {
		super.logout();
	}

	@Test
	public void testCreateSystemAndProvideRole(){
		// create system
		SysSystemDto system = testHelper.createSystem(TestResource.TABLE_NAME);
		// generate schema
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);
		//
		assertEquals(1,objectClasses.size());
		// create mapping with one attribute mapped
		createMappingSystem(SystemEntityType.IDENTITY,
				objectClasses.get(0), "TestMappingProvisioning");
		// create role
		IdmRoleDto role = testHelper.createRole();
		// create role for system
		IdmRoleDto systemRole = testHelper.createRole();
		// put role on the system
		testHelper.createRoleSystem(systemRole, system);
		// set configuration property
		configurationService.setValue(extrasConfiguration.EXTRAS_SYSTEM_EXCHANGE_ID, system.getId().toString());
		// create identity
		IdmIdentityDto identity = testHelper.createIdentity();
		// give identity system role
		IdmRoleRequestDto roleRequest2 = testHelper.createRoleRequest(identity, systemRole);
		IdmRoleRequestDto idmRoleRequest = roleRequestService.startRequest(roleRequest2.getId(), true);
		roleRequestService.executeRequest(idmRoleRequest.getId());

		// give her just other role - should start the processor
		IdmRoleRequestDto roleRequest = testHelper.createRoleRequest(identity, role);
		IdmRoleRequestDto idmRoleRequestDto = roleRequestService.startRequest(roleRequest.getId(), true);
		roleRequestService.executeRequest(idmRoleRequestDto.getId());
		// test result
		SysProvisioningOperationFilter filter = new SysProvisioningOperationFilter();
		filter.setSystemId(system.getId());
		List<SysProvisioningArchiveDto> content = sysProvisioningArchiveService.find(filter, null).getContent();
		assertEquals(2,content.size());
	}

	public SysSystemMappingDto createMappingSystem(SystemEntityType type, SysSchemaObjectClassDto objectClass,
												   String name) {
		// system mapping
		SysSystemMappingDto mapping = new SysSystemMappingDto();
		mapping.setName(name);
		mapping.setEntityType(type);
		mapping.setObjectClass(objectClass.getId());
		mapping.setOperationType(SystemOperationType.PROVISIONING);
		mapping = mappingService.save(mapping);
		//
		SysSchemaAttributeFilter filter = new SysSchemaAttributeFilter();
		filter.setObjectClassId(objectClass.getId());
		List<SysSchemaAttributeDto> content = schemaAttributeService.find(filter, null).getContent();
		Object[] newRes = content.stream().filter(attribute -> attribute.getName().equals(
				"__NAME__")).toArray();
		//
		SysSystemAttributeMappingDto uid = new SysSystemAttributeMappingDto();
		uid.setSchemaAttribute(((SysSchemaAttributeDto)(newRes[0])).getId());
		uid.setEntityAttribute(true);
		uid.setName("username");
		uid.setIdmPropertyName("username");
		uid.setUid(true);
		uid.setSystemMapping(mapping.getId());
		//
		attributeMappingService.save(uid);
		//
		return mappingService.save(mapping);
	}

}