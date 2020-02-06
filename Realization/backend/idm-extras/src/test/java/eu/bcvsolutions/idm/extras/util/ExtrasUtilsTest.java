package eu.bcvsolutions.idm.extras.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import java.time.LocalDate;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.core.api.config.domain.ContractSliceConfiguration;
import eu.bcvsolutions.idm.core.api.dto.BaseDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractSliceDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmRoleCatalogueRoleFilter;
import eu.bcvsolutions.idm.core.api.service.IdmConfigurationService;
import eu.bcvsolutions.idm.core.api.service.IdmContractSliceService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleCatalogueRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmTreeNodeService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormDefinitionDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormValueDto;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormAttributeService;
import eu.bcvsolutions.idm.core.eav.api.service.IdmFormDefinitionService;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityContract;
import eu.bcvsolutions.idm.core.model.entity.IdmTreeNode;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Tests for {@link ExtrasUtils}
 *
 * @author Ondrej Kopr
 * @author Tomáš Doischer
 *
 */
public class ExtrasUtilsTest extends AbstractIntegrationTest {
	@Autowired
	private ExtrasUtils extrasUtils;
	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleCatalogueRoleService catalogueService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmFormDefinitionService formDefinitionService;
	@Autowired
	private IdmFormAttributeService formAttributeService;
	@Autowired
	private IdmTreeNodeService treeNodeService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmConfigurationService configurationService;
	@Autowired
	private IdmContractSliceService contractSliceService;
	
	private final String ROLE_CODE = "role_code";
	private final String ROLE_TWO_CODE = "role_two_code";
	private final String FORM_ATTRIBUTE_ONE_CODE = "form_attr";

	@Test
	public void testGetTitlesAfterNullValue() {
		String titlesAfter = extrasUtils.getTitlesAfter(null);
		assertNull(titlesAfter);
	}

	@Test
	public void testGetTitlesAfterEmptyValue() {
		String titlesAfter = extrasUtils.getTitlesAfter("");
		assertNull(titlesAfter);
	}

	@Test
	public void testGetTitlesAfterNotExisting() {
		String titlesAfter = extrasUtils.getTitlesAfter("ung.");
		assertNotNull(titlesAfter);
		assertTrue(titlesAfter.isEmpty());
	}

	@Test
	public void testGetTitlesAfterNotExistingTwo() {
		String titlesAfter = extrasUtils.getTitlesAfter("Ing.");
		assertNotNull(titlesAfter);
		assertTrue(titlesAfter.isEmpty());
	}

	@Test
	public void testGetTitlesAfter() {
		String titlesAfter = extrasUtils.getTitlesAfter("CSc. Ing.");
		assertNotNull(titlesAfter);
		assertFalse(titlesAfter.isEmpty());
		assertTrue(titlesAfter.equals("CSc."));
	}

	@Test
	public void testGetTitlesAfterTestTwo() {
		String titles = "Ph.D. MBA";
		String titlesBefore = extrasUtils.getTitlesBefore(titles);
		assertTrue(titlesBefore.isEmpty());

		String titlesAfter = extrasUtils.getTitlesAfter(titles);
		assertNotNull(titlesAfter);
		assertFalse(titlesAfter.isEmpty());
		assertTrue(titlesAfter.equals("Ph.D., MBA"));
	}

	@Test
	public void testGetTitlesAfterTestThree() {
		String titles = "Ing. MBA";
		String titlesBefore = extrasUtils.getTitlesBefore(titles);
		assertNotNull(titlesBefore);
		assertFalse(titlesBefore.isEmpty());
		assertTrue(titlesBefore.equals("Ing."));

		String titlesAfter = extrasUtils.getTitlesAfter(titles);
		assertNotNull(titlesAfter);
		assertFalse(titlesAfter.isEmpty());
		assertTrue(titlesAfter.equals("MBA"));
	}

	@Test
	public void testGetTitlesBeforeNullValue() {
		String titlesBefore = extrasUtils.getTitlesBefore(null);
		assertNull(titlesBefore);
	}

	@Test
	public void testGetTitlesBeforeEmptyValue() {
		String titlesBefore = extrasUtils.getTitlesBefore("");
		assertNull(titlesBefore);
	}

