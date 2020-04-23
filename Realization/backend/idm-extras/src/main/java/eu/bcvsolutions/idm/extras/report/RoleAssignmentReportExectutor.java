package eu.bcvsolutions.idm.extras.report;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Description;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Lists;

import eu.bcvsolutions.idm.acc.dto.SysRoleSystemDto;
import eu.bcvsolutions.idm.acc.dto.filter.SysRoleSystemFilter;
import eu.bcvsolutions.idm.acc.service.api.SysRoleSystemService;
import eu.bcvsolutions.idm.acc.service.api.SysSystemService;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditEntityDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmAuditFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmAuditService;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityContractService;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.api.service.IdmRoleService;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentityRole_;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.api.exception.ReportGenerateException;
import eu.bcvsolutions.idm.rpt.api.executor.AbstractReportExecutor;

/**
 * Example report with identities
 * - filter for enabled / disabled identities
 * - uses json stream to save a lot of identities (+creates temporary file)
 *
 * @author Radek Tomiška
 */
@Enabled(ExtrasModuleDescriptor.MODULE_ID)
@Component("extrasRoleAssignmentReportExectutor")
@Description("Identities - role assignments")
public class RoleAssignmentReportExectutor extends AbstractReportExecutor {

	public static final String REPORT_NAME = "extras-role-assignments-report"; // report ~ executor name
	//

	@Autowired
	private IdmAuditService idmAuditService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private IdmRoleService roleService;
	@Autowired
	private IdmIdentityContractService contractService;
	@Autowired
	private SysRoleSystemService roleSystemService;
	@Autowired
	private SysSystemService sysSystemService;

	/**
	 * Report ~ executor name
	 */
	@Override
	public String getName() {
		return REPORT_NAME;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		return Lists.newArrayList();
	}

	@Override
	protected IdmAttachmentDto generateData(RptReportDto report) {
		File temp = null;
		FileOutputStream outputStream = null;
		try {
			// prepare temp file for json stream
			temp = getAttachmentManager().createTempFile();
			outputStream = new FileOutputStream(temp);
			// write into json stream
			JsonGenerator jGenerator = getMapper().getFactory().createGenerator(outputStream, JsonEncoding.UTF8);
			try {
				// json will be array of identities
				jGenerator.writeStartArray();
				// initialize filter by given form - transform to multi value map
				// => form attribute defined above will be automaticaly mapped to identity filter
				IdmAuditFilter filter = new IdmAuditFilter();
				filter.setType(IdmIdentityRole.class.getName());
				filter.setWithVersion(true);
				// report extends long running task - show progress by count and counter lrt attributes
				counter = 0L;
				// find a first page of identities
				Pageable pageable = new PageRequest(0, 100, new Sort(Direction.ASC, IdmIdentity_.username.getName()));
				do {
					Page<IdmAuditDto> auditDtos = idmAuditService.find(filter, null);
					if (count == null) {
						// report extends long running task - show progress by count and counter lrt attributes
						count = auditDtos.getTotalElements();
					}
					boolean canContinue = true;
					for (Iterator<IdmAuditDto> i = auditDtos.iterator(); i.hasNext() && canContinue; ) {
						// write single identity into json
						RoleAssignmentReportDto reportDto = getReportDto((IdmAuditEntityDto) i.next());
						getMapper().writeValue(jGenerator, reportDto);
						//
						// supports cancel report generating (report extends long running task)
						++counter;
						canContinue = updateState();
					}
					// iterate while next page of auditDtos is available
					pageable = auditDtos.hasNext() && canContinue ? auditDtos.nextPageable() : null;
				} while (pageable != null);
				//
				// close array of identities
				jGenerator.writeEndArray();
			} finally {
				// close json stream
				jGenerator.close();
			}
			// save create temp file with array of identities in json as attachment
			return createAttachment(report, new FileInputStream(temp));
		} catch (IOException ex) {
			throw new ReportGenerateException(report.getName(), ex);
		} finally {
			IOUtils.closeQuietly(outputStream); // just for sure - jGenerator should close stream itself
			FileUtils.deleteQuietly(temp);
		}
	}


	private RoleAssignmentReportDto getReportDto(IdmAuditEntityDto next) {
		Map<String, Object> embedded = next.getEntity();
		IdmIdentityContractDto contractDto = getContract(embedded.get(IdmIdentityRole_.identityContract.getName()));
		IdmIdentityDto identityDto;
		if (contractDto == null) {
			identityDto = null;
		} else {
			identityDto = getIdentity(contractDto.getIdentity());
		}
		IdmRoleDto roleDto = getRole(embedded.get(IdmIdentityRole_.role.getName()));
		String system = getSystem(roleDto);


		return new RoleAssignmentReportDto(identityDto, contractDto, roleDto, next.getRevisionDate(),
				next.getModification(), next.getModifier(), system);
	}

	private String getSystem(IdmRoleDto roleDto) {
		if (roleDto == null) {
			return "Role have been deleted - no system info!";
		}
		SysRoleSystemFilter filter = new SysRoleSystemFilter();
		filter.setRoleId(roleDto.getId());
		List<SysRoleSystemDto> systems = roleSystemService.find(filter, null, null).getContent();
		return systems.stream()
				.map(s -> s.getSystem())
				.map(s -> sysSystemService.get(s))
				.map(s -> s.getCode())
				.reduce((s1, s2) -> s1 + "," + s2)
				.orElse("");
	}

	private IdmRoleDto getRole(Object o) {
		return roleService.get((UUID) o, null);
	}

	private IdmIdentityDto getIdentity(UUID identity) {
		return identityService.get(identity, null);
	}

	private IdmIdentityContractDto getContract(Object contractId) {
		return contractService.get((UUID) contractId);
	}

}