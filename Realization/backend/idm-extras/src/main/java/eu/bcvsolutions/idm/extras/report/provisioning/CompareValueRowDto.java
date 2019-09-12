package eu.bcvsolutions.idm.extras.report.provisioning;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Rows for report
 *
 * @author Ondrej Kopr
 *
 */
public class CompareValueRowDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private String key;
	private List<CompareValueCellDto> cells;
	private boolean existEntityOnSystem = true;
	private boolean failed = false;
	private String failedMessage;
	private boolean identityLeft = false;
	private boolean accountInProtection = false;

	public String getKey() {
		return key;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public List<CompareValueCellDto> getCells() {
		if (cells == null) {
			return new ArrayList<>();
		}
		return cells;
	}

	public void setCells(List<CompareValueCellDto> cells) {
		this.cells = cells;
	}

	public void addCell(CompareValueCellDto cell) {
		this.getCells().add(cell);
	}

	public boolean isExistEntityOnSystem() {
		return existEntityOnSystem;
	}

	public void setExistEntityOnSystem(boolean existEntityOnSystem) {
		this.existEntityOnSystem = existEntityOnSystem;
	}

	public boolean isFailed() {
		return failed;
	}

	public void setFailed(boolean failed) {
		this.failed = failed;
	}

	public String getFailedMessage() {
		return failedMessage;
	}

	public void setFailedMessage(String failedMessage) {
		this.failedMessage = failedMessage;
	}

	public boolean isIdentityLeft() {
		return identityLeft;
	}

	public void setIdentityLeft(boolean identityLeft) {
		this.identityLeft = identityLeft;
	}

	public boolean isAccountInProtection() {
		return accountInProtection;
	}

	public void setAccountInProtection(boolean accountInProtection) {
		this.accountInProtection = accountInProtection;
	}
}