	@Test
	public void testGetTitlesBeforeNotExisting() {
		String titlesBefore = extrasUtils.getTitlesBefore("czc.");
		assertNotNull(titlesBefore);
		assertTrue(titlesBefore.isEmpty());
	}

	@Test
	public void testGetTitlesBeforeNotExistingTwo() {
		String titlesBefore = extrasUtils.getTitlesBefore("CSc.");
		assertNotNull(titlesBefore);
		assertTrue(titlesBefore.isEmpty());
	}

	@Test
	public void testGetTitlesBefore() {
		String titlesBefore = extrasUtils.getTitlesBefore("CSc. Ing.");
		assertNotNull(titlesBefore);
		assertFalse(titlesBefore.isEmpty());
		assertTrue(titlesBefore.equals("Ing."));
	}
	
	@Test
	public void getRoleGuaranteesByRoleTest() {
		IdmIdentityDto assignee = getHelper().createIdentity();

		IdmRoleDto role = getHelper().createRole();
		IdmRoleDto roleGuaranteed = getHelper().createRole();
		IdmRoleDto roleGuaranteedTwo = getHelper().createRole();
		
		getHelper().createRoleGuaranteeRole(roleGuaranteedTwo, role);
		getHelper().createRoleGuaranteeRole(roleGuaranteed, role);
		
		getHelper().createIdentityRole(assignee, role);
		List<IdmRoleGuaranteeRoleDto> roleGuarantees = extrasUtils.getRoleGuaranteesByRole(assignee.getId());
		Assert.assertEquals(2, roleGuarantees.size());
	}
	
	@Test
	public void getDirectRoleGuaranteesTest() {
		IdmIdentityDto guarantee = getHelper().createIdentity();
		IdmRoleDto roleOne = getHelper().createRole(ROLE_CODE);
		IdmRoleDto roleTwo = getHelper().createRole(ROLE_TWO_CODE);
		
		getHelper().createRoleGuarantee(roleOne, guarantee);
		getHelper().createRoleGuarantee(roleTwo, guarantee);
		
		List<IdmRoleGuaranteeDto> guarantees = extrasUtils.getDirectRoleGuarantees(guarantee);
		
		// although we have created two guarantees, the method only returns one
		Assert.assertEquals(1, guarantees.size());
	}
	
	@Test
	public void getManagerFromIdentityTest() {
		IdmIdentityDto manager = getHelper().createIdentity();
		IdmIdentityDto guaranteed = getHelper().createIdentity();
		IdmIdentityContractDto guaranteedContract = getHelper().getPrimeContract(guaranteed);
		
		getHelper().createContractGuarantee(guaranteedContract.getId(), manager.getId());
		
		IdmIdentityDto managerFound = extrasUtils.getManagerFromIdentity(guaranteed);
		Assert.assertNotNull(managerFound);
		
		// null contract
		IdmIdentityDto guaranteedTwo = new IdmIdentityDto(UUID.randomUUID(), getHelper().createName());
		IdmIdentityDto managerFoundTwo = extrasUtils.getManagerFromIdentity(guaranteedTwo);
		Assert.assertNull(managerFoundTwo);
	}
	
