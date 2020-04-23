package eu.bcvsolutions.idm.extras.security.evaluator.role;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleCatalogueDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmRole;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Tests for {@link RoleByRoleCatalogueEvaluator}
 *
 * @author Ondrej Kopr
 *
 */
public class RoleByRoleCatalogueEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmRoleService roleService;

	@Test
	public void testOneCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, catalogue);

		checkReadWithUser(administrator, roleOne, 1, true);
	}

	@Test
	public void testTwoDifferentCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto secondCatalogue = getHelper().createRoleCatalogue();
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, catalogue);
		getHelper().createRoleCatalogueRole(roleOne, secondCatalogue);

		checkReadWithUser(administrator, roleOne, 1, true);
	}

	@Test
	public void testRoleWithoutCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto secondCatalogue = getHelper().createRoleCatalogue();
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, secondCatalogue);

		checkReadWithUser(administrator, roleOne, 0, false);
	}

	@Test
	public void testSubSubCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto subCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleCatalogueDto subSubCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), subCatalogue.getId());
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, subSubCatalogue);

		checkReadWithUser(administrator, roleOne, 1, true);
	}

	@Test
	public void testParentCatalogueAndTwiceInSub() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto subCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleCatalogueDto subSecondCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, subCatalogue);
		getHelper().createRoleCatalogueRole(roleOne, subSecondCatalogue);

		checkReadWithUser(administrator, roleOne, 1, true);
	}

	@Test
	public void testMoreRoleInMoreCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto subCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleCatalogueDto subSecondCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		IdmRoleDto roleThree = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, catalogue);
		getHelper().createRoleCatalogueRole(roleTwo, subSecondCatalogue);
		getHelper().createRoleCatalogueRole(roleThree, subCatalogue);

		checkReadWithUser(administrator, roleOne, 3, true);
		checkReadWithUser(administrator, roleTwo, 3, true);
		checkReadWithUser(administrator, roleThree, 3, true);
	}

	@Test
	public void testMoreRolesInDuplicitCatalogue() {
		IdmRoleCatalogueDto catalogue = getHelper().createRoleCatalogue();
		IdmRoleCatalogueDto subCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleCatalogueDto subSecondCatalogue = getHelper().createRoleCatalogue(getHelper().createName(), catalogue.getId());
		IdmRoleDto role = prepareRoleWithEvaluator(catalogue);
		IdmIdentityDto administrator = prepareUserWithRole(role);

		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		IdmRoleDto roleThree = getHelper().createRole();
		getHelper().createRoleCatalogueRole(roleOne, catalogue);
		getHelper().createRoleCatalogueRole(roleTwo, subSecondCatalogue);
		getHelper().createRoleCatalogueRole(roleThree, subCatalogue);
		
		getHelper().createRoleCatalogueRole(roleOne, subCatalogue);
		getHelper().createRoleCatalogueRole(roleTwo, catalogue);
		getHelper().createRoleCatalogueRole(roleThree, subSecondCatalogue);
		
		getHelper().createRoleCatalogueRole(roleOne, subSecondCatalogue);
		getHelper().createRoleCatalogueRole(roleTwo, subCatalogue);
		getHelper().createRoleCatalogueRole(roleThree, catalogue);

		checkReadWithUser(administrator, roleOne, 3, true);
		checkReadWithUser(administrator, roleTwo, 3, true);
		checkReadWithUser(administrator, roleThree, 3, true);
	}

	/**
	 * Process read for all roles (size given in count parameter) and check if in result is given role or not (parameter contains)
	 *
	 * @param identity
	 * @param role
	 * @param count
	 * @param contains
	 */
	private void checkReadWithUser(IdmIdentityDto identity, IdmRoleDto role, int count, boolean contains) {
		try {
			getHelper().login(identity);
			List<IdmRoleDto> roles = roleService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(count, roles.size());
			isRoleInList(role, roles, contains);
		} finally {
			getHelper().logout();
		}
	}

	/**
	 * Check if role contains (or not) defined role in list
	 * @param role
	 * @param list
	 * @param contains
	 */
	private void isRoleInList(IdmRoleDto role, List<IdmRoleDto> list, boolean contains) {
		IdmRoleDto orElse = list.stream().filter(l -> l.getId().equals(role.getId())).findAny().orElse(null);
		if (contains) {
			assertNotNull(orElse);
		} else {
			assertNull(orElse);
		}
	}
	/**
	 * Prepare user with given role
	 *
	 * @param role
	 * @return
	 */
	private IdmIdentityDto prepareUserWithRole(IdmRoleDto role) {
		IdmIdentityDto identity = getHelper().createIdentity(); // There must be create with password
		getHelper().assignRoles(getHelper().getPrimeContract(identity), role);
		return identity;
	}

	/**
	 * Prepare role with evaluator
	 *
	 * @param catalogue
	 * @return
	 */
	private IdmRoleDto prepareRoleWithEvaluator(IdmRoleCatalogueDto catalogue) {
		IdmRoleDto role = getHelper().createRole();
		ConfigurationMap properties = new ConfigurationMap();
		properties.put(RoleByRoleCatalogueEvaluator.PARAMETER_ROLE_CATALOGUE, catalogue.getId());
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITY,
				IdmRole.class,
				RoleByRoleCatalogueEvaluator.class,
				properties,
				IdmBasePermission.READ);
		return role;
	}
}
