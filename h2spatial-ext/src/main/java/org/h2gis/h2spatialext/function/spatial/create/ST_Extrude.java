/*
 * Copyright (C) 2013 IRSTV CNRS-FR-2488
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.h2gis.h2spatialext.function.spatial.create;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Polygon;
import org.h2gis.h2spatialapi.DeterministicScalarFunction;
import org.h2gis.h2spatialext.function.spatial.volume.GeometryExtrude;

import java.sql.SQLException;

/**
 * ST_Extrude takes a LINESTRING or POLYGON as input and extends it to a 3D
 * representation, returning  a geometry collection containing floor, ceiling
 * and wall geometries. In the case of a LINESTRING, the floor and ceiling are
 * LINESTRINGs; for a POLYGON, they are POLYGONs.
 *
 * @author Erwan Bocher
 */
public class ST_Extrude extends DeterministicScalarFunction {

    public ST_Extrude() {
        addProperty(PROP_REMARKS, "ST_Extrude takes a LINESTRING or POLYGON as input\n"
                + " and extends it to a 3D representation, returning a geometry collection\n"
                + " containing floor, ceiling and wall geometries");
    }

    @Override
    public String getJavaStaticMethod() {
        return "extrudeGeometry";
    }

    /**
     * Extrudes a POLYGON or a LINESTRING into a GEOMETRYCOLLECTION containing
     * the floor (input geometry), walls and ceiling.
     *
     * @param geometry Input geometry
     * @param height   Desired height
     * @return Collection (floor, walls, ceiling)
     * @throws SQLException
     */
    public static GeometryCollection extrudeGeometry(Geometry geometry, double height) throws SQLException {
        if (geometry instanceof Polygon) {
            return GeometryExtrude.extrudePolygonAsGeometry((Polygon) geometry, height);
        } else if (geometry instanceof LineString) {
            return GeometryExtrude.extrudeLineStringAsGeometry((LineString) geometry, height);
        }
        throw new SQLException("Only LINESTRING and POLYGON inputs are accepted.");
    }

    /**
     *
     * Extrudes a POLYGON or a LINESTRING into a GEOMETRYCOLLECTION containing
     * the floor (input geometry), walls and ceiling.  A flag of 1 extracts
     * walls and a flag of 2 extracts the roof.
     *
     * @param geometry Input geometry
     * @param height   Desired height
     * @param flag     1 (walls), 2 (roof)
     * @return Walls or roof
     * @throws SQLException
     */
    public static Geometry extrudeGeometry(Geometry geometry, double height, int flag) throws SQLException {
        if (geometry instanceof Polygon) {
            if (flag == 1) {
                return GeometryExtrude.extractWalls((Polygon)geometry, height);
            } else if (flag == 2) {
                return GeometryExtrude.extractRoof((Polygon)geometry, height);
            } else {
                throw new SQLException("Incorrect flag value. Please set 1 to extract walls "
                        + "or 2 to extract roof. ");
            }
        } else if (geometry instanceof LineString) {
            if (flag == 1) {
                return GeometryExtrude.extractWalls((LineString)geometry, height);
            } else if (flag == 2) {
                return GeometryExtrude.extractRoof((LineString)geometry, height);
            } else {
                throw new SQLException("Incorrect flag value. Please set 1 to extract walls "
                        + "or 2 to extract roof. ");
            }
        }
        throw new SQLException("Only LINESTRING and POLYGON inputs are accepted.");
    }
}
