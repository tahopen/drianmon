/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2001-2005 Julian Hyde
// Copyright (C) 2005-2017 Hitachi Vantara and others
// All Rights Reserved.
*/

package drianmon.rolap;

import drianmon.olap.DrianmonDef;

/**
 * Provides the base cubes that a virtual cube uses and
 * specifies if unrelated dimensions to measures from these cubes should be
 * ignored.
 *
 * @author ajoglekar
 * @since Nov 22 2007
 */
public class RolapCubeUsages {
    private DrianmonDef.CubeUsages cubeUsages;

    public RolapCubeUsages(DrianmonDef.CubeUsages cubeUsage) {
        this.cubeUsages = cubeUsage;
    }

    public boolean shouldIgnoreUnrelatedDimensions(String baseCubeName) {
        if (cubeUsages == null || cubeUsages.cubeUsages == null) {
            return false;
        }
        for (DrianmonDef.CubeUsage usage : cubeUsages.cubeUsages) {
            if (usage.cubeName.equals(baseCubeName)
                && Boolean.TRUE.equals(usage.ignoreUnrelatedDimensions))
            {
                return true;
            }
        }
        return false;
    }
}

// End RolapCubeUsages.java
