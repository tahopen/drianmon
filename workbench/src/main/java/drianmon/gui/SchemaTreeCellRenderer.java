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

import org.eigenbase.xom.ElementDef;

import drianmon.gui.validate.ValidationUtils;
import drianmon.gui.validate.impl.*;

import java.awt.*;
import javax.swing.*;
import javax.swing.tree.TreePath;

/**
 * Render an entry for the tree.
 *
 * @author sean
 */
public class SchemaTreeCellRenderer
    extends javax.swing.tree.DefaultTreeCellRenderer
{
    private final ClassLoader myClassLoader;
    public boolean invalidFlag;
    private JdbcMetaData jdbcMetaData;
    private final Workbench workbench;

    /**
     * Creates a SchemaTreeCellRenderer with Workbench and metadata.
     */
    public SchemaTreeCellRenderer(
        Workbench workbench,
        JdbcMetaData jdbcMetaData)
    {
        super();
        this.myClassLoader = this.getClass().getClassLoader();
        this.workbench = workbench;
        this.jdbcMetaData = jdbcMetaData;
    }

    /**
     * Creates a SchemaTreeCellRenderer.
     */
    public SchemaTreeCellRenderer() {
        this(null, null);
    }

    public Component getTreeCellRendererComponent(
        JTree tree,
        Object value,
        boolean sel,
        boolean expanded,
        boolean leaf,
        int row,
        boolean hasFocus)
    {
        super.getTreeCellRendererComponent(
            tree, value, sel, expanded, leaf, row, hasFocus);

        invalidFlag = isInvalid(tree, value, row);

        // Allow the layout mgr to calculate the pref size of renderer.
        this.setPreferredSize(null);
        if (value instanceof DrianmonGuiDef.Cube) {
            setText(invalidFlag, ((DrianmonGuiDef.Cube) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("cube"))));
        } else if (value instanceof DrianmonGuiDef.Column) {
            setText(invalidFlag, ((DrianmonGuiDef.Column) value).name);
        } else if (value instanceof DrianmonGuiDef.Dimension) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "dimension"))));
            setText(invalidFlag, ((DrianmonGuiDef.CubeDimension) value).name);
            // Do not remove this line.  This sets the preferred width of tree
            // cell displaying dimension name.  This resolves the ambiguous
            // problem of last char or last word truncated from dimension name
            // in the tree cell.  This problem was there with only Dimension
            // objects, while all other objects display their names without any
            // truncation of characters. Therefore, we have to force the setting
            // of preferred width to desired width so that characters do not
            // truncate from dimension name.  Along with this the preferred size
            // of other objects should be set to null, so that the layout mgr
            // can calculate the preferred width in case of other objects.
            this.setPreferredSize(
                new java.awt.Dimension(
                    this.getPreferredSize().width + 1,
                    25));
        } else if (value instanceof DrianmonGuiDef.DimensionUsage) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "dimensionUsage"))));
            setText(invalidFlag, ((DrianmonGuiDef.CubeDimension) value).name);
        } else if (value instanceof DrianmonGuiDef.KeyExpression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("key"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.keyExpression.title",
                    "Key Expression"));
        } else if (value instanceof DrianmonGuiDef.NameExpression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("name"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.nameExpression.title",
                    "Name Expression"));
        } else if (value instanceof DrianmonGuiDef.OrdinalExpression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "ordinal"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.ordinalExpression.title",
                    "Ordinal Expression"));
        } else if (value instanceof DrianmonGuiDef.CaptionExpression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("name"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.captionExpression.title",
                    "Caption Expression"));
        } else if (value instanceof DrianmonGuiDef.ParentExpression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "parent"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.parentExpression.title",
                    "Parent Expression"));
        } else if (value instanceof DrianmonGuiDef.Expression) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "expression"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.expression.title",
                    "Expression"));
        } else if (value instanceof DrianmonGuiDef.ExpressionView) {
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "expression"))));
            setText(
                workbench.getResourceConverter().getString(
                    "common.expressionView.title",
                    "Expression View"));
        } else if (value instanceof DrianmonGuiDef.Hierarchy) {
            String name = ((DrianmonGuiDef.Hierarchy) value).name;

            if (name == null || name.trim().length() == 0) {
                setText(
                    invalidFlag,
                    workbench.getResourceConverter().getString(
                        "common.hierarchy.default.name",
                        "default"));
            } else {
                setText(invalidFlag, name);
            }
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "hierarchy"))));
            this.setPreferredSize(
                new java.awt.Dimension(
                    this.getPreferredSize().width + 1,
                    25));
        } else if (value instanceof DrianmonGuiDef.RelationOrJoin) {
            TreePath tpath = tree.getPathForRow(row);
            String prefix = "";
            if (tpath != null) {
                TreePath parentpath = tpath.getParentPath();
                if (parentpath != null) {
                    Object parent = parentpath.getLastPathComponent();
                    if (parent instanceof DrianmonGuiDef.Join) {
                        int indexOfChild = tree.getModel().getIndexOfChild(
                            parent, value);
                        switch (indexOfChild) {
                        case 0:
                            prefix = workbench.getResourceConverter().getString(
                                "common.left.title",
                                "Left")
                                     + " ";
                            break;
                        case 1:
                            prefix = workbench.getResourceConverter().getString(
                                "common.right.title",
                                "Right")
                                     + " ";
                            break;
                        }
                    }
                }
            }
            if (value instanceof DrianmonGuiDef.Join) {
                setText(
                    workbench.getResourceConverter().getFormattedString(
                        "schemaTreeCellRenderer.join.title",
                        "{0}Join",
                        prefix));
                super.setIcon(
                    new ImageIcon(
                        myClassLoader.getResource(
                            workbench.getResourceConverter().getGUIReference(
                                "join"))));
            } else if (value instanceof DrianmonGuiDef.Table) {
                // Set the table name to alias if present.
                DrianmonGuiDef.Table theTable = (DrianmonGuiDef.Table) value;
                String theName =
                    (theTable.alias != null
                     && theTable.alias.trim().length() > 0)
                    ? theTable.alias
                    : theTable.name;
                setText(
                    workbench.getResourceConverter().getFormattedString(
                        "schemaTreeCellRenderer.table.title",
                        "{0}Table: {1}",
                        prefix.length() == 0
                            ? ""
                            : prefix + " : ",
                        theName));
                super.setIcon(
                    new ImageIcon(
                        myClassLoader.getResource(
                            workbench.getResourceConverter().getGUIReference(
                                "table"))));
            } else if (value instanceof DrianmonGuiDef.View) {
                setText(
                    workbench.getResourceConverter().getFormattedString(
                        "schemaTreeCellRenderer.view.title",
                        "View"));
            }
            // REVIEW: Need to deal with InlineTable here
            this.getPreferredSize();
            // Do not remove this
            this.setPreferredSize(
                new Dimension(
                    this.getPreferredSize().width + 35,
                    24));
        } else if (value instanceof DrianmonGuiDef.Level) {
            setText(invalidFlag, ((DrianmonGuiDef.Level) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("level"))));
            // See earlier comments about setPreferredSize.
            this.setPreferredSize(
                new java.awt.Dimension(
                    this.getPreferredSize().width + 1,
                    25)); // Do not remove this
        } else if (value instanceof DrianmonGuiDef.Measure) {
            setText(invalidFlag, ((DrianmonGuiDef.Measure) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "measure"))));
        } else if (value instanceof DrianmonGuiDef.Formula) {
            setText(invalidFlag, ((DrianmonGuiDef.Formula) value).getName());
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "formula"))));
        } else if (value instanceof DrianmonGuiDef.MemberReaderParameter) {
            setText(
                invalidFlag,
                ((DrianmonGuiDef.MemberReaderParameter) value).name);
        } else if (value instanceof DrianmonGuiDef.Property) {
            setText(invalidFlag, ((DrianmonGuiDef.Property) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "property"))));
        } else if (value instanceof DrianmonGuiDef.Schema) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.schema.title",
                    "Schema"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "schema"))));
        } else if (value instanceof DrianmonGuiDef.NamedSet) {
            setText(invalidFlag, ((DrianmonGuiDef.NamedSet) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "namedSet"))));
        } else if (value instanceof DrianmonGuiDef.CalculatedMember) {
            setText(
                invalidFlag, ((DrianmonGuiDef.CalculatedMember) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "calculatedMember"))));
        } else if (value instanceof DrianmonGuiDef.CalculatedMemberProperty) {
            setText(
                invalidFlag,
                ((DrianmonGuiDef.CalculatedMemberProperty) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("nopic"))));
        } else if (value instanceof DrianmonGuiDef.UserDefinedFunction) {
            setText(
                invalidFlag, ((DrianmonGuiDef.UserDefinedFunction) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "userDefinedFunction"))));
        } else if (value instanceof DrianmonGuiDef.MemberFormatter) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.memberFormatter.title",
                    "Member Formatter"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "format"))));
        } else if (value instanceof DrianmonGuiDef.CellFormatter) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.cellFormatter.title",
                    "Cell Formatter"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "format"))));
        } else if (value instanceof DrianmonGuiDef.PropertyFormatter) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.propertyFormatter.title",
                    "Property Formatter"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "format"))));
        } else if (value instanceof DrianmonGuiDef.Script) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.script.title",
                    "Script"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "script"))));
        } else if (value instanceof DrianmonGuiDef.Role) {
            setText(invalidFlag, ((DrianmonGuiDef.Role) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("role"))));
        } else if (value instanceof DrianmonGuiDef.Parameter) {
            setText(invalidFlag, ((DrianmonGuiDef.Parameter) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "parameter"))));
        } else if (value instanceof DrianmonGuiDef.SchemaGrant) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.schemaGrant.title",
                    "Schema Grant"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "schemaGrant"))));
        } else if (value instanceof DrianmonGuiDef.CubeGrant) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.cubeGrant.title",
                    "Cube Grant"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "cubeGrant"))));
        } else if (value instanceof DrianmonGuiDef.DimensionGrant) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.dimensionGrant.title",
                    "Dimension Grant"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "dimensionGrant"))));
        } else if (value instanceof DrianmonGuiDef.HierarchyGrant) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.hierarchyGrant.title",
                    "Hierarchy Grant"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "hierarchyGrant"))));
        } else if (value instanceof DrianmonGuiDef.MemberGrant) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.memberGrant.title",
                    "Member Grant"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "memberGrant"))));
        } else if (value instanceof DrianmonGuiDef.Annotations) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.annotations.title",
                    "Annotations"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "annotations"))));
        } else if (value instanceof DrianmonGuiDef.Annotation) {
            setText(
                invalidFlag, ((DrianmonGuiDef.Annotation)value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "annotation"))));
        } else if (value instanceof DrianmonGuiDef.SQL) {
            setText(invalidFlag, ((DrianmonGuiDef.SQL) value).dialect);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench
                            .getResourceConverter().getGUIReference("sql"))));
        } else if (value instanceof DrianmonGuiDef.View) {
            setText(
                workbench.getResourceConverter().getString(
                    "common.view.title",
                    "View"));
        } else if (value instanceof DrianmonGuiDef.VirtualCube) {
            setText(invalidFlag, ((DrianmonGuiDef.VirtualCube) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "virtualCube"))));
        } else if (value instanceof DrianmonGuiDef.VirtualCubeDimension) {
            setText(
                invalidFlag,
                ((DrianmonGuiDef.VirtualCubeDimension) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "virtualCubeDimension"))));
        } else if (value instanceof DrianmonGuiDef.VirtualCubeMeasure) {
            setText(
                invalidFlag, ((DrianmonGuiDef.VirtualCubeMeasure) value).name);
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "virtualCubeMeasure"))));
        } else if (value instanceof DrianmonGuiDef.AggName) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggName.title",
                    "Aggregate Name"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggTable"))));
        } else if (value instanceof DrianmonGuiDef.AggForeignKey) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggForeignKey.title",
                    "Aggregate Foreign Key"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggForeignKey"))));
        } else if (value instanceof DrianmonGuiDef.AggIgnoreColumn) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggIgnoreColumn.title",
                    "Aggregate Ignore Column"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggIgnoreColumn"))));
        } else if (value instanceof DrianmonGuiDef.AggLevel) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggLevel.title", "Aggregate Level"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggLevel"))));

        } else if (value instanceof DrianmonGuiDef.AggLevelProperty) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggLevelProperty.title",
                    "Aggregate Level Property"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "property"))));
        } else if (value instanceof DrianmonGuiDef.AggMeasure) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggMeasure.title",
                    "Aggregate Measure"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggMeasure"))));
        } else if (value instanceof DrianmonGuiDef.AggPattern) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggPattern.title",
                    "Aggregate Pattern"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggPattern"))));
        } else if (value instanceof DrianmonGuiDef.AggExclude) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.aggExclude.title",
                    "Aggregate Exclude"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "aggExclude"))));
        } else if (value instanceof DrianmonGuiDef.Closure) {
            setText(
                invalidFlag, workbench.getResourceConverter().getString(
                    "common.closure.title",
                    "Closure"));
            super.setIcon(
                new ImageIcon(
                    myClassLoader.getResource(
                        workbench.getResourceConverter().getGUIReference(
                            "closure"))));
        } else if (value instanceof ElementDef) {
            setText(((ElementDef) value).getName());
        } else {
            super.setText("");
        }

        return this;
    }

    // called from external methods
    public String invalid(JTree tree, TreePath tpath, Object value) {
        return this.invalid(tree, tpath, value, null, null, null, null);
    }

    public String invalid(
        JTree tree,
        TreePath tpath,
        Object value,
        DrianmonGuiDef.Cube cube,
        DrianmonGuiDef.Dimension parentDimension,
        DrianmonGuiDef.Hierarchy parentHierarchy,
        DrianmonGuiDef.Level parentLevel)
    {
        return ValidationUtils.invalid(
            new WorkbenchMessages(workbench.getResourceConverter()),
            new WorkbenchJdbcValidator(jdbcMetaData),
            new WorkbenchTreeModel((SchemaTreeModel) tree.getModel()),
            new WorkbenchTreeModelPath(tpath),
            value,
            cube,
            parentDimension,
            parentHierarchy,
            parentLevel,
            jdbcMetaData.getRequireSchema());
    }

    private boolean isInvalid(JTree tree, Object value, int row) {
        // getPathForRow(row) returns null for new objects added to tree in the
        // first run of rendering. Check for null before calling methods on
        // Treepath returned.
        return invalid(tree, tree.getPathForRow(row), value) != null;
    }

    public void setText(boolean invalidFlag, String myText) {
        if (invalidFlag) {
            myText = "<html><FONT COLOR=RED><b>x</b></FONT><FONT COLOR="
                     + getForeground().hashCode()
                     + ">"
                     + myText
                     + "</FONT></html>";
        }
        setText(myText);
    }

    /**
     * Called from {@link SchemaExplorer#resetMetaData(JdbcMetaData)}. A call to
     * {@link #updateUI} should be made on the owning SchemaFrame to reflect the
     * use of the JdbcMetaData being set.
     *
     * @param jdbcMetaData Meta data
     */
    public void setMetaData(JdbcMetaData jdbcMetaData) {
        this.jdbcMetaData = jdbcMetaData;
    }
}

// End SchemaTreeCellRenderer.java
