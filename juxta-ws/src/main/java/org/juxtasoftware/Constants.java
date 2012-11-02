package org.juxtasoftware;

import eu.interedition.text.Name;
import eu.interedition.text.mem.SimpleName;

import java.net.URI;

public interface Constants {
    final URI JUXTA_NS = URI.create("http://juxtasoftware.org/ns");
    final URI TEI_NS = URI.create("http://www.tei-c.org/ns/1.0");

    final Name GAP_NAME = new SimpleName(Constants.JUXTA_NS, "gap");
    final Name TOKEN_NAME = new SimpleName(Constants.JUXTA_NS, "token");
    final Name ALIGNMENT_NAME = new SimpleName(JUXTA_NS, "alignment");
    final Name CHANGE_NAME = new SimpleName(JUXTA_NS, "change");
    final Name ADD_DEL_NAME = new SimpleName(JUXTA_NS, "addDel");
    
    final Name TEI_ADD = new SimpleName(TEI_NS, "add");
    final Name TEI_ADD_SPAN = new SimpleName(TEI_NS, "addSpan");
    final Name TEI_DEL = new SimpleName(TEI_NS, "del");
    final Name TEI_DEL_SPAN = new SimpleName(TEI_NS, "delSpan");

    final Name EDIT_DISTANCE_NAME = new SimpleName(JUXTA_NS, "editDistance");

    final Name TRANSPOSITION_NAME = new SimpleName(JUXTA_NS, "transposition");

    final Name LOCATION_MARKER_NAME = new SimpleName(JUXTA_NS, "locationMarker");
    final Name LOCATION_MARKER_TYPE_ATTR = new SimpleName((URI) null, "type");
    final Name LOCATION_MARKER_NUM_ATTR = new SimpleName((URI) null, "n");
    
    final String PARALLEL_SEGMENTATION_TEMPLATE = "TEI-parallel-segmentation";
    final String WS_LOGGER_NAME = "juxtaWS";
    final String METRICS_LOGGER_NAME = "metrics";
}
