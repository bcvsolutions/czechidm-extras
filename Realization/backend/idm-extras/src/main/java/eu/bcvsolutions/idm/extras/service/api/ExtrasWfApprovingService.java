package eu.bcvsolutions.idm.extras.service.api;

import eu.bcvsolutions.idm.core.api.dto.IdmConceptRoleRequestDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface ExtrasWfApprovingService {
	
	public static final String APPROVE_BY_GUARANTEE_A = "ApproveByContractGuaraneesA";
	public static final String APPROVE_BY_GUARANTEE_B = "ApproveByContractGuaraneesB";
	
	String getAdminAsCandidate();
	
	String getRoleGuaranteesForApproval(IdmConceptRoleRequestDto conceptRoleRequest, String guarenteeType);
	
	String getContractManagersForApproval (IdmConceptRoleRequestDto roleConcept);
	
	String getApproversFromScript (IdmConceptRoleRequestDto roleConcept);
}
