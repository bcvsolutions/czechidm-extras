package eu.bcvsolutions.idm.extras.report.provisioning;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.hssf.util.HSSFColor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import eu.bcvsolutions.idm.acc.dto.SysSystemAttributeMappingDto;
import eu.bcvsolutions.idm.rpt.api.dto.RptReportDto;
import eu.bcvsolutions.idm.rpt.api.exception.ReportRenderException;
import eu.bcvsolutions.idm.rpt.api.renderer.AbstractXlsxRenderer;
import eu.bcvsolutions.idm.rpt.api.renderer.RendererRegistrar;

/**
 * Renderer for {@link CompareValueWithSystemReportExecutor}
 *
 * @author Ondrej Kopr
 *
 */

@Component("compareValueWithSystemReportXlsxRenderer")
@Description(AbstractXlsxRenderer.RENDERER_EXTENSION)
public class CompareValueWithSystemReportXlsxRenderer extends AbstractXlsxRenderer implements RendererRegistrar {

	private static String NEW_LINE = System.lineSeparator();
	private static String IDM_VALUE = "IdM:";
	private static String SYSTEM_VALUE = "System:";

	private static String ENTITY_DOESNT_EXIST = "not exists";
	private static String ENTITY_CHANGED = "CHANGED";
	private static String ENTITY_NOT_CHANGED = "ok";
	private static String ENTITY_FAILED = "FAILED";
	private static String ENTITY_LEFT = "left";
	private static String ENTITY_IN_PROTECTION = "protection";
	
