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

package org.h2gis.h2spatial.internal.function.spatial.properties;

import com.vividsolutions.jts.geom.Geometry;
import org.h2gis.h2spatialapi.DeterministicScalarFunction;

/**
 * Get the number of geometries inside a geometry collection
 * @author Nicolas Fortin
 */
public class ST_NumGeometries extends DeterministicScalarFunction {

    /**
     * Default constructor
     */
    public ST_NumGeometries() {
        addProperty(PROP_REMARKS, "Get the number of geometries inside a geometry collection.");
    }

    @Override
    public String getJavaStaticMethod() {
        return "getNumGeometries";
    }

    /**
     * @param geometry Geometry instance or null
     * @return Number of geometries or null if Geometry is null.
     */
    public static Integer getNumGeometries(Geometry geometry) {
        if(geometry==null) {
            return null;
        }
        return geometry.getNumGeometries();
    }
}
