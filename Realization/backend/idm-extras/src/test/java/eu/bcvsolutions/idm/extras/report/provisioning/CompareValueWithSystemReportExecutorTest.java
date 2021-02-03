package eu.bcvsolutions.idm.extras.report.provisioning;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.domain.AccountType;
import eu.bcvsolutions.idm.acc.domain.AttributeMappingStrategyType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AccAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemEntityDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccAccountFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.service.api.AccAccountService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaObjectClassService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemEntityService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.AbstractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.ecm.api.service.AttachmentManager;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.extras.DefaultAccTestHelper;
import eu.bcvsolutions.idm.extras.TestResource;
import eu.bcvsolutions.idm.ic.api.IcConnectorKey;
import eu.bcvsolutions.idm.ic.connid.service.impl.ConnIdIcConfigurationService;
import eu.bcvsolutions.idm.ic.impl.IcConnectorKeyImpl;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.dto.RptIdentityWithFormValueDto;
import eu.bcvsolutions.idm.rpt.report.identity.IdentityEavReportExecutor;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import javafx.print.Collation;

public class CompareValueWithSystemReportExecutorTest extends AbstractIntegrationTest {

	@Autowired
	AttachmentManager attachmentManager;

	@Autowired
	IdmIdentityService identityService;

	@Autowired
	CompareValueWithSystemReportExecutor reportExecutor;

	@Autowired
	FormService formService;

	@Autowired
	SysSystemService systemService;

	@Autowired
	SysSystemMappingService mappingService;

	@Autowired
	SysSystemAttributeMappingService systemAttributeMappingService;

	@Autowired
	SysSchemaObjectClassService objectClassService;

	@Autowired
	SysSchemaAttributeService schemaAttributeService;

	@Autowired
	ObjectMapper mapper;

	@Autowired
	CompareValueWithSystemReportXlsxRenderer renderer;

	@Autowired
	SysRoleSystemService roleSystemService;

	@Autowired
	ConnIdIcConfigurationService configurationService;

	@Autowired
	DefaultAccTestHelper accTestHelper;

	@Autowired
	AccAccountService accAccountService;

	@Autowired
	SysSystemEntityService entityService;

	@Test
	public void generateMultivalWithNullValues() throws IOException {
		loginAsAdmin();
		// prepare test identities
		IdmIdentityDto identityOne = getHelper().createIdentity((GuardedString) null);
		IdmIdentityContractDto identityContact = getHelper().createIdentityContact(identityOne);
		IdmRoleDto role = getHelper().createRole();
		//
		// prepare report filter
		RptReportDto report = new RptReportDto(UUID.randomUUID());
		report.setExecutorName(reportExecutor.getName());
		IdmFormDto filter = new IdmFormDto();
		IdmFormDefinitionDto definition = reportExecutor.getFormDefinition();
		//
		SysSystemDto systemDto = accTestHelper.createTestResourceSystem(true);
		SysSystemMappingDto sysSystemMappingDto = mappingService.findProvisioningMapping(systemDto.getId(), SystemEntityType.IDENTITY);
		SysSchemaObjectClassDto objectClass = objectClassService.get(sysSystemMappingDto.getObjectClass());

		SysSchemaAttributeFilter attrFilter = new SysSchemaAttributeFilter();
		attrFilter.setSystemId(systemDto.getId());
		attrFilter.setName("EAV_ATTRIBUTE");
		attrFilter.setObjectClassId(objectClass.getId());
		SysSchemaAttributeDto schemaAttrMulti =schemaAttributeService.find(attrFilter, null).stream().findFirst().orElse(null);
		schemaAttrMulti.setMultivalued(true);
		schemaAttributeService.save(schemaAttrMulti);
		SysSystemAttributeMappingDto sysSystemAttributeMappingDtoMulti = createSysSysAttrMapping(sysSystemMappingDto, IdmIdentity_.EMAIL, schemaAttrMulti.getId(), AttributeMappingStrategyType.MERGE, false);
		//
		IdmFormValueDto system =
				new IdmFormValueDto(definition.getMappedAttributeByCode(CompareValueWithSystemReportExecutor.PARAMETER_SYSTEM));
		system.setValue(systemDto.getId().toString());
		//
		IdmFormValueDto mapping =
				new IdmFormValueDto(definition.getMappedAttributeByCode(CompareValueWithSystemReportExecutor.PARAMETER_SYSTEM_MAPPING));
		mapping.setValue(sysSystemMappingDto.getId().toString());
		//
		String collect = systemAttributeMappingService.findBySystemMapping(accTestHelper.getDefaultMapping(systemDto)).stream().map(AbstractDto::getId).map(Objects::toString).collect(Collectors.joining(","));
		IdmFormValueDto attrs =
				new IdmFormValueDto(definition.getMappedAttributeByCode(CompareValueWithSystemReportExecutor.PARAMETER_ATTRIBUTES));
		attrs.setValue((Serializable) collect);

		filter.getValues().add(system);
		filter.getValues().add(mapping);
		filter.getValues().add(attrs);
		filter.setFormDefinition(definition.getId());
		report.setFilter(filter);

		accTestHelper.createRoleSystem(role, systemDto);


		getHelper().assignRoles(identityContact, role);
		AccAccountFilter testFilter = new AccAccountFilter();
		testFilter.setSystemId(systemDto.getId());
		testFilter.setIdentityId(identityOne.getId());
		List<AccAccountDto> accounts = accAccountService.getAccounts(systemDto.getId(), identityOne.getId());
		Page<AccAccountDto> accountsTest = accAccountService.find(testFilter, null, IdmBasePermission.READ);
		// generate report
		report = reportExecutor.generate(report);
		Assert.assertNotNull(report.getData());

		List<CompareValueDataInfoDto> identities = mapper.readValue(
				attachmentManager.getAttachmentData(report.getData()),
				new TypeReference<List<CompareValueDataInfoDto>>() {
				});
		//
		// test
		assertEquals(1, identities.size());
		assertEquals(1, identities.get(0).getRows().size());
		Assert.assertFalse(identities.get(0).getRows().get(0).isFailed());
		// rest renderer
		Assert.assertNotNull(renderer.render(report));
		//

		//
		attachmentManager.deleteAttachments(report);
		logout();
	}