	@Override
	public InputStream render(RptReportDto report) {
		try {
			JsonParser jParser = getMapper().getFactory().createParser(getReportData(report));
			XSSFWorkbook workbook = new XSSFWorkbook();
			XSSFSheet sheet = workbook.createSheet("Report");

			/*
			 * Create font styles
			 */
			XSSFFont redBoldFont = workbook.createFont();
			redBoldFont.setBold(true);
			redBoldFont.setColor(HSSFColor.RED.index);
			
			XSSFFont redFont = workbook.createFont();
			redFont.setBold(true);
			redFont.setColor(HSSFColor.RED.index);

			XSSFFont greenFont = workbook.createFont();
			greenFont.setColor(HSSFColor.GREEN.index);
			
			XSSFFont greenBoldFont = workbook.createFont();
			greenBoldFont.setBold(true);
			greenBoldFont.setColor(HSSFColor.GREEN.index);

			XSSFFont blueLightFont = workbook.createFont();
			blueLightFont.setColor(HSSFColor.LIGHT_BLUE.index);
			
			XSSFFont italicFont = workbook.createFont();
			italicFont.setItalic(true);

			XSSFFont headerFont = workbook.createFont();
			headerFont.setBold(true);
			headerFont.setFontHeightInPoints((short)15);
			
			XSSFFont boldFont = workbook.createFont();
			boldFont.setBold(true);

			// Start with forst row
			// first row contains header
			Row row = sheet.createRow(0);
			Cell cell = row.createCell(0);
			XSSFRichTextString headerColumn = new XSSFRichTextString();
			headerColumn.append("Status", headerFont);
			cell.setCellValue(headerColumn);

			cell = row.createCell(1);
			headerColumn = new XSSFRichTextString();
			headerColumn.append("Username (uid)", headerFont);
			cell.setCellValue(headerColumn);

			/*
			 * Cell styles for changed column
			 */
			XSSFCellStyle changedTrueStyle = workbook.createCellStyle();
			changedTrueStyle.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
			changedTrueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle notChangedStyle = workbook.createCellStyle();
			notChangedStyle.setFillForegroundColor(IndexedColors.SKY_BLUE.getIndex());
			notChangedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle createdStyle = workbook.createCellStyle();
			createdStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
			createdStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			
			XSSFCellStyle failedStyle = workbook.createCellStyle();
			failedStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
			failedStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle identityLeftStyle = workbook.createCellStyle();
			identityLeftStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			identityLeftStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			XSSFCellStyle accountProtectionStyle = workbook.createCellStyle();
			accountProtectionStyle.setFillForegroundColor(IndexedColors.GREY_40_PERCENT.getIndex());
			accountProtectionStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			// start with parse json to dto
			if (jParser.nextToken() == JsonToken.START_ARRAY) {
				
				jParser.nextToken();
				int rowNum = 1;

				CompareValueDataInfoDto data = getMapper().readValue(jParser, CompareValueDataInfoDto.class);

				// first two header column contains username and status
				// this generate dynamically headers by attributes
				int cellIndex = 2;
				for (SysSystemAttributeMappingDto attr : data.getAttributes()) {
					XSSFRichTextString resultValue = new XSSFRichTextString();
					cell = row.createCell(cellIndex);
					resultValue.append(attr.getName(), headerFont);
					cell.setCellValue(resultValue);
					cellIndex++;
				}
				
				row = sheet.createRow(rowNum++);
				row.setHeightInPoints((short)15);
				int maximumColumns = cellIndex;

				Set<String> added = new HashSet<>();
				Set<String> current = new HashSet<>();

				// iterate over rows
				for (CompareValueRowDto rowData : data.getRows()) {
					row = sheet.createRow(rowNum++);
					
					// first cell is status
					cell = row.createCell(0);
					cell.setCellValue("");
					
					// second cell information about entity
					cell = row.createCell(1);
					XSSFRichTextString identifierCell = new XSSFRichTextString(rowData.getKey());
					identifierCell.applyFont(boldFont);
					cell.setCellValue(identifierCell);
					
					if (rowData.isFailed()) {
						cell = row.createCell(2);
						
						String failedMessageValue = rowData.getFailedMessage();
						if (StringUtils.isNotBlank(failedMessageValue)) {
							XSSFRichTextString failedMessage = new XSSFRichTextString(rowData.getFailedMessage());
							failedMessage.applyFont(redFont);
							cell.setCellValue(failedMessage);
						}

						cell = row.getCell(0);
						cell.setCellStyle(failedStyle);
						cell.setCellValue(ENTITY_FAILED);
						continue;
					}
					
					// entity may not exist on system, just log information and continue
					if (!rowData.isExistEntityOnSystem()) {
						cell = row.createCell(0);
						XSSFRichTextString richTextString = new XSSFRichTextString(ENTITY_DOESNT_EXIST);
						cell.setCellValue(richTextString);
						cell.setCellStyle(createdStyle);
						continue;
					}
					
					// iterate over cell data
					cellIndex = 2;
					boolean changed = false;
					for (CompareValueCellDto cellData : rowData.getCells()) {
						
						// cell contains multivalued values, print as multivalued
						XSSFRichTextString resultValue = new XSSFRichTextString();
						if (cellData.isMultivalued()) {

							 // TODO: Think about immutable list
							List<Object> idmValues = new ArrayList<Object>();
							List<Object> systemValues = new ArrayList<Object>();
							
							if (cellData.getIdmValue() != null && cellData.getIdmValue() instanceof Collection) {
								idmValues = (List<Object>) cellData.getIdmValue();
							}

							if (cellData.getSystemValue() != null && cellData.getSystemValue() instanceof Collection) {
								systemValues = (List<Object>) cellData.getSystemValue();
							} else if (cellData.getSystemValue() != null && !(cellData.getSystemValue() instanceof Collection)) { // from system isn't returned collection, but some item
								systemValues.add(cellData.getSystemValue());
							}

							// classic merge
							added.clear();
							current.clear();

							for (Object idmValue : idmValues) {
								if (systemValues.contains(idmValue)) {
									current.add(Objects.toString(idmValue));
									systemValues.remove(idmValue);
								} else {
									added.add(Objects.toString(idmValue));
								}
							}
							
							// removed
							for (Object systemValue : systemValues) {
								resultValue.append(Objects.toString(systemValue), redFont);
								resultValue.append(NEW_LINE);
							}

							// newly added
							for (String add : added) {
								resultValue.append(add, greenFont);
								resultValue.append(NEW_LINE);
							}

							// current state
							for (String cur : current) {
								resultValue.append(cur, blueLightFont);
								resultValue.append(NEW_LINE);
							}

							// something changed
							if (!added.isEmpty() || !systemValues.isEmpty()) {
								changed = true;
							}
							
						} else {
							// single value attribute
							Object idmValue = cellData.getIdmValue();
							Object systemValue = cellData.getSystemValue();
							
							if (StringUtils.isBlank(String.valueOf(idmValue)) && StringUtils.isBlank(String.valueOf(systemValue))) {
								resultValue = new XSSFRichTextString();
								resultValue.append(Objects.toString(idmValue), blueLightFont);

							} else if (ObjectUtils.notEqual(idmValue, systemValue)) {
								resultValue = new XSSFRichTextString();
								
								resultValue.append(IDM_VALUE, greenBoldFont);
								resultValue.append(Objects.toString(idmValue), greenFont);
								
								resultValue.append(NEW_LINE);
								resultValue.append(SYSTEM_VALUE, redBoldFont);
								resultValue.append(Objects.toString(systemValue), redFont);
								
								changed = true;
							} else {
								resultValue = new XSSFRichTextString();
								resultValue.append(Objects.toString(idmValue), blueLightFont);
							}
							
						}

						// create cell with richt text value and increase cell index
						cell = row.createCell(cellIndex);
						cell.setCellValue(resultValue);
						cellIndex++;
					}

					cell = row.getCell(0);

					if (rowData.isIdentityLeft()) { // identity is left, dont set changed status
						cell.setCellStyle(identityLeftStyle);
						cell.setCellValue(ENTITY_LEFT);
					} else if (rowData.isAccountInProtection()) { // account is in protection, dont set changed status
						cell.setCellStyle(accountProtectionStyle);
						cell.setCellValue(ENTITY_IN_PROTECTION);
					} else if (BooleanUtils.isTrue(changed)) { // check changed flag and get first column and set status
						cell.setCellStyle(changedTrueStyle);
						cell.setCellValue(ENTITY_CHANGED);
					} else if (BooleanUtils.isFalse(changed)) { // OK state
						cell.setCellStyle(notChangedStyle);
						cell.setCellValue(ENTITY_NOT_CHANGED);
					}
				}

				// set auto size column
				// TODO: this is very rich operation to executed, is this really needed?
				for (int index = 0; index < maximumColumns+1; index++) {
					sheet.autoSizeColumn(index);
				}
			}
			return getInputStream(workbook);
		} catch (IOException e) {
			throw new ReportRenderException(report.getName(), e);
		} 
	}

	@Override
	public String[] register(String reportName) {
		if (CompareValueWithSystemReportExecutor.REPORT_NAME.equals(reportName)) {
			return new String[] { getName() };
		}
		return new String[] {};
	}
}