package eu.bcvsolutions.idm.extras.report.provisioning;

import java.io.Serializable;

/**
 * One cell for row
 *
 * @author Ondrej Kopr
 *
 */
public class CompareValueCellDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private Object idmValue;
	private Object systemValue;
	private boolean multivalued = false;
	private String attributeName;

	public Object getIdmValue() {
		return idmValue;
	}

	public void setIdmValue(Object idmValue) {
		this.idmValue = idmValue;
	}

	public Object getSystemValue() {
		return systemValue;
	}

	public void setSystemValue(Object systemValue) {
		this.systemValue = systemValue;
	}

	public boolean isMultivalued() {
		return multivalued;
	}

	public void setMultivalued(boolean multivalued) {
		this.multivalued = multivalued;
	}

	public String getAttributeName() {
		return attributeName;
	}

	public void setAttributeName(String attributeName) {
		this.attributeName = attributeName;
	}

}
