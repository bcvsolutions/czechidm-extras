package eu.bcvsolutions.idm.extras.sync;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;
import javax.persistence.Query;
import javax.transaction.Transactional;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import eu.bcvsolutions.idm.InitApplicationData;
import eu.bcvsolutions.idm.extras.TestHelper;
import eu.bcvsolutions.idm.acc.domain.OperationResultType;
import eu.bcvsolutions.idm.acc.domain.ReconciliationMissingAccountActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationLinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationMissingEntityActionType;
import eu.bcvsolutions.idm.acc.domain.SynchronizationUnlinkedActionType;
import eu.bcvsolutions.idm.acc.domain.SystemEntityType;
import eu.bcvsolutions.idm.acc.domain.SystemOperationType;
import eu.bcvsolutions.idm.acc.dto.AbstractSysSyncConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaAttributeDto;
import eu.bcvsolutions.idm.acc.dto.SysSchemaObjectClassDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncActionLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncIdentityConfigDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncItemLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSyncLogDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSchemaAttributeFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncActionLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncConfigFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncItemLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSyncLogFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemAttributeMappingFilter;
import eu.bcvsolutions.idm.acc.dto.filter.SysSystemMappingFilter;
import eu.bcvsolutions.idm.extras.entity.TestRoleResource;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSchemaAttributeService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncActionLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncConfigService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncItemLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSyncLogService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemAttributeMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemMappingService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleFilter;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleGuaranteeRoleFilter;
import eu.bcvsolutions.idm.core.api.service.ConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.dto.filter.IdmFormAttributeFilter;
import eu.bcvsolutions.idm.core.eav.api.service.FormService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.extras.service.api.ExtrasSyncRoleLdapService;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import groovy.json.StringEscapeUtils;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Service
public class RoleWorkflowAdSyncTest  extends AbstractIntegrationTest{

	private static final String ROLE_NAME = "nameOfRole";
	private static final String SECOND_ROLE_NAME = "secondRole\\AndItsName";
	private static final String SYNC_CONFIG_NAME = "syncConfigNameContract";
	private static final String ATTRIBUTE_NAME = "__NAME__";
	private static final String ATTRIBUTE_DN = "EAV_ATTRIBUTE";
	private static final String ATTRIBUTE_MEMBER = "MEMBER";	
	private static final String ATTRIBUTE_DN_VALUE = "CN=" + ROLE_NAME + ",OU=Office,OU=Prague,DC=bcvsolutions,DC=eu";
	private static final String SECOND_ATTRIBUTE_DN_VALUE = "CN=" + SECOND_ROLE_NAME + ",OU=BigHouse,OU=Prague,DC=bcvsolutions,DC=eu";
	private static final String CATALOGUE_CODE_FIRST = "Office";
	private static final String CATALOGUE_CODE_SECOND = "Prague";
	private static final String SYSTEM_NAME = "TestSystem" + System.currentTimeMillis();
	private final String wfExampleKey =  "extrasSyncRoleLdap";
	private static final String CATALOG_FOLDER = "TestCatalog" + System.currentTimeMillis();
	
	@Autowired
	private TestHelper helper;
	@Autowired
	private SysSystemService systemService;
	@Autowired
	private SysSystemMappingService systemMappingService;
	@Autowired
	private SysSystemAttributeMappingService schemaAttributeMappingService;
	@Autowired
	private SysSchemaAttributeService schemaAttributeService;
	@Autowired
	private SysSyncConfigService syncConfigService;
	@Autowired
	private SysSyncLogService syncLogService;
	@Autowired
	private SysSyncItemLogService syncItemLogService;
	@Autowired
	private SysSyncActionLogService syncActionLogService;
	@Autowired
	private EntityManager entityManager;
	@Autowired
	private ApplicationContext applicationContext;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private FormService formService;
	@Autowired
	private IdmRoleCatalogueService roleCatalogueService;
	@Autowired
	private ConfigurationService configurationService;
	@Autowired
	private SysRoleSystemAttributeService roleSystemAttributeService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private IdmRoleCatalogueRoleService roleCatalogueRoleService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmRoleGuaranteeRoleService roleGuaranteeRoleService;
	@Autowired
	private ExtrasSyncRoleLdapService syncRoleLdapService;
	
	
	@Before
	public void init() {
		loginAsAdmin(InitApplicationData.ADMIN_USERNAME);
		configurationService.setBooleanValue("idm.pub.acc.enabled", Boolean.TRUE);
		configurationService.setValue("idm.pub.acc.syncRole.system.mapping.attributeRoleIdentificator", ATTRIBUTE_DN);
	}

