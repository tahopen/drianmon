/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package mondrian.xmla.impl;

import java.util.HashMap;
import java.util.Map;

import mondrian.server.DynamicContentFinder;
import mondrian.server.RepositoryContentFinder;

/**
 * Extends DefaultXmlaServlet to add dynamic datasource loading capability.
 *
 * @author Thiyagu Ajit
 * @author Luc Boudreau
 */
public class DynamicDatasourceXmlaServlet extends MondrianXmlaServlet {
    private static final long serialVersionUID = 1L;

    /**
     * A map of datasources definitions to {@link DynamicContentFinder}
     * instances.
     */
    private final Map<String, DynamicContentFinder> finders =
        new HashMap<String, DynamicContentFinder>();

    @Override
    protected RepositoryContentFinder makeContentFinder(String dataSources) {
        if (!finders.containsKey(dataSources)) {
            finders.put(dataSources, new DynamicContentFinder(dataSources));
        }
        return finders.get(dataSources);
    }
    @Override
    public void destroy() {
        for (DynamicContentFinder finder : finders.values()) {
            finder.shutdown();
        }
        super.destroy();
    }
}

// End DynamicDatasourceXmlaServlet.java
