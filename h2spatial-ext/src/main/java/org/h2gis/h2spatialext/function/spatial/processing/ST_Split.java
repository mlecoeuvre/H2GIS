/**
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier
 * SIG" team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
 *
 * Copyright (C) 2007-2014 IRSTV (FR CNRS 2488)
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
 * or contact directly: info_at_ orbisgis.org
 */
package org.h2gis.h2spatialext.function.spatial.processing;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.distance.GeometryLocation;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import com.vividsolutions.jts.operation.union.UnaryUnionOp;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import org.h2gis.drivers.utility.CoordinatesUtils;
import org.h2gis.h2spatialapi.DeterministicScalarFunction;
import org.h2gis.h2spatialext.function.spatial.convert.ST_ToMultiSegments;
import org.h2gis.h2spatialext.function.spatial.edit.EditUtilities;

/**
 * This function split a line by a line a line by a point a polygon by a line
 *
 * @author Erwan Bocher
 */
public class ST_Split extends DeterministicScalarFunction {

    private static final GeometryFactory FACTORY = new GeometryFactory();
    public static final double PRECISION = 10E-6;

    public ST_Split() {
        addProperty(PROP_REMARKS, "Returns a collection of geometries resulting by splitting a geometry.\n"
                + "Supported operations are : split a line by a line a line by a point a polygon by a line");
    }

    @Override
    public String getJavaStaticMethod() {
        return "split";
    }

    /**
     * Split a geometry a according a geometry b. Supported operations are :
     * split a line by a line a line by a point a polygon by a line.
     *
     * A default tolerance of 10E-6 is used to snap the cutter point.
     *
     * @param geomA
     * @param geomB
     * @return
     * @throws SQLException
     */
    public static Geometry split(Geometry geomA, Geometry geomB) throws SQLException {
        if (geomA instanceof Polygon) {
            return splitPolygonWithLine((Polygon) geomA, (LineString) geomB);
        } else if (geomA instanceof LineString) {
            if (geomB instanceof LineString) {
                return splitLineStringWithLine((LineString) geomA, (LineString) geomB);
            } else if (geomB instanceof Point) {
                return splitLineWithPoint((LineString) geomA, (Point) geomB, PRECISION);
            }
        } else if (geomA instanceof MultiLineString) {
            if (geomB instanceof LineString) {
                return splitMultiLineStringWithLine((MultiLineString) geomA, (LineString) geomB);
            } else if (geomB instanceof Point) {
                return splitMultiLineStringWithPoint((MultiLineString) geomA, (Point) geomB, PRECISION);
            }
        }
        throw new SQLException("Split a " + geomA.getGeometryType() + " by a " + geomB.getGeometryType() + " is not supported.");
    }

    /**
     * Split a geometry a according a geometry b using a snapping tolerance.
     *
     * This function support only the operations :
     *
     * - split a line or a multiline with a point.
     *
     * @param geomA
     * @param geomB
     * @return
     */
    public static Geometry split(Geometry geomA, Geometry geomB, double tolerance) throws SQLException {
        if (geomA instanceof Polygon) {
            throw new SQLException("Split a Polygon by a line is not supported using a tolerance. \n"
                    + "Please used ST_Split(geom1, geom2)");
        } else if (geomA instanceof LineString) {
            if (geomB instanceof LineString) {
                throw new SQLException("Split a line by a line is not supported using a tolerance. \n"
                        + "Please used ST_Split(geom1, geom2)");
            } else if (geomB instanceof Point) {
                return splitLineWithPoint((LineString) geomA, (Point) geomB, tolerance);
            }
        } else if (geomA instanceof MultiLineString) {
            if (geomB instanceof LineString) {
                throw new SQLException("Split a multiline by a line is not supported using a tolerance. \n"
                        + "Please used ST_Split(geom1, geom2)");
            } else if (geomB instanceof Point) {
                return splitMultiLineStringWithPoint((MultiLineString) geomA, (Point) geomB, tolerance);
            }
        }
        throw new SQLException("Split a " + geomA.getGeometryType() + " by a " + geomB.getGeometryType() + " is not supported.");
    }

    /**
     * Split a linestring with a point The point must be on the linestring
     *
     * @param line
     * @param pointToSplit
     * @return
     */
    private static MultiLineString splitLineWithPoint(LineString line, Point pointToSplit, double tolerance) {
        return FACTORY.createMultiLineString(splitLineStringWithPoint(line, pointToSplit, tolerance));
    }

    /**
     * Splits a LineString using a Point, with a distance tolerance.
     *
     * @param line
     * @param pointToSplit
     * @param tolerance
     * @return
     */
    private static LineString[] splitLineStringWithPoint(LineString line, Point pointToSplit, double tolerance) {
        Coordinate[] coords = line.getCoordinates();
        Coordinate firstCoord = coords[0];
        Coordinate lastCoord = coords[coords.length - 1];
        Coordinate coordToSplit = pointToSplit.getCoordinate();
        if ((coordToSplit.distance(firstCoord) <= PRECISION) || (coordToSplit.distance(lastCoord) <= PRECISION)) {
            return new LineString[]{line};
        } else {
            ArrayList<Coordinate> firstLine = new ArrayList<Coordinate>();
            firstLine.add(coords[0]);
            ArrayList<Coordinate> secondLine = new ArrayList<Coordinate>();
            GeometryLocation geometryLocation = EditUtilities.getVertexToSnap(line, pointToSplit, tolerance);
            if (geometryLocation != null) {
                int segmentIndex = geometryLocation.getSegmentIndex();
                Coordinate coord = geometryLocation.getCoordinate();
                int index = -1;
                for (int i = 1; i < coords.length; i++) {
                    index = i - 1;
                    if (index < segmentIndex) {
                        firstLine.add(coords[i]);
                    } else if (index == segmentIndex) {
                        coord.z = CoordinatesUtils.interpolate(coords[i - 1], coords[i], coord);
                        firstLine.add(coord);
                        secondLine.add(coord);
                        if (!coord.equals2D(coords[i])) {
                            secondLine.add(coords[i]);
                        }
                    } else {
                        secondLine.add(coords[i]);
                    }
                }
                LineString lineString1 = FACTORY.createLineString(firstLine.toArray(new Coordinate[firstLine.size()]));
                LineString lineString2 = FACTORY.createLineString(secondLine.toArray(new Coordinate[secondLine.size()]));
                return new LineString[]{lineString1, lineString2};
            }
        }
        return null;
    }

