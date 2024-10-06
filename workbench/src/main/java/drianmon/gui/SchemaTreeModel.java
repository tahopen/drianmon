/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2002-2005 Julian Hyde
// Copyright (C) 2005-2017 Hitachi Vantara and others
// Copyright (C) 2006-2007 CINCOM SYSTEMS, INC.
// All Rights Reserved.
*/

package drianmon.gui;

import drianmon.gui.DrianmonGuiDef;
import mondrian.util.CompositeList;

import java.util.*;
import javax.swing.tree.*;

/**
 * @author sean
 */
public class SchemaTreeModel extends DefaultTreeModel {
    /**
     * Creates a SchemaTreeModel.
     */
    public SchemaTreeModel() {
        super(null);
        // TODO Auto-generated constructor stub
    }

    DrianmonGuiDef.Schema schema;

    /**
     * Creates a new instance of SchemaTreeModel
     */
    public SchemaTreeModel(DrianmonGuiDef.Schema s) {
        super(new DefaultMutableTreeNode(s.name));
        this.schema = s;
    }


    /**
     * Returns the child of <code>parent</code> at index <code>index</code>
     * in the parent's
     * child array.  <code>parent</code> must be a node previously obtained
     * from this data source. This should not return <code>null</code>
     * if <code>index</code>
     * is a valid index for <code>parent</code> (that is <code>index >= 0 &&
     * index < getChildCount(parent</code>)).
     *
     * @param parent a node in the tree, obtained from this data source
     * @return the child of <code>parent</code> at index <code>index</code>
     */
    public Object getChild(Object parent, int index) {
        return getChildList(parent).get(index);
    }

