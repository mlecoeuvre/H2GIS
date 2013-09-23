/*
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2007-2012 IRSTV (FR CNRS 2488)
 *
 * h2patial is free software: you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * h2spatial is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * h2spatial. If not, see <http://www.gnu.org/licenses/>.
 *
 * For more information, please consult: <http://www.orbisgis.org/>
 * or contact directly:
 * info_at_ orbisgis.org
 */

package org.h2gis.drivers.shp;

import org.h2gis.drivers.dbf.internal.DbaseFileException;
import org.h2gis.drivers.dbf.internal.DbaseFileHeader;
import org.h2gis.drivers.shp.internal.SHPDriver;
import org.h2gis.drivers.shp.internal.ShapeType;
import org.h2gis.drivers.shp.internal.ShapefileHeader;
import org.h2gis.h2spatialapi.DriverFunction;
import org.orbisgis.sputilities.GeometryTypeCodes;
import org.orbisgis.sputilities.JDBCUtilities;
import org.orbisgis.sputilities.SFSUtilities;
import org.orbisgis.sputilities.TableLocation;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;

/**
 * Read/Write Shape files
 * @author Nicolas Fortin
 */
public class SHPDriverFunction implements DriverFunction {
    private static final int BATCH_MAX_SIZE = 100;

    @Override
    public void exportTable(Connection connection, String tableReference, File fileName) throws SQLException, IOException {
        int recordCount = getRowCount(connection, tableReference);
        //
        // Read Geometry Index and type
        List<String> spatialFieldNames = SFSUtilities.getGeometryFields(connection, TableLocation.parse(tableReference));
        if(spatialFieldNames.isEmpty()) {
            throw new SQLException(String.format("The table %s does not contain a geometry field", tableReference));
        }
        int geometryType = SFSUtilities.getGeometryType(connection, TableLocation.parse(tableReference), spatialFieldNames.get(0));
        ShapeType shapeType = getShapeTypeFromSFSGeometryTypeCode(geometryType, spatialFieldNames.get(0));
        // Read table content
        Statement st = connection.createStatement();
        try {
            ResultSet rs = st.executeQuery(String.format("select * from `%s`", tableReference));
            try {
                ResultSetMetaData resultSetMetaData = rs.getMetaData();
                DbaseFileHeader header = dBaseHeaderFromMetaData(resultSetMetaData);
                header.setNumRecords(recordCount);
                SHPDriver shpDriver = new SHPDriver();
                shpDriver.setGeometryFieldIndex(JDBCUtilities.getFieldIndex(resultSetMetaData, spatialFieldNames.get(0)));
                shpDriver.initDriver(fileName,shapeType , header);
                Object[] row = new Object[header.getNumFields()];
                while (rs.next()) {
                    for(int columnId = 0; columnId < row.length; columnId++) {
                        row[columnId] = rs.getObject(columnId + 1);
                    }
                    shpDriver.insertRow(row);
                }
                shpDriver.close();
            } finally {
                rs.close();
            }
        } finally {
            st.close();
        }
    }

    @Override
    public IMPORT_DRIVER_TYPE getImportDriverType() {
        return IMPORT_DRIVER_TYPE.COPY;
    }

    @Override
    public String[] getImportFormats() {
        return new String[] {"shp"};
    }

    @Override
    public String[] getExportFormats() {
        return new String[] {"shp"};
    }

