package eu.bcvsolutions.idm.extras.domain;

import org.springframework.http.HttpStatus;

import eu.bcvsolutions.idm.core.api.domain.ResultCode;

/**
 * Enum class for formatting response messages (mainly errors). 
 * Every enum contains a string message and corresponding https HttpStatus code.
 * 
 * Used http codes:
 * - 2xx - success
 * - 4xx - client errors (validations, conflicts ...)
 * - 5xx - server errors
 * 
 * @author peter.sourek@bcvsolutions.eu
 */
public enum ExtrasResultCode implements ResultCode {
	
	EXTRAS_CLIENT_ERROR(HttpStatus.BAD_REQUEST, "Example generated error [%s]"),

	// Import CSV
	IMPORT_CANT_READ_FILE_PATH(HttpStatus.BAD_REQUEST, "Can read the file in path [%s]."),
	IMPORT_WRONG_LINE_LENGTH(HttpStatus.BAD_REQUEST, "On line [%s] was found some error."),
	CONNECTOR_INSTANCE_NOT_FOUND(HttpStatus.BAD_REQUEST, "Connector instance for system %s not found!"),
	CONNECTOR_OBJECT_CLASS_NOT_FOUND(HttpStatus.BAD_REQUEST, "Connector object class for system %s not found!"),
	SYSTEM_NAME_NOT_FOUND(HttpStatus.BAD_REQUEST, "System with name [%s] was not found."),
	SYSTEM_SCHEMA_ATTRIBUTES_NOT_FOUND(HttpStatus.BAD_REQUEST, "No attributes for object class name %s on system %s were found"),
	SET_EAV_TREES_NODE_IS_NULL(HttpStatus.BAD_REQUEST, "nodeCode is null!"),
	SET_EAV_TREES_SIZE_OF_NODES_IS_ZERO(HttpStatus.BAD_REQUEST, "Size of nodes found should not be 0. Check if code is right."),
	SET_EAV_TREES_SIZE_OF_NODES_IS_NOT_ONE(HttpStatus.BAD_REQUEST, "Size of nodes found should not be more than 1."),
	SET_EAV_TREES_NOT_SAVED(HttpStatus.BAD_REQUEST, "Values not saved for some reason!"),
	SET_EAV_TREES_LRT_FAILED(HttpStatus.BAD_REQUEST, "Result of LRT is null!"),
	SET_EAV_TREES_CONTRACT_IS_NULL(HttpStatus.BAD_REQUEST, "Contracts should not be null!"),
	SET_EAV_TREES_MULTIPLE_CONTRACTS_FOUND(HttpStatus.BAD_REQUEST, "Multiple contracts were found!");

	
	private final HttpStatus status;
	private final String message;
	
	private ExtrasResultCode(HttpStatus status, String message) {
		this.message = message;
		this.status = status;
	}
	
	public String getCode() {
		return this.name();
	}
	
	public String getModule() {
		return "extras";
	}
	
	public HttpStatus getStatus() {
		return status;
	}
	
	public String getMessage() {
		return message;
	}	
}
