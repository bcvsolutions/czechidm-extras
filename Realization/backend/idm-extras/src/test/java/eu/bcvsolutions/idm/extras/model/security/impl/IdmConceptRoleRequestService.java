package eu.bcvsolutions.idm.extras.model.security.impl;

import eu.bcvsolutions.idm.core.api.service.LookupService;
import eu.bcvsolutions.idm.core.model.repository.IdmAutomaticRoleRepository;
import eu.bcvsolutions.idm.core.model.repository.IdmConceptRoleRequestRepository;
import eu.bcvsolutions.idm.core.workflow.service.WorkflowProcessInstanceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Overridden service for concept role request.
 * Throw error when currently logged user try to save concept with roles for which is not guarantee
 *
 * @author Roman Kucera
 */
@Primary
@Service("extrasConceptRoleRequestService")
public class IdmConceptRoleRequestService extends ExtrasIdmConceptRoleRequestService {

	@Autowired
	public IdmConceptRoleRequestService(IdmConceptRoleRequestRepository repository,
											  WorkflowProcessInstanceService workflowProcessInstanceService, LookupService lookupService,
											  IdmAutomaticRoleRepository automaticRoleRepository) {
		super(repository, workflowProcessInstanceService, lookupService, automaticRoleRepository);
	}

}