    @Override
    public void importFile(Connection connection, String tableReference, File fileName) throws SQLException, IOException {
        SHPDriver shpDriver = new SHPDriver();
        shpDriver.initDriverFromFile(fileName);
        try {
            DbaseFileHeader dbfHeader = shpDriver.getDbaseFileHeader();
            ShapefileHeader shpHeader = shpDriver.getShapeFileHeader();
            // Build CREATE TABLE sql request
            Statement st = connection.createStatement();
            st.execute(String.format("CREATE TABLE `%s` (the_geom %s, %s)", tableReference, getSFSGeometryType(shpHeader), getSQLColumnTypes(dbfHeader)));
            st.close();
            try {
                PreparedStatement preparedStatement = connection.prepareStatement(
                        String.format("INSERT INTO `%s` VALUES ( " + getQuestionMark(dbfHeader.getNumFields() + 1) + ")", tableReference));
                try {
                    long batchSize = 0;
                    for (int rowId = 0; rowId < shpDriver.getRowCount(); rowId++) {
                        Object[] values = shpDriver.getRow(rowId);
                        for (int columnId = 0; columnId < values.length; columnId++) {
                            preparedStatement.setObject(columnId + 1, values[columnId]);
                        }
                        preparedStatement.addBatch();
                        batchSize++;
                        if (batchSize >= BATCH_MAX_SIZE) {
                            preparedStatement.executeBatch();
                            preparedStatement.clearBatch();
                            batchSize = 0;
                        }
                    }
                    if(batchSize > 0) {
                        preparedStatement.executeBatch();
                    }
                } finally {
                    preparedStatement.close();
                }
                //TODO create spatial index on the_geom ?
            } catch (Exception ex) {
                connection.createStatement().execute("DROP TABLE IF EXISTS " + tableReference);
                throw new SQLException(ex.getLocalizedMessage(), ex);
            }
        } finally {
            shpDriver.close();
        }
    }



    private static DbaseFileHeader dBaseHeaderFromMetaData(ResultSetMetaData metaData) throws SQLException {
        DbaseFileHeader dbaseFileHeader = new DbaseFileHeader();
        for(int fieldId= 1; fieldId <= metaData.getColumnCount(); fieldId++) {
            final String fieldTypeName = metaData.getColumnTypeName(fieldId);
            // TODO postgis check field type
            if(!fieldTypeName.equalsIgnoreCase("geometry")) {
                char fieldType;
                switch (metaData.getColumnType(fieldId)) {
                    case Types.BOOLEAN:
                        fieldType = 'l';
                        break;
                    // (C)character (String)
                    case Types.VARCHAR:
                        fieldType = 'c';
                        break;
                    case Types.DATE:
                        fieldType = 'd';
                        break;
                    case Types.INTEGER:
                    case Types.TINYINT:
                        fieldType = 'n';
                        break;
                    case Types.FLOAT:
                    case Types.DOUBLE:
                        fieldType = 'f';
                        break;
                    default:
                        throw new SQLException("Field type not supported by DBF : " + fieldTypeName);
                }
                try {
                    dbaseFileHeader.addColumn(metaData.getColumnName(fieldId),fieldType, metaData.getPrecision(fieldId),metaData.getColumnDisplaySize(fieldId));
                } catch (DbaseFileException ex) {
                    throw new SQLException(ex.getLocalizedMessage(), ex);
                }
            }
        }
        return dbaseFileHeader;
    }

    private static int getRowCount(Connection connection, String tableReference) throws SQLException {
        Statement st = connection.createStatement();
        int rowCount = 0;
        try {
            ResultSet rs = st.executeQuery(String.format("select count(*) rowcount from `%s`", tableReference));
            try {
                if(rs.next()) {
                    rowCount = rs.getInt(0);
                }
            } finally {
                rs.close();
            }
        }finally {
            st.close();
        }
        return rowCount;
    }