	@After
	public void logout() {
		if (roleService.getByCode(ROLE_NAME) != null) {
			roleService.delete(roleService.getByCode(ROLE_NAME));
		}
		if (roleService.getByCode(SECOND_ROLE_NAME) != null) {
			roleService.delete(roleService.getByCode(SECOND_ROLE_NAME));
		}
		if (systemService.getByCode(SYSTEM_NAME) != null) {
			systemService.delete(systemService.getByCode(SYSTEM_NAME));
		}
		configurationService.deleteValue("idm.pub.acc.syncRole.system.mapping.attributeRoleIdentificator");
		configurationService.setBooleanValue("idm.pub.acc.enabled", Boolean.FALSE);
		super.logout();
	}
	
	@Test
	public void n5_testSyncWithWfSituationMissingEntity() {
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOGUE_CODE_FIRST);
		IdmRoleCatalogueDto catalogueSecond = getCatalogueByCode(CATALOGUE_CODE_SECOND);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertNotNull(catalogueSecond);

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void n5_testSyncWithWfSituationLinked() {
		createRolesInSystem();
		final String newDN = "CN=" + ROLE_NAME + ",OU=Flat,OU=Pardubice,DC=bcvsolutions,DC=eu";
		this.getBean().initIdentityData(ROLE_NAME, newDN, SECOND_ROLE_NAME, SECOND_ATTRIBUTE_DN_VALUE);
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		
		SysSystemDto systemDto = systemService.getByCode(SYSTEM_NAME);
		Assert.assertNotNull(systemDto);

		SysSyncConfigFilter filter = new SysSyncConfigFilter();
		filter.setSystemId(systemDto.getId());
		List<AbstractSysSyncConfigDto> syncConfig = syncConfigService.find(filter, null).getContent();
		Assert.assertEquals(1, syncConfig.size());
		
		// Start sync
		helper.startSynchronization(syncConfig.get(0));

		SysSyncLogDto log = checkSyncLog(syncConfig.get(0), SynchronizationActionType.LINKED, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(newDN, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode("Flat");
		IdmRoleCatalogueDto catalogueSecond = getCatalogueByCode("Pardubice");
		Assert.assertNotNull(catalogueFirst);
		Assert.assertNotNull(catalogueSecond);

		// Delete log
		syncLogService.delete(log);
		
	}
	
	@Test
	public void n5_testSyncWithWfSituationUnlinked() {
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		IdmRoleDto role = new IdmRoleDto();
		role.setCode(ROLE_NAME);
		roleService.save(role);
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.UNLINKED, 1,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOGUE_CODE_FIRST);
		IdmRoleCatalogueDto catalogueSecond = getCatalogueByCode(CATALOGUE_CODE_SECOND);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertNotNull(catalogueSecond);

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void n5_testSyncWithWfSituationMissingAccount() {
		createRolesInSystem();
		this.getBean().deleteAllResourceData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		
		SysSystemDto systemDto = systemService.getByCode(SYSTEM_NAME);
		Assert.assertNotNull(systemDto);

		SysSyncConfigFilter filter = new SysSyncConfigFilter();
		filter.setSystemId(systemDto.getId());
		List<AbstractSysSyncConfigDto> syncConfig = syncConfigService.find(filter, null).getContent();
		Assert.assertEquals(1, syncConfig.size());
		
		// Start sync
		helper.startSynchronization(syncConfig.get(0));

		SysSyncLogDto log = checkSyncLog(syncConfig.get(0), SynchronizationActionType.MISSING_ACCOUNT, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());

		// Delete log
		syncLogService.delete(log);
	}
	
	@Test
	public void n1_testSyncWithWfSituationMissingEntityDoNotCreateCatalog() {
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleCatalog.ResolveCatalog", false);
		
		SysSystemDto system = initData();
		Assert.assertNull(getCatalogueByCode(CATALOGUE_CODE_SECOND));
		
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());
		

		List<IdmRoleDto> roles = roleService.find(new IdmRoleFilter(), null).getContent();
		roles = roles.stream().filter(role -> role.getName().equals(ROLE_NAME) || role.getName().equals(SECOND_ROLE_NAME)).collect(Collectors.toList());
		Assert.assertEquals(2, roles.size());
		//
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		roles = roleService.find(roleFilter, null).getContent();
		//
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOGUE_CODE_FIRST);
		IdmRoleCatalogueDto catalogueSecond = getCatalogueByCode(CATALOGUE_CODE_SECOND);
		Assert.assertNull(catalogueFirst);
		Assert.assertNull(catalogueSecond);

		// Delete log
		syncLogService.delete(log);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleCatalog.ResolveCatalog", true);
	}
	
