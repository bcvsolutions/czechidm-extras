package eu.bcvsolutions.idm.extras.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.UUID;

import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.acc.dto.AccContractAccountDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.AccContractAccountFilter;
import eu.bcvsolutions.idm.acc.service.api.AccContractAccountService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.extras.DefaultAccTestHelper;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import eu.bcvsolutions.idm.test.api.TestHelper;


/**
 * Tests for {@link ExtrasUtils}
 *
 * @author Ondrej Kopr
 *
 */
public class ExtrasUtilsTest extends AbstractIntegrationTest {

	@Autowired
	private ExtrasUtils extrasUtils;

	@Autowired
	private DefaultAccTestHelper accTestHelper;

	@Autowired
	private TestHelper testHelper;

	@Autowired
	private AccContractAccountService contractAccountService;

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
	public void getEntityByAccount() {
		IdmIdentityDto identity = testHelper.createIdentity("TEST_IDENTITY_EXTRAS_GET_ENT_ACC");
		IdmIdentityContractDto primeContract = testHelper.getPrimeContract(identity);
		SysSystemDto system = accTestHelper.createSystem("TABLE_TEST_EXTRAS_ENT_ACC", "TEST_EXTRAS_ENT_ACC");
		//
		AccContractAccountDto contractAccount = accTestHelper.createContractAccount(system, primeContract);
		UUID entityByAccount = extrasUtils.getEntityByAccount(contractAccount.getAccount(), new AccContractAccountFilter(), contractAccountService);
		//
		Assert.assertNotNull(entityByAccount);
		Assert.assertEquals(entityByAccount, primeContract.getId());
	}
}