    private static ShapeType getShapeTypeFromSFSGeometryTypeCode(int sfsGeometryTypeCode, String fieldName) throws SQLException {
        ShapeType shapeType;
        switch (sfsGeometryTypeCode) {
            case GeometryTypeCodes.MULTILINESTRING:
            case GeometryTypeCodes.LINESTRING:
                shapeType = ShapeType.ARC;
                break;
            case GeometryTypeCodes.MULTILINESTRINGM:
            case GeometryTypeCodes.LINESTRINGM:
                shapeType = ShapeType.ARCM;
                break;
            case GeometryTypeCodes.MULTILINESTRINGZ:
            case GeometryTypeCodes.LINESTRINGZ:
                shapeType = ShapeType.ARCZ;
                break;
            case GeometryTypeCodes.POINT:
            case GeometryTypeCodes.MULTIPOINT:
                shapeType = ShapeType.MULTIPOINT;
                break;
            case GeometryTypeCodes.POINTM:
            case GeometryTypeCodes.MULTIPOINTM:
                shapeType = ShapeType.MULTIPOINTM;
                break;
            case GeometryTypeCodes.POINTZ:
            case GeometryTypeCodes.MULTIPOINTZ:
                shapeType = ShapeType.MULTIPOINTZ;
                break;
            case GeometryTypeCodes.POLYGON:
            case GeometryTypeCodes.MULTIPOLYGON:
                shapeType = ShapeType.POLYGON;
                break;
            case GeometryTypeCodes.POLYGONM:
            case GeometryTypeCodes.MULTIPOLYGONM:
                shapeType = ShapeType.POLYGONM;
                break;
            case GeometryTypeCodes.POLYGONZ:
            case GeometryTypeCodes.MULTIPOLYGONZ:
                shapeType = ShapeType.POLYGONZ;
                break;
            default:
                throw new SQLException(String.format("Geometry type of the field %s incompatible with ShapeFile, please use (Multi)Point, (Multi)Polygon or (Multi)LineString constraint", fieldName));
        }
        return shapeType;
    }

    private static String getQuestionMark(int count) {
        StringBuilder qMark = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if(i > 0) {
                qMark.append(", ");
            }
            qMark.append("?");
        }
        return qMark.toString();
    }

    /**
     * Return SQL Columns declaration
     * @param header DBAse file header
     * @return Array of columns ex: ["id INTEGER", "len DOUBLE"]
     */
    private String getSQLColumnTypes(DbaseFileHeader header) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        for(int idColumn = 0; idColumn < header.getNumFields(); idColumn++) {
            if(idColumn > 0) {
                stringBuilder.append(", ");
            }
            stringBuilder.append(header.getFieldName(idColumn));
            stringBuilder.append(" ");
            switch (header.getFieldType(idColumn)) {
                // (L)logical (T,t,F,f,Y,y,N,n)
                case 'l':
                case 'L':
                    stringBuilder.append("BOOLEAN");
                    break;
                // (C)character (String)
                case 'c':
                case 'C':
                    stringBuilder.append("CHAR(");
                    // Append size
                    int length = header.getFieldLength(idColumn);
                    stringBuilder.append(String.valueOf(length));
                    stringBuilder.append(")");
                    break;
                // (D)date (Date)
                case 'd':
                case 'D':
                    stringBuilder.append("DATE");
                    break;
                // (F)floating (Double)
                case 'n':
                case 'N':
                    if ((header.getFieldDecimalCount(idColumn) == 0)) {
                        if ((header.getFieldLength(idColumn) >= 0)
                                && (header.getFieldLength(idColumn) < 10)) {
                            stringBuilder.append("INT4");
                        } else {
                            stringBuilder.append("INT8");
                        }
                    } else {
                        stringBuilder.append("FLOAT8");
                    }
                    break;
                case 'f':
                case 'F': // floating point number
                case 'o':
                case 'O': // floating point number
                    stringBuilder.append("FLOAT8");
                    break;
                default:
                    throw new IOException("Unknown DBF field type " + header.getFieldType(idColumn));
            }
        }
        return stringBuilder.toString();
    }

    private static String getSFSGeometryType(ShapefileHeader header) {
        switch(header.getShapeType().id) {
            case 1:
            case 11:
            case 21:
                return "MULTIPOINT";
            case 3:
            case 13:
            case 23:
                return "MULTILINESTRING";
            case 5:
            case 15:
            case 25:
                return "MULTIPOLYGON";
            case 8:
            case 18:
            case 28:
                return "MULTIPOINT";
            default:
                return "GEOMETRY";
        }
    }
}