	private AccAccountDto createAccount(SysSystemEntityDto entity) {
		return null;
	}

	private SysSystemEntityDto createSystemEntity(SysSystemDto system, String uid) {
		SysSystemEntityDto result = new SysSystemEntityDto();
		result.setEntityType(SystemEntityType.IDENTITY);
		result.setSystem(system.getId());
		result.setUid(uid);
		return entityService.save(result);
	}

	private SysSchemaObjectClassDto createObjectClass(SysSystemDto systemDto) {
		SysSchemaObjectClassDto result = new SysSchemaObjectClassDto();
		result.setSystem(systemDto.getId());
		result.setObjectClassName("__ACCOUNT__");
		return objectClassService.save(result);
	}

	private SysSchemaAttributeDto createSchemaAttribute(UUID objectClass, String name, boolean multi) {
		SysSchemaAttributeDto result = new SysSchemaAttributeDto();
		result.setName(name);
		result.setClassType(String.class.getCanonicalName());
		result.setCreateable(true);
		result.setReadable(true);
		result.setUpdateable(true);
		result.setReturnedByDefault(true);
		result.setMultivalued(multi);
		result.setObjectClass(objectClass);
		return schemaAttributeService.save(result);
	}

	private SysSystemAttributeMappingDto createSysSysAttrMapping(SysSystemMappingDto sysSystemMappingDto, String attributeName, UUID schemaAttr, AttributeMappingStrategyType strategy, boolean uid) {

		SysSystemAttributeMappingDto result = new SysSystemAttributeMappingDto();
		result.setName(attributeName);
		result.setSystemMapping(sysSystemMappingDto.getId());
		result.setStrategyType(strategy);
		result.setIdmPropertyName(attributeName);
		result.setEntityAttribute(true);
		result.setSchemaAttribute(schemaAttr);
		result.setSendAlways(true);
		result.setUid(uid);

		return systemAttributeMappingService.save(result);
	}

	private SysSystemMappingDto createTestProvMapping(UUID objectClass) {
		SysSystemMappingDto mappingDto = new SysSystemMappingDto();

		mappingDto.setName(getHelper().createName());
		mappingDto.setEntityType(SystemEntityType.IDENTITY);
		mappingDto.setObjectClass(objectClass);
		mappingDto.setOperationType(SystemOperationType.PROVISIONING);


		return mappingService.save(mappingDto);
	}


}