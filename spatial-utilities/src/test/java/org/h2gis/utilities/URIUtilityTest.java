/*
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
package org.h2gis.utilities;

import org.junit.Test;

import java.net.URI;
import java.util.Map;
import static org.junit.Assert.assertEquals;

/**
 * Unit test of URI utilities
 * @author Nicolas Fortin
 */
public class URIUtilityTest {
    @Test
    public void testGetQueryKeyValuePairs() throws Exception {
        URI uri = URI.create("http://services.orbisgis.org/wms/wms?REQUEST=GetMap&SERVICE=WMS&VERSION=1.3.0" +
                "&LAYERS=cantons_dep44&CRS=EPSG:27572" +
                "&BBOX=259555.01152073737,2218274.7695852537,342561.9239631337,2287024.7695852537&WIDTH=524&HEIGHT=434" +
                "&FORMAT=image/png&STYLES=");
        Map<String,String> query = URIUtility.getQueryKeyValuePairs(uri);
        assertEquals(10,query.size());
        assertEquals("GetMap",query.get("request"));
        assertEquals("WMS",query.get("service"));
        assertEquals("cantons_dep44",query.get("layers"));
        assertEquals("EPSG:27572",query.get("crs"));
        assertEquals("259555.01152073737,2218274.7695852537,342561.9239631337,2287024.7695852537",query.get("bbox"));
        assertEquals("524",query.get("width"));
        assertEquals("434",query.get("height"));
        assertEquals("image/png",query.get("format"));
        assertEquals("",query.get("styles"));
    }

    @Test
    public void testGetQueryKeyValuePairsJDBC() throws Exception {
        URI uri = URI.create("h2:target/test-resources/dbH2OwsMapContextTest?catalog=&schema=PUBLIC&table=LANDCOVER2000");
        Map<String,String> query = URIUtility.getQueryKeyValuePairs(uri);
        assertEquals(3,query.size());
        assertEquals("",query.get("catalog"));
        assertEquals("PUBLIC",query.get("schema"));
        assertEquals("LANDCOVER2000",query.get("table"));
    }

    @Test
    public void testRelativize() throws Exception {
        URI rel = new URI("file:///home/user/OrbisGIS/maps/landcover/bla/text.txt");
        URI folder = new URI("file:///home/user/OrbisGIS/maps/landcover/folder/");
        assertEquals("../bla/text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/maps/landcover/text.txt");
        assertEquals("../text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/maps/text.txt");
        assertEquals("../../text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/maps/landcover/folder/text.txt");
        assertEquals("text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/maps/landcover/folder/sub/text.txt");
        assertEquals("sub/text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/text.txt");
        assertEquals("../../../text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///home/user/OrbisGIS/maps/landcover/test/folder/text.txt");
        assertEquals("../test/folder/text.txt", URIUtility.relativize(folder, rel).toString());
        rel = new URI("file:///");
        assertEquals("../../../../../../", URIUtility.relativize(folder, rel).toString());
        // This with a file in the base part, file is ignored by relativize
        folder = new URI("file:///home/user/OrbisGIS/maps/landcover/folder/bla.ows");
        rel = new URI("file:///home/user/OrbisGIS/maps/landcover/data/data.shp");
        assertEquals("../data/data.shp", URIUtility.relativize(folder, rel).toString());
    }
}
