package eu.bcvsolutions.idm.extras.report.provisioning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemDto;
import eu.bcvsolutions.idm.acc.dto.SysSystemMappingDto;

/**
 * Transfer date from executor {@link CompareValueWithSystemReportExecutor} to
 * another renderer, or just print as json.
 *
 * @author Ondrej Kopr
 *
 */
public class CompareValueDataInfoDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private SysSystemDto system;
	private SysSystemMappingDto systemMapping;
	private List<SysSystemAttributeMappingDto> attributes;
	private List<CompareValueRowDto> rows; 

	public SysSystemDto getSystem() {
		return system;
	}

	public void setSystem(SysSystemDto system) {
		this.system = system;
	}

	public SysSystemMappingDto getSystemMapping() {
		return systemMapping;
	}

	public void setSystemMapping(SysSystemMappingDto systemMapping) {
		this.systemMapping = systemMapping;
	}

	public List<SysSystemAttributeMappingDto> getAttributes() {
		return attributes;
	}

	public void setAttributes(List<SysSystemAttributeMappingDto> attributes) {
		this.attributes = attributes;
	}

	public List<CompareValueRowDto> getRows() {
		if (rows == null) {
			return new ArrayList<>();
		}
		return rows;
	}

	public void setRows(List<CompareValueRowDto> rows) {
		this.rows = rows;
	}

	public void addRow(CompareValueRowDto row) {
		this.getRows().add(row);
	}
}
