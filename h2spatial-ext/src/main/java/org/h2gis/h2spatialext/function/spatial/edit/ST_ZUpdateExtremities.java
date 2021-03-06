/**
 * h2spatial is a library that brings spatial support to the H2 Java database.
 *
 * h2spatial is distributed under GPL 3 license. It is produced by the "Atelier SIG"
 * team of the IRSTV Institute <http://www.irstv.fr/> CNRS FR 2488.
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
 * or contact directly:
 * info_at_ orbisgis.org
 */
package org.h2gis.h2spatialext.function.spatial.edit;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.CoordinateSequence;
import com.vividsolutions.jts.geom.CoordinateSequenceFilter;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import org.h2gis.h2spatialapi.DeterministicScalarFunction;

/**
 *
 * @author Erwan Bocher
 */
public class ST_ZUpdateExtremities extends DeterministicScalarFunction {

    private static final GeometryFactory FACTORY = new GeometryFactory();

    public ST_ZUpdateExtremities() {
        addProperty(PROP_REMARKS, "Replace the start and end z values of a linestring or multilinestring.\n"
                + "By default the other z values are interpolated according the length of the line.\n"
                + "Set false if you want to update only the start and end z values.");
    }

    @Override
    public String getJavaStaticMethod() {
        return "updateZExtremities";
    }

    public static Geometry updateZExtremities(Geometry geometry, double startZ, double endZ) {
        return updateZExtremities(geometry, startZ, endZ, true);

    }

    /**
     * Update the start and end Z values. If the interpolate is true the
     * vertices are interpolated according the start and end z values.
     *
     * @param geometry
     * @param startZ
     * @param endZ
     * @return
     */
    public static Geometry updateZExtremities(Geometry geometry, double startZ, double endZ, boolean interpolate) {
        if (geometry instanceof LineString) {
            return force3DStartEnd((LineString) geometry, startZ, endZ, interpolate);
        } else if (geometry instanceof MultiLineString) {
            int nbGeom = geometry.getNumGeometries();
            LineString[] lines = new LineString[nbGeom];
            for (int i = 0; i < nbGeom; i++) {
                LineString subGeom = (LineString) geometry.getGeometryN(i);
                lines[i] = (LineString) force3DStartEnd(subGeom, startZ, endZ, interpolate);
            }
            return FACTORY.createMultiLineString(lines);
        } else {
            return null;
        }
    }

    /**
     * Updates all z values by a new value using the specified first and the
     * last coordinates.
     *
     * @param geom
     * @param startZ
     * @param endZ
     * @param interpolate is true the z value of the vertices are interpolate 
     * according the length of the line.
     * @return
     */
    private static Geometry force3DStartEnd(LineString lineString, final double startZ,
            final double endZ, final boolean interpolate) {
        final double bigD = lineString.getLength();
        final double z = endZ - startZ;
        final Coordinate coordEnd = lineString.getCoordinates()[lineString.getCoordinates().length - 1];
        lineString.apply(new CoordinateSequenceFilter() {
            boolean done = false;

            @Override
            public boolean isGeometryChanged() {
                return true;
            }

            @Override
            public boolean isDone() {
                return done;
            }

            @Override
            public void filter(CoordinateSequence seq, int i) {
                double x = seq.getX(i);
                double y = seq.getY(i);
                if (i == 0) {
                    seq.setOrdinate(i, 0, x);
                    seq.setOrdinate(i, 1, y);
                    seq.setOrdinate(i, 2, startZ);
                } else if (i == seq.size() - 1) {
                    seq.setOrdinate(i, 0, x);
                    seq.setOrdinate(i, 1, y);
                    seq.setOrdinate(i, 2, endZ);
                } else {
                    if (interpolate) {
                        double smallD = seq.getCoordinate(i).distance(coordEnd);
                        double factor = smallD / bigD;
                        seq.setOrdinate(i, 0, x);
                        seq.setOrdinate(i, 1, y);
                        seq.setOrdinate(i, 2, startZ + (factor * z));
                    }
                }
                if (i == seq.size()) {
                    done = true;
                }
            }
        });
        return lineString;
    }
}
