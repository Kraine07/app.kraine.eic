package app.kraine.eic.reportManagement;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import app.kraine.eic.config.Csv;
import app.kraine.eic.equipmentManagement.EquipmentRow;
 
/**
 * UC-42: the CSV of a displayed report. Every export has the same six columns, whichever report it comes
 * from, so files from different reports can be combined. A byte order mark leads the file so that Excel reads
 * the UTF-8 text (names with accents) correctly.
 */
public final class EquipmentReportCsv {
 
    static final String HEADER = "Serial number,Manufacturer,Model,Manufactured,Property,Location";
    private static final String BYTE_ORDER_MARK = "\uFEFF";
 
    private EquipmentReportCsv() {
    }
 
    public static void write(Writer writer, List<EquipmentRow> rows) throws IOException {
        writer.write(BYTE_ORDER_MARK);
        writer.write(HEADER);
        writer.write("\r\n");
        for (EquipmentRow row : rows) {
            writer.write(String.join(",",
                    Csv.cell(row.serialNumber()),
                    Csv.cell(row.manufacturerName()),
                    Csv.cell(row.modelDescription()),
                    Csv.cell(row.manufacturedDate() == null ? null : row.manufacturedDate().toString()),
                    Csv.cell(row.propertyName()),
                    Csv.cell(row.locationName())));
            writer.write("\r\n");
        }
        writer.flush();
    }
}