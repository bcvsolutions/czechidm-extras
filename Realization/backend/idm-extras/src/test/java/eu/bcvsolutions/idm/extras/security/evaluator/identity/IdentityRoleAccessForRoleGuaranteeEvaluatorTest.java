package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import eu.bcvsolutions.idm.core.api.dto.*;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityRoleService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Roman Kucera
 */
@Transactional
public class IdentityRoleAccessForRoleGuaranteeEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private IdmIdentityRoleService identityRoleService;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;

	@Test
	public void testIdentityRoleAccess() {
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeValidContract = contractService.getPrimeValidContract(identity.getId());

		// prepare user with roles
		IdmIdentityDto identityWithRoles = getHelper().createIdentity();
		IdmIdentityContractDto primeValidContractWithRoles = contractService.getPrimeValidContract(identityWithRoles.getId());
		IdmRoleDto role = getHelper().createRole();
		IdmRoleDto roleOne = getHelper().createRole();
		IdmRoleDto roleTwo = getHelper().createRole();
		getHelper().assignRoles(primeValidContractWithRoles, false, role, roleOne, roleTwo);

		// Try to get identity roles without permissions
		try {
			getHelper().login(identity);
			List<IdmIdentityRoleDto> roles = identityRoleService.find(null, IdmBasePermission.READ).getContent();
			assertTrue(roles.isEmpty());
		} finally {
			getHelper().logout();
		}

		// create role with evaluator permissions
		IdmRoleDto roleWithPermission = getHelper().createRole();
		getHelper().createAuthorizationPolicy(roleWithPermission.getId(),
				CoreGroupPermission.IDENTITYROLE,
				IdmIdentityRole.class,
				IdentityRoleAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().assignRoles(primeValidContract, false, roleWithPermission);

		// create other role and set our user as guarantee
		IdmRoleDto roleWithGuarantee = getHelper().createRole();
		IdmRoleGuaranteeDto roleGuarantee = new IdmRoleGuaranteeDto();
		roleGuarantee.setRole(roleWithGuarantee.getId());
		roleGuarantee.setGuarantee(identity.getId());
		roleGuaranteeService.save(roleGuarantee);

		// Try to get identity roles with permissions
		try {
			getHelper().login(identity);
			List<IdmIdentityRoleDto> roles = identityRoleService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(1, roles.size());
		} finally {
			getHelper().logout();
		}
	}
}