package eu.bcvsolutions.idm.extras.security.evaluator.identity;

import eu.bcvsolutions.idm.core.api.domain.RoleRequestedByType;
import eu.bcvsolutions.idm.core.api.dto.*;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleGuaranteeService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleRequestService;
import eu.bcvsolutions.idm.core.model.domain.CoreGroupPermission;
import eu.bcvsolutions.idm.core.model.entity.IdmRoleRequest;
import eu.bcvsolutions.idm.core.security.api.domain.IdmBasePermission;
import eu.bcvsolutions.idm.test.api.AbstractIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import javax.transaction.Transactional;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Roman Kucera
 */
@Transactional
public class RoleRequestAccessForRoleGuaranteeEvaluatorTest extends AbstractIntegrationTest {

	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private IdmRoleGuaranteeService roleGuaranteeService;
	@Autowired
	private IdmRoleRequestService roleRequestService;

	@Test
	public void testRequestRoleAccess() {
		// prepare users
		IdmIdentityDto identity = getHelper().createIdentity();
		IdmIdentityContractDto primeValidContract = contractService.getPrimeValidContract(identity.getId());

		IdmIdentityDto creator = getHelper().createIdentity();

		//create request
		IdmRoleRequestDto requestDto = new IdmRoleRequestDto();
		requestDto.setApplicant(creator.getId());
		requestDto.setRequestedByType(RoleRequestedByType.MANUALLY);
		requestDto.setExecuteImmediately(true);
		requestDto = roleRequestService.save(requestDto);

		// without permissions I will see only myself
		try {
			getHelper().login(identity);
			List<IdmRoleRequestDto> requests = roleRequestService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(0, requests.size());
		} finally {
			getHelper().logout();
		}

		// create role with evaluator permissions
		IdmRoleDto role = getHelper().createRole();
		getHelper().createAuthorizationPolicy(role.getId(),
				CoreGroupPermission.ROLEREQUEST,
				IdmRoleRequest.class,
				RoleRequestAccessForRoleGuaranteeEvaluator.class,
				IdmBasePermission.READ);

		getHelper().assignRoles(primeValidContract, false, role);

		// create other role and set our user as guarantee
		IdmRoleDto roleWithGuarantee = getHelper().createRole();
		IdmRoleGuaranteeDto roleGuarantee = new IdmRoleGuaranteeDto();
		roleGuarantee.setRole(roleWithGuarantee.getId());
		roleGuarantee.setGuarantee(identity.getId());
		roleGuaranteeService.save(roleGuarantee);

		// now with permissions I will see all requests
		try {
			long count = roleRequestService.count(null);
			getHelper().login(identity);
			List<IdmRoleRequestDto> requests = roleRequestService.find(null, IdmBasePermission.READ).getContent();
			assertEquals(count, requests.size());
		} finally {
			getHelper().logout();
		}
	}
}