	@Test
	public void getEavValueForTreeNodeTest() {
		IdmTreeNodeDto node = getHelper().createTreeNode();
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmTreeNode.class.getCanonicalName());
		IdmFormAttributeDto attribute = createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);

		definition.setMain(true);
		formDefinitionService.save(definition);
		
		IdmFormInstanceDto instance = new IdmFormInstanceDto();
		instance.setOwnerId(node.getId());
		instance.setOwnerType(IdmTreeNodeDto.class);
		instance.setFormDefinition(definition);
		instance.setValues(Lists.newArrayList(prepareValue(attribute, "TEST_VALUE")));
		node.setEavs(Lists.newArrayList(instance));
		treeNodeService.save(node);
		
		String value = (String) extrasUtils.getEavValueForTreeNode(node.getId(), definition, FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", value);
		
		String withDefault = (String) extrasUtils.getEavValueForTreeNode(node.getId(), definition, FORM_ATTRIBUTE_ONE_CODE, "default");
		// default should not be used
		Assert.assertEquals("TEST_VALUE", withDefault);
		
		String reallyWithDefault = (String) extrasUtils.getEavValueForTreeNode(node.getId(), definition, "nonexistant_code", "default");
		Assert.assertEquals("default", reallyWithDefault);
		
		String valueTwo = (String) extrasUtils.getEavValueForTreeNode(node.getId(), FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", valueTwo);
	}
	
	@Test
	public void getEavValueForIdentity() {
		IdmIdentityDto identity = getHelper().createIdentity();
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentity.class.getCanonicalName());
		IdmFormAttributeDto attribute = createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);

		definition.setMain(true);
		formDefinitionService.save(definition);
		
		IdmFormInstanceDto instance = new IdmFormInstanceDto();
		instance.setOwnerId(identity.getId());
		instance.setOwnerType(IdmIdentityDto.class);
		instance.setFormDefinition(definition);
		instance.setValues(Lists.newArrayList(prepareValue(attribute, "TEST_VALUE")));
		identity.setEavs(Lists.newArrayList(instance));
		identityService.save(identity);
		
		String value = (String) extrasUtils.getEavValueForIdentity(identity.getId(), definition, FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", value);
		
		String valueTwo = (String) extrasUtils.getEavValueForIdentity(identity.getId(), FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", valueTwo);
		
		String valueThree = (String) extrasUtils.getEavValueForIdentity(identity.getId(), attribute);
		Assert.assertEquals("TEST_VALUE", valueThree);
	}
	
	@Test
	public void getEavValueForIdentityContractTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identity);
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentityContract.class.getCanonicalName());
		IdmFormAttributeDto attribute = createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);
		
		definition.setMain(true);
		formDefinitionService.save(definition);

		IdmFormInstanceDto instance = new IdmFormInstanceDto();
		instance.setOwnerId(contract.getId());
		instance.setOwnerType(IdmIdentityContractDto.class);
		instance.setFormDefinition(definition);
		instance.setValues(Lists.newArrayList(prepareValue(attribute, "TEST_VALUE")));
		contract.setEavs(Lists.newArrayList(instance));
		identityContractService.save(contract);
		
		String value = (String) extrasUtils.getEavValueForIdentityContract(contract.getId(), definition, FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", value);
		
		String valueTwo = (String) extrasUtils.getEavValueForIdentityContract(contract.getId(), FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("TEST_VALUE", valueTwo);
	}

	@Test
	public void getOneValueTest() {
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentity.class.getCanonicalName());
		IdmFormAttributeDto attribute = createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);
		IdmFormValueDto value = prepareValue(attribute, "test_value");
		Optional<IdmFormValueDto> optional = Optional.of(value);
		
		Object result = ExtrasUtils.getOneValue(optional);
		Assert.assertEquals("test_value", result);
		
		// empty value
		Optional<IdmFormValueDto> optionalTwo = Optional.empty();
		
		Object resultTwo = ExtrasUtils.getOneValue(optionalTwo);
		Assert.assertNull(resultTwo);
}
	
	@Test
	public void getPrimeContractTest() {
		IdmIdentityDto tester = getHelper().createIdentity();
		IdmIdentityContractDto testerContractPrime = getHelper().createIdentityContact(tester);
		testerContractPrime.setMain(true);
		identityContractService.saveInternal(testerContractPrime);
		getHelper().createIdentityContact(tester);
		
		List<IdmIdentityContractDto> contracts = identityContractService.findAllByIdentity(tester.getId());
		
		// the user should have 3 contracts
		Assert.assertEquals(3, contracts.size());
		
		IdmIdentityContractDto prime = extrasUtils.getPrimeContract(tester.getId());
		Assert.assertEquals(testerContractPrime.getId(), prime.getId());
	}
	
	@Test
	public void isInProtectionIntervalTest() {
		// is in protection interval, return true
		IdmIdentityDto identity = getHelper().createIdentity();
		configurationService.setValue(ContractSliceConfiguration.PROPERTY_PROTECTION_INTERVAL, "5");
		
//		LocalDate sliceValidTill = LocalDate.now("2019-12-05");
		LocalDate sliceValidTill = LocalDate.of(2019, 12, 5);
		
		LocalDate nextsliceValidFrom = LocalDate.of(2019, 12, 9);
		
		IdmContractSliceDto slice = getHelper().createContractSlice(identity);
		slice.setContractValidTill(sliceValidTill);
		contractSliceService.save(slice);

		IdmContractSliceDto nextSlice = getHelper().createContractSlice(identity);
		nextSlice.setContractValidFrom(nextsliceValidFrom);
		contractSliceService.save(nextSlice);
		
		boolean result = extrasUtils.isInProtectionInterval(slice, nextSlice);
		Assert.assertTrue(result);
		
		// is not in protection interval, return false
		LocalDate sliceValidTillTwo = LocalDate.of(2019, 12, 5);
		LocalDate nextsliceValidFromTwo = LocalDate.of(2019, 12, 15);
		
		IdmContractSliceDto sliceTwo = getHelper().createContractSlice(identity);
		sliceTwo.setContractValidTill(sliceValidTillTwo);
		contractSliceService.save(sliceTwo);

		IdmContractSliceDto nextSliceTwo = getHelper().createContractSlice(identity);
		nextSliceTwo.setContractValidFrom(nextsliceValidFromTwo);
		contractSliceService.save(nextSliceTwo);
		
		boolean resultTwo = extrasUtils.isInProtectionInterval(slice, nextSlice);
		Assert.assertTrue(resultTwo);
		
		// null slice.getContractValidTill() and nextSlice.getContractValidFrom()
		IdmContractSliceDto sliceThree = getHelper().createContractSlice(identity);
		IdmContractSliceDto nextSliceThree = getHelper().createContractSlice(identity);
		
		boolean resultThree = extrasUtils.isInProtectionInterval(sliceThree, nextSliceThree);
		Assert.assertFalse(resultThree);
	}
	
	@Test
	public void saveEavByNameTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentity.class.getCanonicalName());
		createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);

		extrasUtils.saveEavByName(identity.getId(), definition, "test_value", FORM_ATTRIBUTE_ONE_CODE);
		
		String value = (String) extrasUtils.getEavValueForIdentity(identity.getId(), definition, FORM_ATTRIBUTE_ONE_CODE);
		Assert.assertEquals("test_value", value);
	}
	
	@Test
	public void removeDiacriticTest() {
		String withDiacritics = "Žluťoučký kůň úpěl ďábelské ódy.";
		String withoutDiacritics = extrasUtils.removeDiacritic(withDiacritics);
		String expected = "Zlutoucky kun upel dabelske ody.";
		
		Assert.assertEquals(expected, withoutDiacritics);
	}
	
	@Test
	public void removeWhitespacesTest() {
		String withWhiteSpaces = "abc  as \ndsdf";
		String withoutWhiteSpaces = extrasUtils.removeWhitespaces(withWhiteSpaces);
		String expected = "abcasdsdf";
		
		Assert.assertEquals(expected, withoutWhiteSpaces);
	}
	
	@Test
	public void getStringValueTest() {
		String notNull = "string";
		String isNull = null;
		String stringValueNotNull = extrasUtils.getStringValue(notNull);
		String stringValueIsNull = extrasUtils.getStringValue(isNull);
		
		Assert.assertEquals(notNull, stringValueNotNull);
		Assert.assertEquals("", stringValueIsNull);
	}

	
	@Test
	public void getIdentityByExternalCodeTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		identity.setExternalCode("555");
		identityService.saveInternal(identity);
		IdmIdentityDto found = extrasUtils.getIdentityByExternalCode("555");
		
		Assert.assertEquals(identity, found);
		
		// nonexistent identity
		IdmIdentityDto foundTwo = extrasUtils.getIdentityByExternalCode("123");
		
		Assert.assertNull(foundTwo);
	}
	
	@Test
	public void getEavValuesForContractByIdentityTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identity);
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentityContract.class.getCanonicalName());
		createFormAttributeMultivalued(definition, FORM_ATTRIBUTE_ONE_CODE);
		
		extrasUtils.saveEavByNameToContract(contract.getId(), definition, Lists.newArrayList("TEST_VALUE", "TEST_VALUE_TWO"),
				FORM_ATTRIBUTE_ONE_CODE);
		
		List<Object> values = extrasUtils.getEavValuesForContractByIdentity(identity.getId(), FORM_ATTRIBUTE_ONE_CODE, definition);
		Assert.assertEquals(2, values.size());
		Assert.assertTrue(values.contains("TEST_VALUE"));
		Assert.assertTrue(values.contains("TEST_VALUE_TWO"));
		
		// null contract
		IdmIdentityDto identityTwo = new IdmIdentityDto();
		identityTwo.setUsername(getHelper().createName());
		identityTwo.setId(UUID.randomUUID());
		identityService.save(identityTwo);
		List<Object> valuesTwo = extrasUtils.getEavValuesForContractByIdentity(identityTwo.getId(), FORM_ATTRIBUTE_ONE_CODE, definition);
		Assert.assertEquals(0, valuesTwo.size());
	}
	
	@Test
	public void findIdentityOwnersOfEavTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentity.class.getCanonicalName());
		IdmFormAttributeDto attribute = createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);
		
		definition.setMain(true);
		formDefinitionService.save(definition);
		
		IdmFormInstanceDto instance = new IdmFormInstanceDto();
		instance.setOwnerId(identity.getId());
		instance.setOwnerType(IdmIdentityDto.class);
		instance.setFormDefinition(definition);
		instance.setValues(Lists.newArrayList(prepareValue(attribute, "TEST_VALUE")));
		identity.setEavs(Lists.newArrayList(instance));
		identityService.save(identity);
		
		List<BaseDto> result = extrasUtils.findIdentityOwnersOfEav(FORM_ATTRIBUTE_ONE_CODE, "TEST_VALUE");
		Assert.assertEquals(1, result.size());
		Assert.assertEquals(identity.getId(), result.get(0).getId());
	}
	
	@Test
	public void addRoleToCatalogueTest() {
		IdmRoleDto role = getHelper().createRole();
		IdmRoleCatalogueDto catalog = getHelper().createRoleCatalogue();
		
		extrasUtils.addRoleToCatalogue(role.getCode(), catalog.getCode());
		
		IdmRoleCatalogueRoleFilter f = new IdmRoleCatalogueRoleFilter();
		f.setRoleCatalogueId(catalog.getId());
		
		List<IdmRoleCatalogueRoleDto> result = catalogueService.find(f, null).getContent();
		Assert.assertEquals(1, result.size());
		UUID roleInCatalogueId = result.get(0).getRole();
		Assert.assertEquals(role.getId(), roleInCatalogueId);
		
		// null role
		IdmRoleCatalogueDto catalogTwo = getHelper().createRoleCatalogue();
		extrasUtils.addRoleToCatalogue("nonexistant", catalogTwo.getCode());
		
		IdmRoleCatalogueRoleFilter fTwo = new IdmRoleCatalogueRoleFilter();
		fTwo.setRoleCatalogueId(catalog.getId());
		
		List<IdmRoleCatalogueRoleDto> resultTwo = catalogueService.find(fTwo, null).getContent();
		UUID roleInCatalogueIdTwo = resultTwo.get(0).getRole();
		IdmRoleDto roleTwo = roleService.get(roleInCatalogueIdTwo);
		Assert.assertNotEquals("nonexistant", roleTwo.getCode());

		// role already in the catalogue
		extrasUtils.addRoleToCatalogue(role.getCode(), catalog.getCode());
		
		f.setRoleCatalogueId(catalog.getId());
		
		List<IdmRoleCatalogueRoleDto> resultThree = catalogueService.find(f, null).getContent();
		Assert.assertEquals(1, resultThree.size());
		UUID roleInCatalogueIdThree = resultThree.get(0).getRole();
		Assert.assertEquals(role.getId(), roleInCatalogueIdThree);
	}
	
	@Test
	public void serializeAndDeserializeTest() {
		String before = "abc defg f";
		byte[] serialized = ExtrasUtils.serialize(before);
		String deserialized = (String) ExtrasUtils.deserialize(serialized);
		
		Assert.assertEquals(before, deserialized);
	}
	
	
	@Test
	public void hasRoleOnContractTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto identityContract = getHelper().createIdentityContact(identity);
		IdmRoleDto role = getHelper().createRole();
		IdmIdentityRoleDto identityRole = getHelper().createIdentityRole(identityContract, role);
		List<IdmIdentityRoleDto> identityRoles = new ArrayList<>();
		identityRoles.add(identityRole);
		
		boolean hasRole = extrasUtils.hasRoleOnContract(identityRoles, role.getName());
		Assert.assertTrue(hasRole);
	}
	
	@Test
	public void removeRoleFromIdentityTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto identityContract = getHelper().createIdentityContact(identity);
		IdmRoleDto role = getHelper().createRole();
		getHelper().createIdentityRole(identityContract, role);
		
		List<IdmIdentityRoleDto> foundFirst = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(1, foundFirst.size());
		
		extrasUtils.removeRoleFromIdentity(identity.getId().toString(), role.getCode());
		
		List<IdmIdentityRoleDto> foundSecond = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(0, foundSecond.size());
	}
	
	@Test
	public void removeRoleFromAllContractsTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto identityContract = getHelper().createIdentityContact(identity);
		IdmIdentityContractDto identityContractTwo = getHelper().createIdentityContact(identity);
		IdmRoleDto role = getHelper().createRole();
		getHelper().createIdentityRole(identityContract, role);
		getHelper().createIdentityRole(identityContractTwo, role);
		
		List<IdmIdentityRoleDto> foundFirst = identityRoleService.findAllByIdentity(identity.getId());
		// should have the role on both contracts
		Assert.assertEquals(2, foundFirst.size());
		
		extrasUtils.removeRoleFromAllContracts(identity.getId().toString(), role.getCode());
		
		List<IdmIdentityRoleDto> foundSecond = identityRoleService.findAllByIdentity(identity.getId());
		Assert.assertEquals(0, foundSecond.size());
	}
	
	@Test
	public void addRoleToIdentityTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityDto identityTwo = getHelper().createIdentity();
		IdmIdentityContractDto identityContract = getHelper().createIdentityContact(identity);
		IdmRoleDto role = getHelper().createRole();
		IdmIdentityRoleDto identityRole = getHelper().createIdentityRole(identityContract, role);
		
		extrasUtils.addRoleToIdentity(identityTwo.getId(), identityRole);
		List<IdmIdentityRoleDto> found = identityRoleService.findAllByIdentity(identityTwo.getId());
		
		Assert.assertEquals(1, found.size());
	}
	
	@Test
	public void addRoleToIdentityRoleCodeTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		getHelper().createIdentityContact(identity);
		IdmRoleDto role = getHelper().createRole();
		
		extrasUtils.addRoleToIdentity(identity.getId(), role.getCode());
		List<IdmIdentityRoleDto> found = identityRoleService.findAllByIdentity(identity.getId());
		
		Assert.assertEquals(1, found.size());
	}
	
	@Test
	public void saveEavByNameToContractTest() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identity);
		
		IdmFormDefinitionDto definition = createFormDefinition(IdmIdentityContract.class.getCanonicalName());
		createFormAttribute(definition, FORM_ATTRIBUTE_ONE_CODE);

		extrasUtils.saveEavByNameToContract(contract.getId(), definition, Lists.newArrayList("test_value"), FORM_ATTRIBUTE_ONE_CODE);
		
		Object value = extrasUtils.getEavValueForContractByIdentity(identity.getId(), FORM_ATTRIBUTE_ONE_CODE, definition);
		Assert.assertEquals("test_value", value);
		
		// null value
		createFormAttribute(definition, getHelper().createName());
		Object valueTwo = extrasUtils.getEavValueForContractByIdentity(identity.getId(), FORM_ATTRIBUTE_ONE_CODE, definition);
		Assert.assertEquals("test_value", valueTwo);
	}
	
	@Test
	public void getManagerForContractTest() {
		IdmIdentityDto manager = getHelper().createIdentity();
		IdmIdentityDto guaranteed = getHelper().createIdentity();
		IdmIdentityContractDto guaranteedContract = getHelper().getPrimeContract(guaranteed);
		
		getHelper().createContractGuarantee(guaranteedContract.getId(), manager.getId());
		IdmIdentityDto managerFound = extrasUtils.getManagerForContract(guaranteedContract.getId());
		
		// the contract has 3 managers, we don't know which one we'll get so we only check for not null
		Assert.assertNotNull(managerFound);
	}
	
	@Test
	public void getAllManagersForContractTest() {
		IdmIdentityDto manager = getHelper().createIdentity();
		IdmIdentityDto guaranteed = getHelper().createIdentity();
		IdmIdentityContractDto guaranteedContract = getHelper().getPrimeContract(guaranteed);
		
		getHelper().createContractGuarantee(guaranteedContract.getId(), manager.getId());
		
		List<IdmIdentityDto> managersFound = extrasUtils.getAllManagersForContract(guaranteedContract.getId());
		
		// it is difficult to predict how many managers we will find
		Assert.assertFalse(managersFound.isEmpty());
	}
	
	@Test
	public void getUsersByRoleNameTest() {
		IdmIdentityDto userOne = getHelper().createIdentity();
		IdmIdentityContractDto userOneContract = getHelper().getPrimeContract(userOne); 
		IdmIdentityDto userTwo = getHelper().createIdentity();
		IdmIdentityContractDto userTwoContract = getHelper().getPrimeContract(userTwo);
		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createIdentityRole(userOneContract, roleOne);
		getHelper().createIdentityRole(userTwoContract, roleOne);
		
		List<IdmIdentityDto> foundIdentities = extrasUtils.getUsersByRoleName(roleOne.getCode());
		Assert.assertEquals(2, foundIdentities.size());
		Assert.assertTrue(foundIdentities.contains(userOne));
		Assert.assertTrue(foundIdentities.contains(userTwo));
		
		List<IdmIdentityDto> foundIdentitiesTwo = extrasUtils.getUsersByRoleName(ROLE_TWO_CODE);
		Assert.assertEquals(0, foundIdentitiesTwo.size());
	}
	
	@Test
	public void isExternistTest() {
		IdmIdentityDto userOne = getHelper().createIdentity();
		IdmIdentityContractDto userOneContract = getHelper().getPrimeContract(userOne); 
		IdmIdentityDto userTwo = getHelper().createIdentity();
		IdmIdentityContractDto userTwoContract = getHelper().getPrimeContract(userTwo);
		
		userOneContract.setExterne(true);
		identityContractService.saveInternal(userOneContract);
		userTwoContract.setExterne(false);
		identityContractService.saveInternal(userTwoContract);
		
		boolean isExternistOne = extrasUtils.isExternist(userOne.getUsername());
		boolean isExternistTwo = extrasUtils.isExternist(userTwo.getUsername());
		
		Assert.assertTrue(isExternistOne);
		Assert.assertFalse(isExternistTwo);
	}
	
	/**
	 * Create form attribute for definition. If definition isn't defined create attribute for default form definition
	 *
	 * @param definition
	 * @return
	 */
	private IdmFormAttributeDto createFormAttribute(IdmFormDefinitionDto definition, String code) {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setFormDefinition(definition.getId());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setName(code);
		attribute.setCode(code);
		return formAttributeService.save(attribute);
	}
	
	/**
	 * Create form attribute for definition. If definition isn't defined create attribute for default form definition
	 *
	 * @param definition
	 * @return
	 */
	private IdmFormAttributeDto createFormAttributeMultivalued(IdmFormDefinitionDto definition, String code) {
		IdmFormAttributeDto attribute = new IdmFormAttributeDto();
		attribute.setFormDefinition(definition.getId());
		attribute.setPersistentType(PersistentType.SHORTTEXT);
		attribute.setName(code);
		attribute.setCode(code);
		attribute.setMultiple(true);

		return formAttributeService.save(attribute);
	}
	
	/**
	 * Prepare form value for given attribute
	 *
	 * @param attribute
	 * @param shorttextvalue
	 * @return
	 */
	private IdmFormValueDto prepareValue(IdmFormAttributeDto attribute, String shorttextvalue) {
		IdmFormValueDto value = new IdmFormValueDto(attribute);
		value.setValue(shorttextvalue);
		return value;
	}
	
	/**
	 * Create normal form definition for identity
	 * @return
	 */
	private IdmFormDefinitionDto createFormDefinition(String classCanonicalName) {
		IdmFormDefinitionDto definition = new IdmFormDefinitionDto();
		definition.setName(getHelper().createName());
		definition.setCode(getHelper().createName());
		definition.setType(classCanonicalName);
		return formDefinitionService.save(definition);
	}
}
