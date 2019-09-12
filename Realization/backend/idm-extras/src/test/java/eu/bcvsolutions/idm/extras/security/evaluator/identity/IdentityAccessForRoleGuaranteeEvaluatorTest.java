package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import static org.junit.Assert.assertEquals;

import java.util.List;

import javax.transaction.Transactional;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleGuaranteeDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;

/**
 * Test that if user is guarantee at least for one role he can see all users
 *
 * @author Roman Kucera
 */
@Transactional
public class IdentityAccessForRoleGuaranteeEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;

	@Test
	public void testIdentityAccess() {
		// prepare users
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeValidContract = contractService.getPrimeValidContract(identity.getId());
		getHelper().createIdentity();
		getHelper().createIdentity();

		// without permissions I will see only myself
		try {
			getHelper().login(identity);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(1, users.size());
		} finally {
			getHelper().logout();
		}

		// create role with evaluator permissions
		IdmRoleDto role = getHelper().createRole();
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.IDENTITY,
				IdmIdentity.class,
				IdentityAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().assignRoles(primeValidContract, false, role);

		// create other role and set our user as guarantee
		IdmRoleDto roleWithGuarantee = getHelper().createRole();
		IdmRoleGuaranteeDto roleGuarantee = new IdmRoleGuaranteeDto();
		roleGuarantee.setRole(roleWithGuarantee.getId());
		roleGuarantee.setGuarantee(identity.getId());
		roleGuaranteeService.save(roleGuarantee);

		// now wth permissions I will see all users
		try {
			long numberOfUsers = identityService.count(null);
			getHelper().login(identity);
			List<IdmIdentityDto> users = identityService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(numberOfUsers, users.size());
		} finally {
			getHelper().logout();
		}
	}
}