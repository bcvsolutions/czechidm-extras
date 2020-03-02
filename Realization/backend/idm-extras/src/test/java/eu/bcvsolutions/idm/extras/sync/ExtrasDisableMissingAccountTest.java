package eu.bcvsolutions.idm.extras.sync;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncContractConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.extras.DefaultAccTestHelper;
import eu.bcvsolutions.idm.extras.TestContractResource;
import eu.bcvsolutions.idm.extras.entity.TestRoleResource;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

@Component
public class ExtrasDisableMissingAccountTest extends AbstractIntegrationTest {

	public static final String SYS_NAME = "extras_contract_disable_test";

	@Autowired private DefaultAccTestHelper accHelper;

	@Autowired private SysSystemService systemService;
	@Autowired
	SysSystemMappingService systemMappingService;
	@Autowired
	SysSchemaAttributeService schemaAttributeService;
	@Autowired
	SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	EntityManager entityManager;
	@Autowired
	SysSyncConfigService syncConfigService;
	@Autowired
	IdmIdentityContractService contractService;


	public SysSystemDto setup() {
		SysSystemDto sys = accHelper.createSystem(TestContractResource.TABLE_NAME, SYS_NAME);
		//
		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(sys);

		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.CONTRACT);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());

		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);
		createMapping(sys, syncMapping);
		return sys;
	}

	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if ("ID".equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("position");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if ("OWNER".equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName("identity");
				attributeMappingTwo.setEntityAttribute(true);
				attributeMappingTwo.setExtendedAttribute(false);
				attributeMappingTwo.setName(schemaAttr.getName());
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);
			}
		});
	}

	private ExtrasDisableMissingAccountTest getBean() {
		return applicationContext.getBean(this.getClass());
	}

	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestRoleResource.TABLE_NAME);
		q.executeUpdate();
	}

	@Transactional
	public void createContract(String id, String owner) {
		TestContractResource contract = new TestContractResource();
		contract.setId(id);
		contract.setName(id);
		contract.setOwner(owner);
		entityManager.persist(contract);
	}

	@Transactional
	public void deleteContract(String id) {
		entityManager.remove(entityManager.find(TestContractResource.class, id));
	}

	private SysSyncContractConfigDto doCreateSyncConfig(SysSystemDto system) {

		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.CONTRACT);
		mappingFilter.setSystemId(system.getId());
		mappingFilter.setOperationType(SystemOperationType.SYNCHRONIZATION);
		List<SysSystemMappingDto> mappings = systemMappingService.find(mappingFilter, null).getContent();
		Assert.assertEquals(1, mappings.size());
		SysSystemMappingDto mapping = mappings.get(0);
		SysSystemAttributeMappingFilter attributeMappingFilter = new SysSystemAttributeMappingFilter();
		attributeMappingFilter.setSystemMappingId(mapping.getId());

		List<SysSystemAttributeMappingDto> attributes = schemaAttributeMappingService.find(attributeMappingFilter, null)
				.getContent();
		SysSystemAttributeMappingDto uidAttribute = attributes.stream().filter(attribute -> {
			return attribute.isUid();
		}).findFirst().orElse(null);

		// Create default synchronization config
		SysSyncContractConfigDto syncConfigCustom = new SysSyncContractConfigDto();
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setName("ctrsync");
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK_AND_UPDATE_ENTITY);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.IGNORE);

		syncConfigCustom = (SysSyncContractConfigDto) syncConfigService.save(syncConfigCustom);

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setSystemId(system.getId());
		Assert.assertEquals(1, syncConfigService.find(configFilter, null).getTotalElements());
		return syncConfigCustom;
	}

	@Test
	public void testDisablingAccounts() {
		SysSystemDto sys = setup();
		SysSyncContractConfigDto configDto = doCreateSyncConfig(sys);
		configDto.setMissingAccountActionWfKey("extrasDisableMissingContract");
		syncConfigService.save(configDto);

		IdmIdentityDto ctrtest1 = getHelper().createIdentity("CTRTEST1");

		getBean().createContract("1", "CTRTEST1");

		accHelper.startSynchronization(configDto);

		List<IdmIdentityContractDto> allByIdentity = contractService.findAllByIdentity(ctrtest1.getId());

		Assert.assertTrue(allByIdentity.stream().anyMatch(c -> "1".equals(c.getPosition())));

		getBean().deleteContract("1");

		accHelper.startSynchronization(configDto);

		List<IdmIdentityContractDto> allByIdentityAfterDelete = contractService.findAllByIdentity(ctrtest1.getId());

		Assert.assertTrue(allByIdentityAfterDelete.stream().anyMatch(c -> "1".equals(c.getPosition())));
		IdmIdentityContractDto contr = allByIdentityAfterDelete.stream()
				.filter(c -> "1".equals(c.getPosition()))
				.findFirst().orElseThrow(() -> new IllegalStateException("Nothing found"));
		Assert.assertTrue(contr.isDisabled());
	}

}
