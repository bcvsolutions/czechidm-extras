package eu.bcvsolutions.idm.extras.report.identity;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.google.common.collect.Lists;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.audit.dto.filter.IdmAuditFilter;
import eu.bcvsolutions.idm.core.api.audit.service.IdmAuditService;
import eu.bcvsolutions.idm.core.api.domain.IdentityState;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.filter.IdmIdentityFilter;
import eu.bcvsolutions.idm.core.api.service.IdmIdentityService;
import eu.bcvsolutions.idm.core.eav.api.domain.PersistentType;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormAttributeDto;
import eu.bcvsolutions.idm.core.eav.api.dto.IdmFormInstanceDto;
import eu.bcvsolutions.idm.core.ecm.api.dto.IdmAttachmentDto;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity;
import eu.bcvsolutions.idm.core.model.entity.IdmIdentity_;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.api.exception.ReportGenerateException;
import eu.bcvsolutions.idm.rpt.api.executor.AbstractReportExecutor;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author Roman Kučera
 */
@Component
@Enabled(ExtrasModuleDescriptor.MODULE_ID)
@Description("Get users who start/end contract in specific time range")
public class IdentityStateExecutor extends AbstractReportExecutor {

	public static final String REPORT_NAME = "users-start-end";

	public static final String PARAMETR_DATE_FROM = "date from";
	public static final String PARAMETR_DATE_TO = "date to";
	public static final String PARAMETER_TREE_NODE = "treeNode";

	public static final String EXCLUDED = "vyňat";
	public static final String LEFT = "odchod";
	public static final String VALID = "nástup";

	private UUID treeNode = null;

	@Autowired
	private IdmAuditService auditService;
	@Autowired
	private IdmIdentityService identityService;
	@Autowired
	private ApplicationContext applicationContext;

	@Override
	public String getName() {
		return REPORT_NAME;
	}

	@Override
	public List<IdmFormAttributeDto> getFormAttributes() {
		IdmFormAttributeDto dateFrom = new IdmFormAttributeDto(PARAMETR_DATE_FROM, "Date from", PersistentType.DATE);

		IdmFormAttributeDto dateTo = new IdmFormAttributeDto(PARAMETR_DATE_TO, "Date to", PersistentType.DATE);

		IdmFormAttributeDto treeNode = new IdmFormAttributeDto(PARAMETER_TREE_NODE, "Treenode",
				PersistentType.SHORTTEXT);
		treeNode.setDescription("Organization ID. Filtering users only on this organization unit and recursively down.");

		return Lists.newArrayList(dateFrom, dateTo, treeNode);
	}

	@Override
	protected IdmAttachmentDto generateData(RptReportDto report) {
		ZonedDateTime dateFrom = (ZonedDateTime) getParameterValue(report, PARAMETR_DATE_FROM);
		ZonedDateTime dateTo = (ZonedDateTime) getParameterValue(report, PARAMETR_DATE_TO);
		Serializable treeNodeValue = getParameterValue(report, PARAMETER_TREE_NODE);
		if (treeNodeValue != null) {
			treeNode = UUID.fromString(treeNodeValue.toString());
		}

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
				if (dateFrom != null) {
					filter.setFrom(dateFrom);
				}
				if (dateTo != null) {
					filter.setTill(dateTo);
				}
				filter.setType(IdmIdentity.class.getName());
				List<String> changedAttributesList = new ArrayList<String>();
				changedAttributesList.add(IdmIdentity_.state.getName());
				filter.setChangedAttributesList(changedAttributesList);

				// report extends long running task - show progress by count and counter lrt attributes
				counter = 0L;
				processUsers(jGenerator, filter);

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

	private void processUsers(JsonGenerator jGenerator, IdmAuditFilter filter) throws IOException {
		List<UUID> content;
		if (treeNode != null) {
			IdmIdentityFilter identityFilter = new IdmIdentityFilter();
			identityFilter.setTreeNode(treeNode);
			identityFilter.setRecursively(true);
			content = identityService.findIds(identityFilter, null).getContent();
		} else {
			content = identityService.findIds(null).getContent();
		}

		boolean canContinue;
		for (UUID identityId : content) {
			filter.setOwnerId(identityId.toString());
			List<IdmAuditDto> audits = auditService.find(filter, null).getContent();
			for (IdmAuditDto audit : audits) {
				IdentityStateReportDto reportDto = getIdentityState(audit);
				getMapper().writeValue(jGenerator, reportDto);

				canContinue = updateState();
				if (!canContinue) {
					return;
				}
			}
		}
	}

	/**
	 * Return {@link IdentityStateReportDto} for given {@link IdmAuditDto}
	 *
	 * @param audit
	 * @return
	 */
	public IdentityStateReportDto getIdentityState(IdmAuditDto audit) {
		String pattern = "dd. MM. yyyy HH:mm:ss";
		DateFormat formatter = new SimpleDateFormat(pattern);

		IdmIdentity version = this.getBean().getVersion(audit);
		IdentityState state = version.getState();

		IdentityStateReportDto reportDto = new IdentityStateReportDto();
		reportDto.setDateOfChange(formatter.format(audit.getRevisionDate()));

		IdmIdentityDto identityDto = identityService.get(UUID.fromString(audit.getOwnerId()));
		reportDto.setUsername(identityDto.getUsername());
		reportDto.setPersonalNumber(identityDto.getExternalCode());
		reportDto.setFirstName(identityDto.getFirstName());
		reportDto.setLastName(identityDto.getLastName());

		if (state == IdentityState.VALID) {
			reportDto.setState(VALID);
		} else if (state == IdentityState.DISABLED) {
			reportDto.setState(EXCLUDED);
		} else if (state == IdentityState.LEFT) {
			reportDto.setState(LEFT);
		}

		return reportDto;
	}

	private Serializable getParameterValue(RptReportDto report, String name) {
		IdmFormInstanceDto formInstance = new IdmFormInstanceDto(report, getFormDefinition(), report.getFilter());
		return formInstance.toSinglePersistentValue(name);
	}

	private IdentityStateExecutor getBean() {
		return applicationContext.getBean(this.getClass());
	}

	@Transactional
	public IdmIdentity getVersion(IdmAuditDto auditDto) {
		return auditService.findVersion(IdmIdentity.class, auditDto.getEntityId(),
				Long.valueOf(auditDto.getId().toString()));
	}
}

