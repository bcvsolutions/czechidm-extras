package eu.bcvsolutions.idm.extras.report;

import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.dto.IdmContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityContractDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.api.dto.IdmRoleDto;

import java.io.Serializable;
import java.util.Date;

/**
 * Created by peter on 24.7.19.
 */
public class RoleAssignmentReportDto implements Serializable {

    //private IdmAuditDto auditDto;
    private IdmIdentityDto relatedIdentity;
    private IdmIdentityContractDto relatedContract;
    private IdmRoleDto relatedRole;
    private Date revisionDate;
    private String operation;
    private String applicant;

    public RoleAssignmentReportDto(IdmIdentityDto relatedIdentity, IdmIdentityContractDto relatedContract,
                                   IdmRoleDto relatedRole, Date revisionDate, String operation, String applicant) {
        //this.auditDto = auditDto;
        this.relatedIdentity = relatedIdentity;
        this.relatedContract = relatedContract;
        this.relatedRole = relatedRole;
        this.revisionDate = revisionDate;
        this.operation = operation;
        this.applicant = applicant;
    }

    public RoleAssignmentReportDto() {
    }

   /* public void setAuditDto(IdmAuditDto auditDto) {
        this.auditDto = auditDto;
    }*/

    public String getApplicant() {
        return applicant;
    }

    public void setApplicant(String applicant) {
        this.applicant = applicant;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public Date getRevisionDate() {
        return revisionDate;
    }

    public void setRevisionDate(Date revisionDate) {
        this.revisionDate = revisionDate;
    }

    public void setRelatedIdentity(IdmIdentityDto relatedIdentity) {
        this.relatedIdentity = relatedIdentity;
    }

    public void setRelatedContract(IdmIdentityContractDto relatedContract) {
        this.relatedContract = relatedContract;
    }

    public void setRelatedRole(IdmRoleDto relatedRole) {
        this.relatedRole = relatedRole;
    }

    /*public IdmAuditDto getAuditDto() {
        return auditDto;
    }*/

    public IdmIdentityDto getRelatedIdentity() {
        return relatedIdentity;
    }

    public IdmIdentityContractDto getRelatedContract() {
        return relatedContract;
    }

    public IdmRoleDto getRelatedRole() {
        return relatedRole;
    }
}
