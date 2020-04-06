package eu.bcvsolutions.idm.extras.report;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import eu.bcvsolutions.idm.core.api.audit.dto.IdmAuditDto;
import eu.bcvsolutions.idm.core.api.dto.IdmIdentityDto;
import eu.bcvsolutions.idm.core.security.api.domain.Enabled;
import eu.bcvsolutions.idm.extras.ExtrasModuleDescriptor;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.api.exception.ReportRenderException;
import eu.bcvsolutions.idm.rpt.api.renderer.AbstractXlsxRenderer;
import eu.bcvsolutions.idm.rpt.api.renderer.RendererRegistrar;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Description;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;

@Enabled(ExtrasModuleDescriptor.MODULE_ID)
@Component("roleAssignmentReportRenderer")
@Description(AbstractXlsxRenderer.RENDERER_EXTENSION) // will be show as format for download
public class RoleAssignmentReportRenderer
        extends AbstractXlsxRenderer
        implements RendererRegistrar {

    @Override
    public InputStream render(RptReportDto report) {
        try {
            // read json stream
            JsonParser jParser = getMapper().getFactory().createParser(getReportData(report));
            XSSFWorkbook workbook = new XSSFWorkbook();
            XSSFSheet sheet = workbook.createSheet("Report");
            // header
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("Username");
            cell = row.createCell(1);
            cell.setCellValue("First name");
            cell = row.createCell(2);
            cell.setCellValue("Last name");
            cell = row.createCell(3);
            cell.setCellValue("Role");
            cell = row.createCell(4);
            cell.setCellValue("Position");
            cell = row.createCell(5);
            cell.setCellValue("Date");
            cell = row.createCell(6);
            cell.setCellValue("Operation");
            cell = row.createCell(7);
            cell.setCellValue("Applicant");
            cell = row.createCell(8);
            cell.setCellValue("System");
            int rowNum = 1;
            //
            // json is array of identities
            if (jParser.nextToken() == JsonToken.START_ARRAY) {
                // write single identity
                while (jParser.nextToken() == JsonToken.START_OBJECT) {
                    RoleAssignmentReportDto auditDto = getMapper().readValue(jParser, RoleAssignmentReportDto.class);

                    row = sheet.createRow(rowNum++);
                    if(auditDto.getRelatedIdentity()==null){
                        cell = row.createCell(0);
                        cell.setCellValue("User deleted!");

                        cell = row.createCell(1);
                        cell.setCellValue("");

                        cell = row.createCell(2);
                        cell.setCellValue("");
                    } else {
                        cell = row.createCell(0);
                        cell.setCellValue(auditDto.getRelatedIdentity().getUsername());

                        cell = row.createCell(1);
                        cell.setCellValue(auditDto.getRelatedIdentity().getFirstName());

                        cell = row.createCell(2);
                        cell.setCellValue(auditDto.getRelatedIdentity().getLastName());
                    }
                    if(auditDto.getRelatedRole()==null){
                        cell = row.createCell(3);
                        cell.setCellValue("Role deleted!");
                    } else {
                        cell = row.createCell(3);
                        cell.setCellValue(auditDto.getRelatedRole().getCode());
                    }

                    if(auditDto.getRelatedContract()!=null){
                        cell = row.createCell(4);
                        cell.setCellValue(auditDto.getRelatedContract().getPosition());
                    } else {
                        cell = row.createCell(4);
                        cell.setCellValue("Contract deleted!");
                    }

                    cell = row.createCell(5);
                    cell.setCellValue(DateFormat.getDateTimeInstance().format(auditDto.getRevisionDate()));

                    cell = row.createCell(6);
                    cell.setCellValue(auditDto.getOperation());

                    cell = row.createCell(7);
                    cell.setCellValue(auditDto.getApplicant());

                    cell = row.createCell(8);
                    cell.setCellValue(auditDto.getSystem());
                }
            }
            // close json stream
            jParser.close();
            //
            // close and return input stream
            return getInputStream(workbook);
        } catch (IOException ex) {
            throw new ReportRenderException(report.getName(), ex);
        }
    }

    /**
     * Register renderer to example report
     */
    @Override
    public String[] register(String reportName) {
        if (RoleAssignmentReportExectutor.REPORT_NAME.equals(reportName)) {
            return new String[] { getName() };
        }
        return new String[] {};
    }

}