    /**
     * Returns an immutable list of child elements of a given element.
     *
     * @param parent Parent element
     * @return List of children
     */
    private List<Object> getChildList(Object parent) {
        if (parent instanceof DrianmonGuiDef.Cube) {
            DrianmonGuiDef.Cube c = (DrianmonGuiDef.Cube) parent;
            // Return children in this order: fact table, dimensions, measures,
            // calculatedMembers, namedSets
            return new CompositeList<Object>(
                ifList(c.fact),
                Arrays.asList(c.dimensions),
                Arrays.asList(c.measures),
                Arrays.asList(c.calculatedMembers),
                Arrays.asList(c.namedSets),
                ifList(c.annotations));
        } else if (parent instanceof DrianmonGuiDef.Dimension) {
            DrianmonGuiDef.Dimension d = (DrianmonGuiDef.Dimension) parent;
            return new CompositeList<Object>(
                Arrays.asList((Object[]) d.hierarchies),
                ifList(d.annotations));
        } else if (parent instanceof DrianmonGuiDef.UserDefinedFunction) {
            DrianmonGuiDef.UserDefinedFunction udf =
                (DrianmonGuiDef.UserDefinedFunction) parent;
            return new CompositeList<Object>(
                ifList(udf.script));
        } else if (parent instanceof DrianmonGuiDef.ExpressionView) {
            DrianmonGuiDef.ExpressionView ev =
                (DrianmonGuiDef.ExpressionView) parent;
            return Arrays.asList((Object[]) ev.expressions);
        } else if (parent instanceof DrianmonGuiDef.Hierarchy) {
            DrianmonGuiDef.Hierarchy h = (DrianmonGuiDef.Hierarchy) parent;
            return new CompositeList<Object>(
                Arrays.asList(h.levels),
                Arrays.asList(h.memberReaderParameters),
                ifList(h.relation),
                ifList(h.annotations));
        } else if (parent instanceof DrianmonGuiDef.Join) {
            DrianmonGuiDef.Join j = (DrianmonGuiDef.Join) parent;
            return Arrays.<Object>asList(
                j.left,
                j.right);
        } else if (parent instanceof DrianmonGuiDef.Level) {
            DrianmonGuiDef.Level level = (DrianmonGuiDef.Level) parent;
            return new CompositeList<Object>(
                Arrays.asList(level.properties),
                ifList(level.keyExp),
                ifList(level.nameExp),
                ifList(level.ordinalExp),
                ifList(level.captionExp),
                ifList(level.parentExp),
                ifList(level.closure),
                ifList(level.memberFormatter),
                ifList(level.annotations));
        } else if (parent instanceof DrianmonGuiDef.CellFormatter) {
            DrianmonGuiDef.CellFormatter f =
                (DrianmonGuiDef.CellFormatter) parent;
            return new CompositeList<Object>(
                ifList(f.script));
        } else if (parent instanceof DrianmonGuiDef.MemberFormatter) {
            DrianmonGuiDef.MemberFormatter f =
                (DrianmonGuiDef.MemberFormatter) parent;
            return new CompositeList<Object>(
                ifList(f.script));
        } else if (parent instanceof DrianmonGuiDef.PropertyFormatter) {
            DrianmonGuiDef.PropertyFormatter f =
                (DrianmonGuiDef.PropertyFormatter) parent;
            return new CompositeList<Object>(
                ifList(f.script));
        } else if (parent instanceof DrianmonGuiDef.Property) {
            DrianmonGuiDef.Property property =
                (DrianmonGuiDef.Property) parent;
            return new CompositeList<Object>(
                ifList(property.propertyFormatter));
        } else if (parent instanceof DrianmonGuiDef.CalculatedMember) {
            DrianmonGuiDef.CalculatedMember c =
                (DrianmonGuiDef.CalculatedMember) parent;
            return new CompositeList<Object>(
                ifList(c.formulaElement),
                arrayList(c.memberProperties),
                ifList(c.annotations),
                ifList(c.cellFormatter));
        } else if (parent instanceof DrianmonGuiDef.Measure) {
            DrianmonGuiDef.Measure m = (DrianmonGuiDef.Measure) parent;
            return new CompositeList<Object>(
                ifList(m.measureExp),
                arrayList(m.memberProperties),
                ifList(m.annotations),
                ifList(m.cellFormatter));
        } else if (parent instanceof DrianmonGuiDef.NamedSet) {
            DrianmonGuiDef.NamedSet m = (DrianmonGuiDef.NamedSet) parent;
            return new CompositeList<Object>(
                ifList((Object) m.formulaElement),
                ifList(m.annotations));
        } else if (parent instanceof DrianmonGuiDef.Schema) {
            DrianmonGuiDef.Schema s = (DrianmonGuiDef.Schema) parent;
            // Return children in this order: cubes, dimensions, namedSets,
            // userDefinedFunctions, virtual cubes, roles
            return new CompositeList<Object>(
                Arrays.asList(s.cubes),
                Arrays.asList(s.dimensions),
                Arrays.asList(s.namedSets),
                Arrays.asList(s.namedSets),
                Arrays.asList(s.userDefinedFunctions),
                Arrays.asList(s.virtualCubes),
                Arrays.asList(s.roles),
                Arrays.asList(s.parameters),
                ifList(s.annotations));
        } else if (parent instanceof DrianmonGuiDef.Table) {
            DrianmonGuiDef.Table t = (DrianmonGuiDef.Table) parent;
            return new CompositeList<Object>(
                arrayList(t.aggTables),
                arrayList(t.aggExcludes));
        } else if (parent instanceof DrianmonGuiDef.AggTable) {
            DrianmonGuiDef.AggTable t = (DrianmonGuiDef.AggTable) parent;
            return new CompositeList<Object>(
                ifList(t.factcount),
                Arrays.asList(t.measuresfactcount),
                Arrays.asList(t.ignoreColumns),
                Arrays.asList(t.foreignKeys),
                Arrays.asList(t.measures),
                Arrays.asList(t.levels),
                (t instanceof DrianmonGuiDef.AggPattern)
                    ? Arrays.asList(((DrianmonGuiDef.AggPattern) t).excludes)
                    : Collections.emptyList());
        } else if (parent instanceof DrianmonGuiDef.AggLevel) {
            DrianmonGuiDef.AggLevel t = (DrianmonGuiDef.AggLevel) parent;
            if (t.properties == null) {
                t.properties = new DrianmonGuiDef.AggLevelProperty[0];
            }
            return new CompositeList<Object>(
                Arrays.asList(t.properties));
        } else if (parent instanceof DrianmonGuiDef.View) {
            DrianmonGuiDef.View v = (DrianmonGuiDef.View) parent;
            return Arrays.asList((Object[]) v.selects);
        } else if (parent instanceof DrianmonGuiDef.VirtualCube) {
            DrianmonGuiDef.VirtualCube c = (DrianmonGuiDef.VirtualCube) parent;
            return new CompositeList<Object>(
                Arrays.asList(c.dimensions),
                Arrays.asList(c.measures),
                Arrays.asList(c.calculatedMembers),
                ifList(c.annotations));
        } else if (parent instanceof DrianmonGuiDef.VirtualCubeDimension) {
            DrianmonGuiDef.VirtualCubeDimension d =
                (DrianmonGuiDef.VirtualCubeDimension) parent;
            return ifList((Object)d.annotations);
        } else if (parent instanceof DrianmonGuiDef.VirtualCubeMeasure) {
            DrianmonGuiDef.VirtualCubeMeasure m =
                (DrianmonGuiDef.VirtualCubeMeasure) parent;
            return ifList((Object)m.annotations);
        } else if (parent instanceof DrianmonGuiDef.Role) {
            DrianmonGuiDef.Role c = (DrianmonGuiDef.Role) parent;
            return new CompositeList<Object>(
                Arrays.asList((Object[]) c.schemaGrants),
                ifList((Object)c.annotations));
        } else if (parent instanceof DrianmonGuiDef.SchemaGrant) {
            DrianmonGuiDef.SchemaGrant c = (DrianmonGuiDef.SchemaGrant) parent;
            return Arrays.asList((Object[]) c.cubeGrants);
        } else if (parent instanceof DrianmonGuiDef.CubeGrant) {
            DrianmonGuiDef.CubeGrant c = (DrianmonGuiDef.CubeGrant) parent;
            return new CompositeList<Object>(
                Arrays.asList(c.dimensionGrants),
                Arrays.asList(c.hierarchyGrants));
        } else if (parent instanceof DrianmonGuiDef.HierarchyGrant) {
            DrianmonGuiDef.HierarchyGrant c =
                (DrianmonGuiDef.HierarchyGrant) parent;
            return Arrays.asList((Object[]) c.memberGrants);
        } else if (parent instanceof DrianmonGuiDef.Closure) {
            DrianmonGuiDef.Closure c = (DrianmonGuiDef.Closure) parent;
            return ifList((Object) c.table);
        } else if (parent instanceof DrianmonGuiDef.Annotations) {
            DrianmonGuiDef.Annotations annotations =
                (DrianmonGuiDef.Annotations) parent;
            return Arrays.asList((Object[]) annotations.array);
        } else {
            // In particular: Column, SQL, DimensionUsage have no children.
            return Collections.emptyList();
        }
    }

