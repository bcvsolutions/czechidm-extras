package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.domain.ConfigurationMap;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmTreeNodeDto;
import eu.bcvsolutions.idm.core.api.exception.ForbiddenEntityException;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.GuardedString;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Tests for {@link IdentityByTreeNodeEvaluator}
 *
 * @author Ondrej Kopr
 *
 */
public class IdentityByTreeNodeEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmIdentityContractService identityContractService;
	@Autowired
	private IdmIdentityService identityService;

	@Test
	public void testOneContract() {
		IdmTreeNodeDto position = getHelper().createTreeNode();
		IdmRoleDto role = prepareRoleWithEvaluator(position);
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(position);

		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);
		} finally {
			getHelper().logout();
		}
	}

	@Test
	public void testThreeContract() {
		IdmTreeNodeDto position = getHelper().createTreeNode();
		IdmTreeNodeDto positionTwo = getHelper().createTreeNode();
		IdmRoleDto role = prepareRoleWithEvaluator(position);
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(position);

		// Two different contract
		getHelper().createIdentityContact(identity, positionTwo);
		getHelper().createIdentityContact(identity, null);

		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);
		} finally {
			getHelper().logout();
		}
	}

	@Test
	public void testSubpositionOnContract() {
		IdmTreeNodeDto parent = getHelper().createTreeNode();
		IdmTreeNodeDto parentTwo = getHelper().createTreeNode(getHelper().createName(), parent);
		IdmTreeNodeDto position = getHelper().createTreeNode(getHelper().createName(), parentTwo);

		IdmRoleDto role = prepareRoleWithEvaluator(parent); // Evaluator is on parent
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(position); // Position is sub child


		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);
		} finally {
			getHelper().logout();
		}
	}

	@Test
	public void testSubpositionContract() {
		IdmTreeNodeDto parent = getHelper().createTreeNode();
		IdmTreeNodeDto parentTwo = getHelper().createTreeNode(getHelper().createName(), parent);
		IdmTreeNodeDto position = getHelper().createTreeNode(getHelper().createName(), parentTwo);

		IdmRoleDto role = prepareRoleWithEvaluator(parentTwo); // Evaluator is on parentTwo
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(parent); // Position is sub child


		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		changePosition(identity, position);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);
		} finally {
			getHelper().logout();
		}
	}

	@Test
	public void testSubpositionMoreContract() {
		IdmTreeNodeDto parent = getHelper().createTreeNode();
		IdmTreeNodeDto parentTwo = getHelper().createTreeNode(getHelper().createName(), parent);
		IdmTreeNodeDto position = getHelper().createTreeNode(getHelper().createName(), parentTwo);

		IdmRoleDto role = prepareRoleWithEvaluator(parentTwo); // Evaluator is on parentTwo
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(parent); // Position is sub child


		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		IdmIdentityContractDto newContract = getHelper().createIdentityContact(identity, position);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);
		} finally {
			getHelper().logout();
		}

		identityContractService.delete(newContract);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}
	}

	@Test(expected = ForbiddenEntityException.class)
	public void testSaveContract() {
		IdmTreeNodeDto position = getHelper().createTreeNode();
		IdmRoleDto role = prepareRoleWithEvaluator(position);
		IdmIdentityDto identity = prepareIdentityOnWorkPosition(position);

		IdmIdentityDto administrator = getHelper().createIdentity();
		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			// only myself
			assertEquals(1, users.size());
			checkIdentityInList(identity, users, false);
		} finally {
			getHelper().logout();
		}

		getHelper().assignRoles(getHelper().getPrimeContract(administrator), role);

		try {
			getHelper().login(administrator);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(2, users.size());
			checkIdentityInList(identity, users, true);

			IdmIdentityDto get = identityService.get(identity.getId(), IdmBasePermission.READ);
			get.setDescription(getHelper().createName());
			assertNotNull(get);
			get = identityService.save(get, IdmBasePermission.UPDATE);
			fail("Save identity isn't possible");
		} finally {
			getHelper().logout();
		}
	}

	/**
	 * Check if exists (or not) given identity in list
	 *
	 * @param identity
	 * @param list
	 * @param exists
	 */
	private void checkIdentityInList(IdmIdentityDto identity, List<IdmIdentityDto> list, boolean exists) {
		IdmIdentityDto founded = list.stream().filter(i -> i.getId().equals(identity.getId())).findAny().orElse(null);
		if (exists) {
			assertNotNull(founded);
		} else {
			assertNull(founded);
		}
	}

	/**
	 * Prepare role with evaluator {@link IdentityByTreeNodeEvaluator}
	 *
	 * @param position
	 * @return
	 */
	private IdmRoleDto prepareRoleWithEvaluator(IdmTreeNodeDto position) {
		IdmRoleDto role = getHelper().createRole();
		ConfigurationMap properties = new ConfigurationMap();
		properties.put(IdentityByTreeNodeEvaluator.PARAMETER_TREE_NODE, position.getId());
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdentityByTreeNodeEvaluator.class,
				properties,
				IdmBasePermission.READ);
		return role;
	}

	/**
	 * Prepare identity with given work position on main contract
	 *
	 * @param position
	 * @return
	 */
	private IdmIdentityDto prepareIdentityOnWorkPosition(IdmTreeNodeDto position) {
		IdmIdentityDto identity = getHelper().createIdentity((GuardedString)null);
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identity);
		assertNotNull(contract); // Something strange with test settings?

		contract.setWorkPosition(position.getId());
		contract = identityContractService.save(contract);
		return identity;
	}

	/**
	 * Change positon for main contract
	 *
	 * @param identity
	 * @param newPosition
	 */
	private void changePosition(IdmIdentityDto identity, IdmTreeNodeDto newPosition) {
		IdmIdentityContractDto contract = getHelper().getPrimeContract(identity);
		assertNotNull(contract); // Something strange with test settings?
		contract.setWorkPosition(newPosition.getId());
		contract = identityContractService.save(contract);
	}
}