	@Test
	public void n8_testSyncWithWfSituationMissingEntityOneFolderCatalog() {
		configurationService.setValue("idm.pub.acc.syncRole.roles.allToOneCatalog", CATALOG_FOLDER);
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOG_FOLDER);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertFalse(!isRoleInCatalogue(role.getId(), CATALOG_FOLDER));
		
		
		// Delete log
		syncLogService.delete(log);
		configurationService.setValue("idm.pub.acc.syncRole.roles.allToOneCatalog", null);
	}
	
	@Test
	public void n81_testSyncWithWfSituationMissingEntityCreateCatalog() {
		configurationService.setValue("idm.pub.acc.syncRole.roleCatalog.catalogueTreeInOneCatalog", CATALOG_FOLDER);
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOG_FOLDER);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertTrue(isRoleInCatalogue(role.getId(), CATALOG_FOLDER));
		
		// Delete log
		syncLogService.delete(log);
		//
		configurationService.setValue("idm.pub.acc.syncRole.roleCatalog.catalogueTreeInOneCatalog", null);
	}
	
	@Test
	public void n82_testSyncWithWfSituationMissingEntityTwiceSync() {
		String valueOfMemberAtt = "" + System.currentTimeMillis();
		String nameOfEav = "externalIdentifier";
		configurationService.setValue("idm.pub.acc.syncRole.identity.eav.externalIdentifier.code", nameOfEav);
		configurationService.setValue("idm.pub.acc.syncRole.roles.attributeNameOfMembership", ATTRIBUTE_MEMBER);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.update.resolveMembership", true);
		
		
		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmFormAttributeDto attribute = helper.createEavAttribute(nameOfEav, IdmIdentity.class, PersistentType.SHORTTEXT);
		helper.setEavValue(identity, attribute, IdmIdentity.class, valueOfMemberAtt, PersistentType.SHORTTEXT);
		
		SysSystemDto system = initData();
		
		this.getBean().deleteAllResourceData();
		this.getBean().addRoleToResource(ROLE_NAME, ATTRIBUTE_DN, valueOfMemberAtt);
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(0, content.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 1,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());

		content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(1, content.size());
		
		// Delete log
		syncLogService.delete(log);
		
		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log2 = checkSyncLog(config, SynchronizationActionType.LINKED, 1,
				OperationResultType.WF);

		Assert.assertFalse(log2.isRunning());
		Assert.assertFalse(log2.isContainsError());
		
		// after two synchronizations, there must be just one assign role
		content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(1, content.size());
		
		identityRoleService.delete(content.get(0));
		// Delete log
		syncLogService.delete(log2);
		//
		configurationService.deleteValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code");
		configurationService.deleteValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf");
		configurationService.setBooleanValue("idm.pub.acc.syncRole.update.resolveMembership", false);
	}
	
	@Test
	public void n83_testSyncWithWfSituationMissingEntityPriorityLevelRoleGarantee() {
		configurationService.setValue("idm.pub.acc.syncRole.roleCatalog.catalogueTreeInOneCatalog", CATALOG_FOLDER);
		configurationService.setValue("idm.pub.acc.syncRole.roles.create.priorityOfRoles", "1");
		//
		String roleGaranteeName = "roleGarantee" + System.currentTimeMillis();
		configurationService.setValue("idm.pub.acc.syncRole.roles.create.garanteeOfRoles", roleGaranteeName);
		
		IdmRoleDto garantee = new IdmRoleDto();
		garantee.setName(roleGaranteeName);
		roleService.save(garantee);
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOG_FOLDER);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertTrue(isRoleInCatalogue(role.getId(), CATALOG_FOLDER));
		// assert priority level
		Assert.assertEquals(role.getPriority(), 1);
		// assert role garantee
		IdmRoleGuaranteeRoleFilter filter = new IdmRoleGuaranteeRoleFilter();
		filter.setRole(role.getId());
		List<IdmRoleGuaranteeRoleDto> content = roleGuaranteeRoleService.find(filter, null).getContent();
		Assert.assertEquals(content.size(), 1);
		Assert.assertNotNull(content.get(0).getGuaranteeRole());
		IdmRoleDto assignedRoleGarantee = roleService.get(content.get(0).getGuaranteeRole());
		Assert.assertNotNull(assignedRoleGarantee);
		Assert.assertEquals(assignedRoleGarantee.getName(), roleGaranteeName);
		// Delete log
		syncLogService.delete(log);
		//
		configurationService.setValue("idm.pub.acc.syncRole.roles.create.priorityOfRoles", null);
		configurationService.setValue("idm.pub.acc.syncRole.roleCatalog.catalogueTreeInOneCatalog", null);
		configurationService.setValue("idm.pub.acc.syncRole.roles.create.garanteeOfRoles", roleGaranteeName);
	}
	
	@Test
	public void n94_testSyncWithWfSituationMissingEntityTriceSyncForwardManagement() {
		String valueOfMemberAtt = "" + System.currentTimeMillis();
		String nameOfEav = "externalIdentifier";
		String USER_SYSTEM_NAME = "TestName001";
		String overridedAttributeName = "EAV_ATTRIBUTE";
		configurationService.setValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code", USER_SYSTEM_NAME);
		configurationService.setValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf", overridedAttributeName);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.forwardManagement.value", true);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.update.manageforwardManagement", false);
		
		SysSystemDto userSystem = systemService.getByCode(USER_SYSTEM_NAME);
		if (userSystem == null) {
			userSystem = initData(USER_SYSTEM_NAME, true);
		}
		
		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmFormAttributeDto attribute = formService.getAttribute(formService.getDefinition(IdmIdentity.class), nameOfEav);
		if(attribute == null) {
			attribute = helper.createEavAttribute(nameOfEav, IdmIdentity.class, PersistentType.SHORTTEXT);
		}		helper.setEavValue(identity, attribute, IdmIdentity.class, valueOfMemberAtt, PersistentType.SHORTTEXT);
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(new IdmRoleFilter(), null).getContent();
		roles = roles.stream().filter(role -> role.getName().equals(ROLE_NAME) || role.getName().equals(SECOND_ROLE_NAME)).collect(Collectors.toList());
		Assert.assertEquals(2, roles.size());
		
		assertTrue(syncRoleLdapService.isForwardAccountManagement(userSystem.getId(), roles.get(0).getId(), ATTRIBUTE_NAME));
		assertTrue(syncRoleLdapService.isForwardAccountManagement(userSystem.getId(), roles.get(1).getId(), ATTRIBUTE_NAME));
		
		// Delete log
		syncLogService.delete(log);
		
		// set forward management to false
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.forwardManagement.value", false);
		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log2 = checkSyncLog(config, SynchronizationActionType.LINKED, 2,
				OperationResultType.WF);

		Assert.assertFalse(log2.isRunning());
		Assert.assertFalse(log2.isContainsError());
		
		// Delete log
		syncLogService.delete(log2);
		
		// forward management still true
		assertTrue(syncRoleLdapService.isForwardAccountManagement(userSystem.getId(), roles.get(0).getId(), ATTRIBUTE_NAME));
		
		// set true to set forward management on update
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.update.manageforwardManagement", true);
		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log3 = checkSyncLog(config, SynchronizationActionType.LINKED, 2,
				OperationResultType.WF);

		Assert.assertFalse(log3.isRunning());
		Assert.assertFalse(log3.isContainsError());

		// Delete log
		syncLogService.delete(log3);
		
		// forward management false
		assertFalse(syncRoleLdapService.isForwardAccountManagement(userSystem.getId(), roles.get(0).getId(), ATTRIBUTE_NAME));
		//
        configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.update.manageforwardManagement", false);
        configurationService.setValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code", null);
	}
	
	@Test
	public void n95_contractOnExclusionOptionTest() {
		String valueOfMemberAtt = "" + System.currentTimeMillis();
		String nameOfEav = "externalIdentifier";
		String USER_SYSTEM_NAME = "TestName001";
		String overridedAttributeName = "EAV_ATTRIBUTE";
		configurationService.setValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code", USER_SYSTEM_NAME);
		configurationService.setValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf", overridedAttributeName);
		configurationService.setValue("idm.pub.acc.syncRole.roles.nameOfRoles.doNotSentValueOnExclusion", ROLE_NAME);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roleSystem.update.manageforwardManagement", false);
		
		SysSystemDto userSystem = systemService.getByCode(USER_SYSTEM_NAME);
		if (userSystem == null) {
			userSystem = initData(USER_SYSTEM_NAME, true);
		}
		
		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmFormAttributeDto attribute = formService.getAttribute(formService.getDefinition(IdmIdentity.class), nameOfEav);
		if(attribute == null) {
			attribute = helper.createEavAttribute(nameOfEav, IdmIdentity.class, PersistentType.SHORTTEXT);
		}		helper.setEavValue(identity, attribute, IdmIdentity.class, valueOfMemberAtt, PersistentType.SHORTTEXT);
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(new IdmRoleFilter(), null).getContent();
		roles = roles.stream().filter(role -> role.getName().equals(ROLE_NAME) || role.getName().equals(SECOND_ROLE_NAME)).collect(Collectors.toList());
		Assert.assertEquals(2, roles.size());
		
		assertTrue(syncRoleLdapService.isSkipValueIfExcluded(userSystem.getId(), roles.get(0).getId(), overridedAttributeName, ATTRIBUTE_NAME));
		assertTrue(!syncRoleLdapService.isSkipValueIfExcluded(userSystem.getId(), roles.get(1).getId(), overridedAttributeName, ATTRIBUTE_NAME));
		
		// Delete log
		syncLogService.delete(log);
		
		// set forward management to false
		configurationService.setValue("idm.pub.acc.syncRole.roles.nameOfRoles.doNotSentValueOnExclusion", null);
		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log2 = checkSyncLog(config, SynchronizationActionType.LINKED, 2,
				OperationResultType.WF);

		Assert.assertFalse(log2.isRunning());
		Assert.assertFalse(log2.isContainsError());
		
		// Delete log
		syncLogService.delete(log2);
		
		// forward management still true
		assertTrue(syncRoleLdapService.isSkipValueIfExcluded(userSystem.getId(), roles.get(0).getId(), overridedAttributeName, ATTRIBUTE_NAME));
		
		// set true to set forward management on update
		configurationService.setBooleanValue("idm.pub.acc.syncRole.roles.update.nameOfRoles.manageSentValueOnExclusion", true);
		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log3 = checkSyncLog(config, SynchronizationActionType.LINKED, 2,
				OperationResultType.WF);

		Assert.assertFalse(log3.isRunning());
		Assert.assertFalse(log3.isContainsError());

		// Delete log
		syncLogService.delete(log3);
		
		// forward management false
		assertFalse(syncRoleLdapService.isSkipValueIfExcluded(userSystem.getId(), roles.get(0).getId(), overridedAttributeName, ATTRIBUTE_NAME));
		//
        configurationService.setBooleanValue("idm.pub.acc.syncRole.roles.update.nameOfRoles.manageSentValueOnExclusion", false);
        configurationService.setValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code", null);
	}
	
	@Test
	public void n93_testSyncWithWfSituationMissingEntityAddResource() {
		String USER_SYSTEM_NAME = "TestName001";
		String overridedAttributeName = "EAV_ATTRIBUTE";
		configurationService.setValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code", USER_SYSTEM_NAME);
		configurationService.setValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf", overridedAttributeName);
		SysSystemDto userSystem = initData(USER_SYSTEM_NAME, true);
		
		
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		// resource existing
		SysRoleSystemAttributeDto systemAttribute = getSystemAttribute(userSystem.getId(), overridedAttributeName, role.getId());
		Assert.assertNotNull(systemAttribute);
		String transformationScript = "'" + ATTRIBUTE_DN_VALUE + "'";
		Assert.assertEquals(systemAttribute.getTransformToResourceScript(), transformationScript);
		
		//find second role, which has special char
		roleFilter.setText(StringEscapeUtils.escapeJava(SECOND_ROLE_NAME));
		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		
		//find system attribute of second role
		systemAttribute = getSystemAttribute(userSystem.getId(), overridedAttributeName, roles.get(0).getId());
		Assert.assertNotNull(systemAttribute);
		transformationScript = "'" + SECOND_ATTRIBUTE_DN_VALUE + "'";
		//assert - has atribute have escaped special character
		Assert.assertEquals(systemAttribute.getTransformToResourceScript(), StringEscapeUtils.escapeJava(transformationScript));

		// Delete log
		syncLogService.delete(log);
		
		configurationService.deleteValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code");
		configurationService.deleteValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf");
	}
	
	@Test
	public void n91_testSyncWithWfSituationMissingResolveMember() {
		
		String valueOfMemberAtt = "" + System.currentTimeMillis();
		String nameOfEav = "externalIdentifier";
		configurationService.setValue("idm.pub.acc.syncRole.identity.eav.externalIdentifier.code", nameOfEav);
		configurationService.setValue("idm.pub.acc.syncRole.roles.attributeNameOfMembership", ATTRIBUTE_MEMBER);
		
		
		IdmIdentityDto identity = this.getHelper().createIdentity();
		IdmFormAttributeDto attribute = formService.getAttribute(formService.getDefinition(IdmIdentity.class), nameOfEav);
		if(attribute == null) {
			attribute = helper.createEavAttribute(nameOfEav, IdmIdentity.class, PersistentType.SHORTTEXT);
		}
		helper.setEavValue(identity, attribute, IdmIdentity.class, valueOfMemberAtt, PersistentType.SHORTTEXT);
		
		SysSystemDto system = initData();
		
		this.getBean().deleteAllResourceData();
		this.getBean().addRoleToResource(ROLE_NAME, ATTRIBUTE_DN, valueOfMemberAtt);
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(0, content.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 1,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());

		content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(1, content.size());
		
		identityRoleService.delete(content.get(0));

		// Delete log
		syncLogService.delete(log);
		
		configurationService.deleteValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code");
		configurationService.deleteValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf");
	}
	
	@Test
	public void n92_testSyncWithWfSituationLinkedResolveMember() {
		createRolesInSystem();
		final String newDN = "CN=" + ROLE_NAME + ",OU=Flat,OU=Pardubice,DC=bcvsolutions,DC=eu";
		this.getBean().initIdentityData(ROLE_NAME, newDN, SECOND_ROLE_NAME, SECOND_ATTRIBUTE_DN_VALUE);
		
		String valueOfMemberAtt = "" + System.currentTimeMillis();
		String nameOfEav = "externalIdentifier";
		configurationService.setValue("idm.pub.acc.syncRole.identity.eav.externalIdentifier.code", nameOfEav);
		configurationService.setValue("idm.pub.acc.syncRole.roles.attributeNameOfMembership", ATTRIBUTE_MEMBER);
		configurationService.setBooleanValue("idm.pub.acc.syncRole.update.resolveMembership", true);
		
		IdmIdentityDto identity = this.getHelper().createIdentity();

		IdmFormAttributeFilter attributeFilter = new IdmFormAttributeFilter();
		attributeFilter.setCode(nameOfEav);
		IdmFormAttributeDto formAttribute = formAttributeService.find(attributeFilter, null).getContent().stream().findFirst().orElse(null);
		Assert.assertNotNull(formAttribute);
		
		helper.setEavValue(identity, formAttribute, IdmIdentity.class, valueOfMemberAtt, PersistentType.SHORTTEXT);
		
		this.getBean().deleteAllResourceData();
		this.getBean().addRoleToResource(ROLE_NAME, ATTRIBUTE_DN, valueOfMemberAtt);
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size()); // role is in already synced ind idm
		
		IdmIdentityRoleFilter filter = new IdmIdentityRoleFilter();
		filter.setIdentityId(identity.getId());
		List<IdmIdentityRoleDto> content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(0, content.size()); // identity does not have assigned this role
		
		SysSystemDto systemDto = systemService.getByCode(SYSTEM_NAME);
		Assert.assertNotNull(systemDto);
		
		SysSyncConfigFilter syncFilter = new SysSyncConfigFilter();
		syncFilter.setSystemId(systemDto.getId());
		List<AbstractSysSyncConfigDto> syncConfig = syncConfigService.find(syncFilter, null).getContent();
		Assert.assertEquals(1, syncConfig.size()); // find synchronization config to start sync

		// Start sync
		helper.startSynchronization(syncConfig.get(0));

		SysSyncLogDto log = checkSyncLog(syncConfig.get(0), SynchronizationActionType.LINKED, 1,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());

		content = identityRoleService.find(filter , null).getContent();
		Assert.assertEquals(1, content.size());
		
		identityRoleService.delete(content.get(0));

		// Delete log
		syncLogService.delete(log);
		
		configurationService.deleteValue("idm.pub.acc.syncRole.provisioningOfIdentities.system.code");
		configurationService.deleteValue("idm.pub.acc.syncRole.system.mapping.attributeMemberOf");
		configurationService.setBooleanValue("idm.pub.acc.syncRole.update.resolveMembership", false);
	}
	
	private SysSyncIdentityConfigDto doCreateSyncConfig(SysSystemDto system) {

		SysSystemMappingFilter mappingFilter = new SysSystemMappingFilter();
		mappingFilter.setEntityType(SystemEntityType.ROLE);
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
		SysSyncIdentityConfigDto syncConfigCustom = new SysSyncIdentityConfigDto();
		syncConfigCustom.setReconciliation(true);
		syncConfigCustom.setCustomFilter(false);
		syncConfigCustom.setSystemMapping(mapping.getId());
		syncConfigCustom.setCorrelationAttribute(uidAttribute.getId());
		syncConfigCustom.setName(SYNC_CONFIG_NAME);
		syncConfigCustom.setLinkedAction(SynchronizationLinkedActionType.UPDATE_ENTITY);
		syncConfigCustom.setUnlinkedAction(SynchronizationUnlinkedActionType.LINK_AND_UPDATE_ENTITY);
		syncConfigCustom.setMissingEntityAction(SynchronizationMissingEntityActionType.CREATE_ENTITY);
		syncConfigCustom.setMissingAccountAction(ReconciliationMissingAccountActionType.DELETE_ENTITY);

		syncConfigCustom = (SysSyncIdentityConfigDto) syncConfigService.save(syncConfigCustom);

		SysSyncConfigFilter configFilter = new SysSyncConfigFilter();
		configFilter.setSystemId(system.getId());
		Assert.assertEquals(1, syncConfigService.find(configFilter, null).getTotalElements());
		return syncConfigCustom;
	}
	
	private SysSyncLogDto checkSyncLog(AbstractSysSyncConfigDto config, SynchronizationActionType actionType, int count,
			OperationResultType resultType) {
		SysSyncLogFilter logFilter = new SysSyncLogFilter();
		logFilter.setSynchronizationConfigId(config.getId());
		List<SysSyncLogDto> logs = syncLogService.find(logFilter, null).getContent();
		Assert.assertEquals(1, logs.size());
		SysSyncLogDto log = logs.get(0);
		if (actionType == null) {
			return log;
		}

		SysSyncActionLogFilter actionLogFilter = new SysSyncActionLogFilter();
		actionLogFilter.setSynchronizationLogId(log.getId());
		List<SysSyncActionLogDto> actions = syncActionLogService.find(actionLogFilter, null).getContent();

		SysSyncActionLogDto actionLog = actions.stream().filter(action -> {
			return actionType == action.getSyncAction();
		}).findFirst().orElse(null);

		Assert.assertNotNull(actionLog);
		Assert.assertEquals(resultType, actionLog.getOperationResult());
		SysSyncItemLogFilter itemLogFilter = new SysSyncItemLogFilter();
		itemLogFilter.setSyncActionLogId(actionLog.getId());
		List<SysSyncItemLogDto> items = syncItemLogService.find(itemLogFilter, null).getContent();
		Assert.assertEquals(count, items.size());

		return log;
	}
	
	private SysSystemDto initData() {
		return initData(SYSTEM_NAME, false);
	}
	
	private SysSystemDto initData(String systemName, boolean isProvisioning) {

		// create test system
		SysSystemDto system = helper.createSystem(TestRoleResource.TABLE_NAME, systemName);
		Assert.assertNotNull(system);

		// generate schema for system
		List<SysSchemaObjectClassDto> objectClasses = systemService.generateSchema(system);

		// Create synchronization mapping
		SysSystemMappingDto syncSystemMapping = new SysSystemMappingDto();
		syncSystemMapping.setName("default_" + System.currentTimeMillis());
		syncSystemMapping.setEntityType(SystemEntityType.ROLE);
		syncSystemMapping.setOperationType(SystemOperationType.SYNCHRONIZATION);
		syncSystemMapping.setObjectClass(objectClasses.get(0).getId());
		if (isProvisioning) {
			syncSystemMapping.setEntityType(SystemEntityType.IDENTITY);
			syncSystemMapping.setOperationType(SystemOperationType.PROVISIONING);
		}
		final SysSystemMappingDto syncMapping = systemMappingService.save(syncSystemMapping);
		createMapping(system, syncMapping);
		if (!isProvisioning) {
			this.getBean().initIdentityData(ROLE_NAME, ATTRIBUTE_DN_VALUE, SECOND_ROLE_NAME, SECOND_ATTRIBUTE_DN_VALUE);
		}
		return system;

	}
	
	private void createMapping(SysSystemDto system, final SysSystemMappingDto entityHandlingResult) {
		SysSchemaAttributeFilter schemaAttributeFilter = new SysSchemaAttributeFilter();
		schemaAttributeFilter.setSystemId(system.getId());

		Page<SysSchemaAttributeDto> schemaAttributesPage = schemaAttributeService.find(schemaAttributeFilter, null);
		schemaAttributesPage.forEach(schemaAttr -> {
			if (ATTRIBUTE_NAME.equals(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMapping = new SysSystemAttributeMappingDto();
				attributeMapping.setUid(true);
				attributeMapping.setEntityAttribute(true);
				attributeMapping.setIdmPropertyName("name");
				attributeMapping.setName(schemaAttr.getName());
				attributeMapping.setSchemaAttribute(schemaAttr.getId());
				attributeMapping.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMapping);

			} else if (ATTRIBUTE_DN.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_DN);
				attributeMappingTwo.setEntityAttribute(false);
				attributeMappingTwo.setExtendedAttribute(true);
				attributeMappingTwo.setName("distinguishedName");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);

			} else if (ATTRIBUTE_MEMBER.equalsIgnoreCase(schemaAttr.getName())) {
				SysSystemAttributeMappingDto attributeMappingTwo = new SysSystemAttributeMappingDto();
				attributeMappingTwo.setIdmPropertyName(ATTRIBUTE_MEMBER);
				attributeMappingTwo.setEntityAttribute(false);
				attributeMappingTwo.setExtendedAttribute(true);
				attributeMappingTwo.setName("MEMBER");
				attributeMappingTwo.setSchemaAttribute(schemaAttr.getId());
				attributeMappingTwo.setSystemMapping(entityHandlingResult.getId());
				schemaAttributeMappingService.save(attributeMappingTwo);

			}
		});
	}

	@Transactional
	public void initIdentityData(String roleName, String eavAttribute, String roleNameTwo, String eavAttributeTwo) {
		deleteAllResourceData();

		TestRoleResource resourceUserOne = new TestRoleResource();
		resourceUserOne.setName(roleName);
		resourceUserOne.setEavAttribute(eavAttribute);
		entityManager.persist(resourceUserOne);
		
		TestRoleResource resourceUserTwo = new TestRoleResource();
		resourceUserTwo.setName(roleNameTwo);
		resourceUserTwo.setEavAttribute(eavAttributeTwo);
		entityManager.persist(resourceUserTwo);

	}
	
	@Transactional
	public void addRoleToResource(String roleName, String eavAttribute, String member) {

		TestRoleResource resourceUserOne = new TestRoleResource();
		resourceUserOne.setName(roleName);
		resourceUserOne.setEavAttribute(eavAttribute);
		resourceUserOne.setMember(member);
		entityManager.persist(resourceUserOne);

	}
	
	@Transactional
	public void deleteAllResourceData() {
		// Delete all
		Query q = entityManager.createNativeQuery("DELETE FROM " + TestRoleResource.TABLE_NAME);
		q.executeUpdate();
	}
	
	private RoleWorkflowAdSyncTest getBean() {
		return applicationContext.getBean(this.getClass());
	}
	
	private IdmRoleCatalogueDto getCatalogueByCode(String code) {
	    IdmRoleCatalogueFilter filter = new IdmRoleCatalogueFilter();
	    filter.setCode(code);
	    List<IdmRoleCatalogueDto> result = roleCatalogueService.find(filter, null).getContent();
	    if (result.size() != 1) {
	        return null;
	    }
	    return result.get(0);
	}
	
	private void createRolesInSystem () {
		SysSystemDto system = initData();
		
		IdmRoleFilter roleFilter = new IdmRoleFilter();
		roleFilter.setText(ROLE_NAME);
		List<IdmRoleDto> roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(0, roles.size());
		
		Assert.assertNotNull(system);
		SysSyncIdentityConfigDto config = doCreateSyncConfig(system);
		config.setLinkedActionWfKey(wfExampleKey);
		config.setMissingAccountActionWfKey(wfExampleKey);
		config.setMissingEntityActionWfKey(wfExampleKey);
		config.setUnlinkedActionWfKey(wfExampleKey);
		config = (SysSyncIdentityConfigDto) syncConfigService.save(config);

		// Start sync
		helper.startSynchronization(config);

		SysSyncLogDto log = checkSyncLog(config, SynchronizationActionType.MISSING_ENTITY, 2,
				OperationResultType.WF);

		Assert.assertFalse(log.isRunning());
		Assert.assertFalse(log.isContainsError());

		roles = roleService.find(roleFilter, null).getContent();
		Assert.assertEquals(1, roles.size());
		IdmRoleDto role = roles.get(0);
		List<IdmFormValueDto> dnValues = formService.getValues(role, ATTRIBUTE_DN);
		Assert.assertEquals(1, dnValues.size());
		Assert.assertEquals(ATTRIBUTE_DN_VALUE, dnValues.get(0).getValue());
		IdmRoleCatalogueDto catalogueFirst = getCatalogueByCode(CATALOGUE_CODE_FIRST);
		IdmRoleCatalogueDto catalogueSecond = getCatalogueByCode(CATALOGUE_CODE_SECOND);
		Assert.assertNotNull(catalogueFirst);
		Assert.assertNotNull(catalogueSecond);

		// Delete log
		syncLogService.delete(log);
	}
	
	/**
	 * Returns existing system attribute or null
	 * 
	 * @param attr
	 * @return
	 */
	private SysRoleSystemAttributeDto getSystemAttribute(UUID roleSystem, String attributeName, UUID roleId) {
		SysRoleSystemAttributeFilter filter = new SysRoleSystemAttributeFilter();
		filter.setRoleSystemId(getSysRoleSystem(roleSystem, roleId));
		List<SysRoleSystemAttributeDto> content = roleSystemAttributeService.find(filter, null).getContent();
		for (SysRoleSystemAttributeDto attribute : content) {
			if (attribute.getName().equals(attributeName)) {
				return attribute;
			}
		}
		return null;
	}
	
	/**
	 * Returns existing role's system
	 * 
	 * @param systemId
	 * @param roleId
	 * @param objectClassName
	 * @return
	 */
	private UUID getSysRoleSystem(UUID systemId, UUID roleId) {
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setRoleId(roleId);
		filter.setSystemId(systemId);
		List<SysRoleSystemDto> roleSystem = roleSystemService.find(filter, null).getContent();
		SysRoleSystemDto attribute = roleSystem.stream().findFirst().orElse(null);
		Assert.assertNotNull(attribute);
		return attribute.getId();
	}
	
	private boolean isRoleInCatalogue(UUID roleId, String inCatalog) {
		List<IdmRoleCatalogueRoleDto> allCatalogsByRole = roleCatalogueRoleService.findAllByRole(roleId);
		if(!allCatalogsByRole.isEmpty()) {
			IdmRoleCatalogueDto inCatalogDto = roleCatalogueService.get(allCatalogsByRole.get(0).getRoleCatalogue());
			IdmRoleCatalogueDto rootCatalogue = inCatalogDto.getParent() != null ? getRootCatalogue(allCatalogsByRole.get(0).getRoleCatalogue()) : inCatalogDto;
			IdmRoleCatalogueDto catalogue = getCatalogueByCode(inCatalog);
			Assert.assertNotNull(catalogue);
			return catalogue.getId().equals(rootCatalogue.getId());
		}
		return false;
	}
	
	private IdmRoleCatalogueDto getRootCatalogue(UUID catalogueId) {
		List<IdmRoleCatalogueDto> findAllParents = roleCatalogueService.findAllParents(catalogueId);
		return findAllParents.stream().filter(parent -> parent.getParent() == null).findFirst().orElse(null);
	}
}