    /**
     * Returns a list with zero or one elements.
     *
     * @param e Element
     * @param <T> Element type
     * @return List containing element if it is not null, otherwise empty list
     */
    private <T> List<T> ifList(T e) {
        return e == null
            ? Collections.<T>emptyList()
            : Collections.singletonList(e);
    }

    /**
     * Returns a list with a given set of elements, or an empty list if the
     * array is null.
     *
     * @param e Element
     * @param <T> Element type
     * @return List containing element if it is not null, otherwise empty list
     */
    private <T> List<T> arrayList(T... e) {
        return e == null || e.length == 0
            ? Collections.<T>emptyList()
            : e.length == 1
            ? Collections.singletonList(e[0])
            : Arrays.asList(e);
    }

    /**
     * Returns the number of children of <code>parent</code>.
     * Returns 0 if the node
     * is a leaf or if it has no children.  <code>parent</code> must be a node
     * previously obtained from this data source.
     *
     * @param parent a node in the tree, obtained from this data source
     * @return the number of children of the node <code>parent</code>
     */
    public int getChildCount(Object parent) {
        return getChildList(parent).size();
    }

    /**
     * Returns the index of child in parent.  If <code>parent</code>
     * is <code>null</code> or <code>child</code> is <code>null</code>,
     * returns -1.
     *
     * @param parent a note in the tree, obtained from this data source
     * @param child  the node we are interested in
     * @return the index of the child in the parent, or -1 if either
     *         <code>child</code> or <code>parent</code> are <code>null</code>
     */
    public int getIndexOfChild(Object parent, Object child) {
        if (parent == null) {
            return -1;
        }
        final List<Object> list = getChildList(parent);
        int i = 0;
        for (Object o : list) {
            if (equal(o, child)) {
                return i;
            }
            ++i;
        }
        return -1;
    }

    /**
     * Returns whether two XML objects are equal.
     *
     * @param o1 First object
     * @param o2 Second object
     * @return Whether objects are equal
     */
    private boolean equal(Object o1, Object o2) {
        if (o1 == null) {
            return o2 == null;
        } else if (o1 instanceof DrianmonGuiDef.Hierarchy
             || o1 instanceof DrianmonGuiDef.SQL)
        {
            return o1 == o2;
        } else {
            return o1.equals(o2);
        }
    }

    /**
     * Returns the root of the tree.  Returns <code>null</code>
     * only if the tree has no nodes.
     *
     * @return the root of the tree
     */
    public Object getRoot() {
        return schema;
    }

    /**
     * Returns <code>true</code> if <code>node</code> is a leaf.
     * It is possible for this method to return <code>false</code>
     * even if <code>node</code> has no children.
     * A directory in a filesystem, for example,
     * may contain no files; the node representing
     * the directory is not a leaf, but it also has no children.
     *
     * @param node a node in the tree, obtained from this data source
     * @return true if <code>node</code> is a leaf
     */
    public boolean isLeaf(Object node) {
        return getChildCount(node) == 0;
    }

    public void valueForPathChanged(TreePath path, Object newValue) {
        //super.valueForPathChanged(path, newValue);
    }

}

// End SchemaTreeModel.java