    /**
     * Splits a MultilineString using a point.
     *
     * @param multiLineString
     * @param pointToSplit
     * @param tolerance
     * @return
     */
    private static MultiLineString splitMultiLineStringWithPoint(MultiLineString multiLineString, Point pointToSplit, double tolerance) {
        ArrayList<LineString> linestrings = new ArrayList<LineString>();
        boolean notChanged = true;
        int nb = multiLineString.getNumGeometries();
        for (int i = 0; i < nb; i++) {
            LineString subGeom = (LineString) multiLineString.getGeometryN(i);
            LineString[] result = splitLineStringWithPoint(subGeom, pointToSplit, tolerance);
            if (result != null) {
                Collections.addAll(linestrings, result);
                notChanged = false;
            } else {
                linestrings.add(subGeom);
            }
        }
        if (!notChanged) {
            return FACTORY.createMultiLineString(linestrings.toArray(new LineString[linestrings.size()]));
        }
        return null;
    }

    /**
     * Splits a Polygon with a LineString.
     *
     * @param polygon
     * @param lineString
     * @return
     */
    private static Collection<Polygon> splitPolygonizer(Polygon polygon, LineString lineString) throws SQLException {
        LinkedList<LineString> result = new LinkedList<LineString>();
        ST_ToMultiSegments.createSegments(polygon.getExteriorRing(), result);
        result.add(lineString);
        int holes = polygon.getNumInteriorRing();
        for (int i = 0; i < holes; i++) {
            ST_ToMultiSegments.createSegments(polygon.getInteriorRingN(i), result);
        }
        // Perform union of all extracted LineStrings (the edge-noding process)  
        UnaryUnionOp uOp = new UnaryUnionOp(result);
        Geometry union = uOp.union();

        // Create polygons from unioned LineStrings  
        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add(union);
        Collection<Polygon> polygons = polygonizer.getPolygons();

        if (polygons.size() > 1) {
            return polygons;
        }
        return null;
    }

    /**
     * Splits a Polygon using a LineString.
     *
     * @param polygon
     * @param lineString
     * @return
     */
    private static Geometry splitPolygonWithLine(Polygon polygon, LineString lineString) throws SQLException {
        Collection<Polygon> pols = polygonWithLineSplitter(polygon, lineString);
        if (pols != null) {
            return FACTORY.buildGeometry(polygonWithLineSplitter(polygon, lineString));
        }
        return null;
    }

    /**
     * Splits a Polygon using a LineString.
     *
     * @param polygon
     * @param lineString
     * @return
     */
    private static Collection<Polygon> polygonWithLineSplitter(Polygon polygon, LineString lineString) throws SQLException {
        Collection<Polygon> polygons = splitPolygonizer(polygon, lineString);
        if (polygons != null && polygons.size() > 1) {
            List<Polygon> pols = new ArrayList<Polygon>();
            for (Polygon pol : polygons) {
                if (polygon.contains(pol.getInteriorPoint())) {
                    pols.add(pol);
                }
            }
            return pols;
        }
        return null;
    }

    /**
     * Splits a MultiPolygon using a LineString.
     *
     * @param multiPolygon
     * @param lineString
     * @return
     */
    private static Geometry splitMultiPolygonWithLine(MultiPolygon multiPolygon, LineString lineString) throws SQLException {
        ArrayList<Polygon> allPolygons = new ArrayList<Polygon>();
        for (int i = 0; i < multiPolygon.getNumGeometries(); i++) {
            Collection<Polygon> polygons = splitPolygonizer((Polygon) multiPolygon.getGeometryN(i), lineString);
            if (polygons != null) {
                allPolygons.addAll(polygons);
            }
        }
        if (!allPolygons.isEmpty()) {
            return FACTORY.buildGeometry(allPolygons);
        }
        return null;
    }

    /**
     * Splits the specified lineString with another lineString.
     *
     * @param lineString
     * @param lineString
     *
     */
    private static Geometry splitLineStringWithLine(LineString input, LineString cut) {
        return input.difference(cut);
    }

    /**
     * Splits the specified MultiLineString with another lineString.
     *
     * @param MultiLineString
     * @param lineString
     *
     */
    private static MultiLineString splitMultiLineStringWithLine(MultiLineString input, LineString cut) {
        ArrayList<Geometry> geometries = new ArrayList<Geometry>();
        Geometry lines = input.difference(cut);
        for (int i = 0; i < lines.getNumGeometries(); i++) {
            geometries.add(lines.getGeometryN(i));
        }
        return FACTORY.createMultiLineString(geometries.toArray(new LineString[geometries.size()]));
    }
}
