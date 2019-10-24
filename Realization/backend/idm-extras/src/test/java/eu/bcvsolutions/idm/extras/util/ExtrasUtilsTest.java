package eu.bcvsolutions.idm.extras.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Tests for {@link ExtrasUtils}
 *
 * @author Ondrej Kopr
 *
 */
public class ExtrasUtilsTest extends AbstractIntegrationTest {

	@Autowired
	private ExtrasUtils extrasUtils;

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
}
