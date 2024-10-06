/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2002-2005 Julian Hyde
// Copyright (C) 2005-2021 Hitachi Vantara and others
// Copyright (C) 2006-2007 CINCOM SYSTEMS, INC.
// All Rights Reserved.
*/

package drianmon.gui;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.eigenbase.xom.*;

import drianmon.gui.DrianmonGuiDef;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.event.*;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.table.*;
import javax.swing.text.DefaultEditorKit;
import javax.swing.text.PlainDocument;
import javax.swing.tree.*;

/**
 * @author sean
 */
public class SchemaExplorer
    extends javax.swing.JPanel
    implements TreeSelectionListener, CellEditorListener
{
    private static final Logger LOGGER = LogManager.getLogger(SchemaExplorer.class);

    private final Workbench workbench;
    private DrianmonGuiDef.Schema schema;
    private SchemaTreeModel model;
    private SchemaTreeCellRenderer renderer;
    private File schemaFile;
    private JTreeUpdater updater;
    private final ClassLoader myClassLoader;
    private boolean newFile;

    // indicates file is without changes, dirty=true when some changes are made
    // to the file
    private boolean dirty = false;

    // indicates dirty status shown on title
    private boolean dirtyFlag = false;

    private JInternalFrame parentIFrame;
    private JdbcMetaData jdbcMetaData;
    private boolean editModeXML = false;
    private String errMsg = null;

    /**
     * Creates new form SchemaExplorer
     */
    public SchemaExplorer(Workbench workbench) {
        this.workbench = workbench;
        myClassLoader = this.getClass().getClassLoader();
        initComponents();
    }

    public SchemaExplorer(
        Workbench workbench,
        File f,
        JdbcMetaData jdbcMetaData,
        boolean newFile,
        JInternalFrame parentIFrame)
    {
        this(workbench);

        alert =
            getResourceConverter().getString(
                "schemaExplorer.alert.title",
                "Alert");

        // XML editor
        try {
            jEditorPaneXML = new JEditorPane();
        } catch (Exception ex) {
            LOGGER.error("SchemaExplorer-JEditorPane", ex);
        }

        jEditorPaneXML.setLayout(new java.awt.BorderLayout());
        jEditorPaneXML.setEditable(false);
        jScrollPaneXML = new JScrollPane(jEditorPaneXML);
        jPanelXML.setLayout(new java.awt.BorderLayout());
        jPanelXML.add(jScrollPaneXML, java.awt.BorderLayout.CENTER);
        jPanelXML.add(targetLabel2, java.awt.BorderLayout.NORTH);
        jPanelXML.add(validStatusLabel2, java.awt.BorderLayout.SOUTH);
        jPanelXML.setMaximumSize(jPanel1.getMaximumSize());
        jPanelXML.setPreferredSize(jPanel1.getPreferredSize());

        databaseLabel.setText(
            getResourceConverter().getFormattedString(
                "schemaExplorer.database.text",
                "Database - {0} ({1})",
                nvl(jdbcMetaData.getDbCatalogName()),
                jdbcMetaData.getDatabaseProductName()));

        try {
            Parser xmlParser = XOMUtil.createDefaultParser();
            this.schemaFile = f;
            this.setNewFile(newFile);
            this.parentIFrame = parentIFrame;

            this.jdbcMetaData = jdbcMetaData;

            if (newFile) {
                schema = new DrianmonGuiDef.Schema();
                schema.parameters = new DrianmonGuiDef.Parameter[0];
                schema.cubes = new DrianmonGuiDef.Cube[0];
                schema.dimensions = new DrianmonGuiDef.Dimension[0];
                schema.namedSets = new DrianmonGuiDef.NamedSet[0];
                schema.roles = new DrianmonGuiDef.Role[0];
                schema.userDefinedFunctions =
                    new DrianmonGuiDef.UserDefinedFunction[0];
                schema.virtualCubes = new DrianmonGuiDef.VirtualCube[0];

                String sname = schemaFile.getName();
                int ext = sname.indexOf(".");
                if (ext != -1) {
                    schema.name = "New " + sname.substring(0, ext);
                }
            } else {
                try {
                    schema =
                        new DrianmonGuiDef.Schema(
                            xmlParser.parse(
                                schemaFile.toURL()));
                } catch (XOMException ex) {
                    // Parsing error of the schema file causes default tree of
                    // colors etc. to be displayed in schema explorer.
                    // Initialize the schema to display an empty schema if you
                    // want to show schema explorer for file where parsing
                    // failed.
                    ex.printStackTrace();
                    schema = new DrianmonGuiDef.Schema();
                    schema.parameters = new DrianmonGuiDef.Parameter[0];
                    schema.cubes = new DrianmonGuiDef.Cube[0];
                    schema.dimensions = new DrianmonGuiDef.Dimension[0];
                    schema.namedSets = new DrianmonGuiDef.NamedSet[0];
                    schema.roles = new DrianmonGuiDef.Role[0];
                    schema.userDefinedFunctions =
                        new DrianmonGuiDef.UserDefinedFunction[0];
                    schema.virtualCubes = new DrianmonGuiDef.VirtualCube[0];

                    LOGGER.error(
                        "Exception  : Schema file parsing failed."
                        + ex.getMessage(),
                        ex);
                    errMsg =
                        getResourceConverter().getFormattedString(
                            "schemaExplorer.parsing.error",
                            "Parsing Error: Could not open file {0}\n{1}",
                            schemaFile.toString(),
                            ex.getLocalizedMessage());
                }
            }
            // sets title of i frame with schema name and file name
            setTitle();

            renderer = new SchemaTreeCellRenderer(workbench, jdbcMetaData);
            model = new SchemaTreeModel(schema);
            tree.setModel(model);
            tree.setCellRenderer(renderer);
            tree.addTreeSelectionListener(this);

            JComboBox listEditor = new JComboBox(
                new String[]{
                    getResourceConverter().getString(
                        "schemaExplorer.hierarchy.select.join", "Join"),
                    getResourceConverter().getString(
                        "schemaExplorer.hierarchy.select.table", "Table")
                });
            listEditor.setToolTipText(
                getResourceConverter().getString(
                    "schemaExplorer.hierarchy.select.title",
                    "Select Join or Table Hierarchy"));
            listEditor.setPreferredSize(
                new java.awt.Dimension(
                    listEditor.getPreferredSize().width,
                    24)); // Do not remove this

            listEditor.addItemListener(
                new ItemListener() {
                    public void itemStateChanged(ItemEvent e) {
                        tree.stopEditing();
                        TreePath tpath = tree.getSelectionPath();
                        if (tpath != null) {
                            TreePath parentpath = tpath.getParentPath();
                            if (parentpath != null) {
                                refreshTree(parentpath);
                            }
                        }
                    }
                });

            TreeCellEditor comboEditor = new DefaultCellEditor(listEditor);

            SchemaTreeCellEditor editor = new SchemaTreeCellEditor(
                workbench, tree, renderer, comboEditor);
            tree.setCellEditor(editor);
            tree.setEditable(true);


            SchemaPropertyCellEditor spce = new SchemaPropertyCellEditor(
                workbench, jdbcMetaData);
            spce.addCellEditorListener(this);
            propertyTable.setDefaultEditor(Object.class, spce);
            // to set background color of attribute columns
            SchemaPropertyCellRenderer.attributeBackground =
                jScrollPane2.getBackground();
            SchemaPropertyCellRenderer spcr =
                new SchemaPropertyCellRenderer(workbench);
            propertyTable.setDefaultRenderer(Object.class, spcr);
        } catch (Exception ex) {
            LOGGER.error("SchemaExplorer init error", ex);
        }
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    /**
     * Called from within the constructor to initialize the form.
     */
    private void initComponents() {
        jPanelXML = new JPanel();
        jScrollPaneXML = new JScrollPane();

        footer = new JPanel();
        databaseLabel = new javax.swing.JLabel();

        jSeparator1 = new JSeparator();
        jSeparator2 = new JSeparator();
        jSplitPane1 = new JSplitPane();
        jPanel1 = new JPanel();
        jScrollPane2 = new JScrollPane();

        // propertyTable includes changeSelection and processKeyEvent
        // processing for keyboard navigation
        propertyTable = new JTable() {
            public void changeSelection(
                int rowIndex, int columnIndex, boolean toggle, boolean extend)
            {
                if (columnIndex == 0) {
                    AWTEvent currentEvent = EventQueue.getCurrentEvent();
                    if (currentEvent instanceof KeyEvent) {
                        KeyEvent ke = (KeyEvent) currentEvent;
                        int kcode = ke.getKeyCode();
                        if (kcode == KeyEvent.VK_TAB) {
                            if ((ke.getModifiersEx()
                                 & InputEvent.SHIFT_DOWN_MASK)
                                == InputEvent.SHIFT_DOWN_MASK)
                            {
                                rowIndex -= 1;
                                if (rowIndex < 0) {
                                    rowIndex = propertyTable.getRowCount() - 1;
                                }
                            }
                            setTableCellFocus(rowIndex);
                            return;
                        }
                    }
                }
                super.changeSelection(rowIndex, columnIndex, toggle, extend);
            }

            public void processKeyEvent(KeyEvent e) {
                int kcode = e.getKeyCode();
                if (kcode == KeyEvent.VK_UP || kcode == KeyEvent.VK_DOWN) {
                    int row = propertyTable.getSelectedRow();
                    setTableCellFocus(row);
                    return;
                }
                super.processKeyEvent(e);
            }
        };

        targetLabel = new javax.swing.JLabel();
        validStatusLabel = new javax.swing.JLabel();
        targetLabel2 = new javax.swing.JLabel();
        validStatusLabel2 = new javax.swing.JLabel();
        jPanel2 = new JPanel();
        jScrollPane1 = new JScrollPane();

        tree = new JTree();
        tree.getSelectionModel().setSelectionMode(
            DefaultTreeSelectionModel.SINGLE_TREE_SELECTION);

        ToolTipManager.sharedInstance().registerComponent(tree);

        jToolBar1 = new JToolBar();
        addCubeButton = new JButton();
        addDimensionButton = new JButton();
        addDimensionUsageButton = new JButton();
        addHierarchyButton = new JButton();
        addNamedSetButton = new JButton();
        addUserDefinedFunctionButton = new JButton();
        addRoleButton = new JButton();

        addMeasureButton = new JButton();
        addCalculatedMemberButton = new JButton();
        addLevelButton = new JButton();
        addPropertyButton = new JButton();
        addCalculatedMemberPropertyButton = new JButton();

        addVirtualCubeButton = new JButton();
        addVirtualCubeDimensionButton = new JButton();
        addVirtualCubeMeasureButton = new JButton();

        cutButton = new JButton(new DefaultEditorKit.CutAction());
        copyButton = new JButton(new DefaultEditorKit.CopyAction());
        pasteButton = new JButton(new DefaultEditorKit.PasteAction());
        deleteButton = new JButton();
        editModeButton = new JToggleButton();

        setLayout(new BorderLayout());

        jSplitPane1.setDividerLocation(200);
        jPanel1.setLayout(new BorderLayout());

        propertyTable.setModel(
            new DefaultTableModel(
                new Object[][]{
                },
                new String[]{
                    getResourceConverter().getString(
                        "schemaExplorer.propertyTable.attribute", "Attribute"),
                    getResourceConverter().getString(
                        "schemaExplorer.propertyTable.value", "Value")
                })
            {
                Class[] types = {String.class, Object.class};
                boolean[] canEdit = {false, true};

                public Class getColumnClass(int columnIndex) {
                    return types[columnIndex];
                }

                public boolean isCellEditable(int rowIndex, int columnIndex) {
                    return canEdit[columnIndex];
                }
            });

        // Set property table column headers to bold.
        propertyTable.getTableHeader().setFont(
            new Font(
                "Dialog",
                Font.BOLD,
                12));

        jScrollPane2.setViewportView(propertyTable);

        jPanel1.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        targetLabel.setFont(new Font("Dialog", 1, 14));
        targetLabel.setForeground(
            (Color) UIManager.getDefaults().get(
                "CheckBoxMenuItem.acceleratorForeground"));
        targetLabel.setHorizontalAlignment(SwingConstants.CENTER);
        targetLabel.setText(
            getResourceConverter().getString(
                "schemaExplorer.targetLabel.title", "Schema"));
        targetLabel.setBorder(new EtchedBorder());
        // up arrow button for property table heading
        jPanel3 = new JPanel();
        jPanel3.setLayout(new BorderLayout());
        BasicArrowButton arrowButtonUp =
            new BasicArrowButton(SwingConstants.NORTH);
        BasicArrowButton arrowButtonDown =
            new BasicArrowButton(SwingConstants.SOUTH);
        arrowButtonUp.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.arrowButtonUp.toolTip",
                "move to parent element"));
        arrowButtonDown.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.arrowButtonDown.toolTip",
                "move to child element"));
        arrowButtonUpAction = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.arrowButtonUp.title", "Arrow button up"))
        {
            public void actionPerformed(ActionEvent e) {
                arrowButtonUpAction(e);
            }
        };
        arrowButtonDownAction = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.arrowButtonDown.title", "Arrow button down"))
        {
            public void actionPerformed(ActionEvent e) {
                arrowButtonDownAction(e);
            }
        };
        arrowButtonUp.addActionListener(arrowButtonUpAction);
        arrowButtonDown.addActionListener(arrowButtonDownAction);
        jPanel3.add(arrowButtonDown, java.awt.BorderLayout.EAST);
        jPanel3.add(arrowButtonUp, java.awt.BorderLayout.WEST);
        jPanel3.add(targetLabel, java.awt.BorderLayout.CENTER);

        jPanel1.add(jPanel3, java.awt.BorderLayout.NORTH);
        validStatusLabel.setFont(new Font("Dialog", Font.PLAIN, 12));
        validStatusLabel.setForeground(Color.RED);
        validStatusLabel.setHorizontalAlignment(SwingConstants.CENTER);

        jPanel1.add(validStatusLabel, java.awt.BorderLayout.SOUTH);

        // for XML viewing
        targetLabel2.setFont(new Font("Dialog", 1, 14));
        targetLabel2.setForeground(
            (Color) UIManager.getDefaults().get(
                "CheckBoxMenuItem.acceleratorForeground"));
        targetLabel2.setHorizontalAlignment(SwingConstants.CENTER);
        targetLabel2.setText(
            getResourceConverter().getString(
                "schemaExplorer.targetLabel.title", "Schema"));
        targetLabel2.setBorder(new EtchedBorder());
        validStatusLabel2.setFont(new Font("Dialog", Font.PLAIN, 12));
        validStatusLabel2.setForeground(Color.RED);
        validStatusLabel2.setHorizontalAlignment(SwingConstants.CENTER);

        jSplitPane1.setRightComponent(jPanel1);

        jPanel2.setLayout(new java.awt.BorderLayout());

        jScrollPane1.setViewportView(tree);

        jPanel2.add(jScrollPane1, java.awt.BorderLayout.CENTER);

        jSplitPane1.setLeftComponent(jPanel2);


        // ========================================================
        // actions
        // ========================================================
        addCube = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCube.title", "Add Cube"))
        {
            public void actionPerformed(ActionEvent e) {
                addCube(e);
            }
        };
        addParameter = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addParameter.title", "Add Parameter"))
        {
            public void actionPerformed(ActionEvent e) {
                addParameter(e);
            }
        };

        addDimension = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addDimension.title", "Add Dimension"))
        {
            public void actionPerformed(ActionEvent e) {
                addDimension(e);
            }
        };
        addDimensionUsage = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addDimensionUsage.title",
                "Add Dimension Usage"))
        {
            public void actionPerformed(ActionEvent e) {
                addDimensionUsage(e);
            }
        };
        addHierarchy = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addHierarchy.title", "Add Hierarchy"))
        {
            public void actionPerformed(ActionEvent e) {
                addHierarchy(e);
            }
        };
        addNamedSet = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addNamedSet.title", "Add Named Set"))
        {
            public void actionPerformed(ActionEvent e) {
                addNamedSet(e);
            }
        };
        addMeasure = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addMeasure.title", "Add Measure"))
        {
            public void actionPerformed(ActionEvent e) {
                addMeasure(e);
            }
        };
        addCalculatedMember = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCalculatedMember.title",
                "Add Calculated Member"))
        {
            public void actionPerformed(ActionEvent e) {
                addCalculatedMember(e);
            }
        };
        addUserDefinedFunction = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addUserDefinedFunction.title",
                "Add User Defined Function"))
        {
            public void actionPerformed(ActionEvent e) {
                addUserDefinedFunction(e);
            }
        };
        addScript = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addScript.title",
                "Add Script"))
        {
            public void actionPerformed(ActionEvent e) {
                addScript(e);
            }
        };
        addCellFormatter = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCellFormatter.title",
                "Add Cell Formatter"))
        {
            public void actionPerformed(ActionEvent e) {
                addCellFormatter(e);
            }
        };
        addPropertyFormatter = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addPropertyFormatter.title",
                "Add Property Formatter"))
        {
            public void actionPerformed(ActionEvent e) {
                addPropertyFormatter(e);
            }
        };
        addMemberFormatter = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addMemberFormatter.title",
                "Add Member Formatter"))
        {
            public void actionPerformed(ActionEvent e) {
                addMemberFormatter(e);
            }
        };
        addRole = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addRole.title", "Add Role"))
        {
            public void actionPerformed(ActionEvent e) {
                addRole(e);
            }
        };
        addSchemaGrant = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addSchemaGrant.title", "Add Schema Grant"))
        {
            public void actionPerformed(ActionEvent e) {
                addSchemaGrant(e);
            }
        };
        addCubeGrant = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCubeGrant.title", "Add Cube Grant"))
        {
            public void actionPerformed(ActionEvent e) {
                addCubeGrant(e);
            }
        };
        addDimensionGrant = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addDimensionGrant.title",
                "Add Dimension Grant"))
        {
            public void actionPerformed(ActionEvent e) {
                addDimensionGrant(e);
            }
        };
        addHierarchyGrant = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addHierarchyGrant.title",
                "Add Hierarchy Grant"))
        {
            public void actionPerformed(ActionEvent e) {
                addHierarchyGrant(e);
            }
        };
        addMemberGrant = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addMemberGrant.title", "Add Member Grant"))
        {
            public void actionPerformed(ActionEvent e) {
                addMemberGrant(e);
            }
        };
        addAnnotations = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAnnotations.title", "Add Annotations"))
        {
            public void actionPerformed(ActionEvent e) {
                addAnnotations(e);
            }
        };
        addAnnotation = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAnnotation.title", "Add Annotation"))
        {
            public void actionPerformed(ActionEvent e) {
                addAnnotation(e);
            }
        };

        addLevel = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addLevel.title", "Add Level"))
        {
            public void actionPerformed(ActionEvent e) {
                addLevel(e);
            }
        };
        addClosure = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addClosure.title", "Add Closure"))
        {
            public void actionPerformed(ActionEvent e) {
                addClosure(e);
            }
        };
        addKeyExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addKeyExpression.title", "Add Key Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addKeyExp(e);
            }
        };
        addNameExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addNameExpression.title",
                "Add Name Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addNameExp(e);
            }
        };
        addOrdinalExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addOrdinalExpression.title",
                "Add Ordinal Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addOrdinalExp(e);
            }
        };
        addCaptionExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCaptionExpression.title",
                "Add Caption Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addCaptionExp(e);
            }
        };
        addParentExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addParentExpression.title",
                "Add Parent Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addParentExp(e);
            }
        };
        addMeasureExp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addMeasureExpression.title",
                "Add Measure Expression"))
        {
            public void actionPerformed(ActionEvent e) {
                addMeasureExp(e);
            }
        };
        addFormula = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addFormula.title", "Add Formula"))
        {
            public void actionPerformed(ActionEvent e) {
                addFormula(e);
            }
        };
        addSQL = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addSQL.title", "Add SQL"))
        {
            public void actionPerformed(ActionEvent e) {
                addSQL(e);
            }
        };
        addTable = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addTable.title", "Add Table"))
        {
            public void actionPerformed(ActionEvent e) {
                addTable(e);
            }
        };
        addJoin = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addJoin.title", "Add Join"))
        {
            public void actionPerformed(ActionEvent e) {
                addJoin(e);
            }
        };
        addView = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addView.title", "Add View"))
        {
            public void actionPerformed(ActionEvent e) {
                addView(e);
            }
        };
        addInlineTable = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addInlineTable.title", "Add Inline Table"))
        {
            public void actionPerformed(ActionEvent e) {
                addInlineTable(e);
            }
        };


        moveLevelUp = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.moveLevelUp.title", "Move Up"))
        {
            public void actionPerformed(ActionEvent e) {
                moveLevelUp(e);
            }
        };

        moveLevelDown = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.moveLevelDown.title", "Move Down"))
        {
            public void actionPerformed(ActionEvent e) {
                moveLevelDown(e);
            }
        };


        addProperty = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addProperty.title", "Add Property"))
        {
            public void actionPerformed(ActionEvent e) {
                addProperty(e);
            }
        };

        addCalculatedMemberProperty = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addCalculatedMemberProperty.title",
                "Add Calculated Member Property"))
        {
            public void actionPerformed(ActionEvent e) {
                addCalculatedMemberProperty(e);
            }
        };

        addVirtualCube = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCube.title", "Add Virtual Cube"))
        {
            public void actionPerformed(ActionEvent e) {
                addVirtualCube(e);
            }
        };
        addVirtualCubeDimension = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCubeDimension.title",
                "Add Virtual Cube Dimension"))
        {
            public void actionPerformed(ActionEvent e) {
                addVirtualCubeDimension(e);
            }
        };
        addVirtualCubeMeasure = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCubeMeasure.title",
                "Add Virtual Cube Measure"))
        {
            public void actionPerformed(ActionEvent e) {
                addVirtualCubeMeasure(e);
            }
        };

        addAggPattern = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregatePattern.title",
                "Add Aggregate Pattern"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggPattern(e);
            }
        };
        addAggExclude = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateExcludeTable.title",
                "Add Aggregate Exclude Table"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggExclude(e);
            }
        };
        addAggName = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateName.title", "Add Aggregate Name"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggName(e);
            }
        };
        addAggIgnoreColumn = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateIgnoreColumn.title",
                "Add Aggregate Ignore Column"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggIgnoreColumn(e);
            }
        };
        addAggForeignKey = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateForeignKey.title",
                "Add Aggregate Foreign Key"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggForeignKey(e);
            }
        };
        addAggMeasure = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateMeasure.title",
                "Add Aggregate Measure"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggMeasure(e);
            }
        };
        addAggLevel = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateLevel.title",
                "Add Aggregate Level"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggLevel(e);
            }
        };
        addAggLevelProperty = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateLevelProperty.title",
                "Add Aggregate Level Property"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggLevelProperty(e);
            }
        };
        addAggFactCount = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.addAggregateFactCount.title",
                "Add Aggregate Fact Count"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggFactCount(e);
            }
        };
        addAggMeasureFactCount = new AbstractAction(
                getResourceConverter().getString(
                        "schemaExplorer.addAggregateMeasureFactCount.title",
                        "Add Aggregate Measure Fact Count"))
        {
            public void actionPerformed(ActionEvent e) {
                addAggMeasureFactCount(e);
            }
        };

        delete = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.actionDelete.title", "Delete"))
        {
            public void actionPerformed(ActionEvent e) {
                delete(e);
            }
        };

        editMode = new AbstractAction(
            getResourceConverter().getString(
                "schemaExplorer.actionEdit.title", "EditMode"))
        {
            public void actionPerformed(ActionEvent e) {
                editMode(e);
            }
        };

        // ========================================================
        // toolbar buttons
        // ========================================================
        addCubeButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addCube"))));
        addCubeButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addCube.title", "Add cube"));
        addCubeButton.addActionListener(addCube);

        addDimensionButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addDimension"))));
        addDimensionButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addDimension.title", "Add Dimension"));
        addDimensionButton.addActionListener(addDimension);

        addDimensionUsageButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addDimensionUsage"))));
        addDimensionUsageButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addDimensionUsage.title",
                "Add Dimension Usage"));
        addDimensionUsageButton.addActionListener(addDimensionUsage);

        addHierarchyButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addHierarchy"))));
        addHierarchyButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addHierarchy.title", "Add Hierarchy"));
        addHierarchyButton.addActionListener(addHierarchy);

        addNamedSetButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addNamedSet"))));
        addNamedSetButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addNamedSet.title", "Add Named Set"));
        addNamedSetButton.addActionListener(addNamedSet);

        addUserDefinedFunctionButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addUserDefinedFunction"))));
        addUserDefinedFunctionButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addUserDefinedFunction.title",
                "Add User defined Function"));
        addUserDefinedFunctionButton.addActionListener(addUserDefinedFunction);

        addCalculatedMemberButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addCalculatedMember"))));
        addCalculatedMemberButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addCalculatedMember.title",
                "Add Calculated Member"));
        addCalculatedMemberButton.addActionListener(addCalculatedMember);

        addMeasureButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addMeasure"))));
        addMeasureButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addMeasure.title", "Add Measure"));
        addMeasureButton.addActionListener(addMeasure);

        addLevelButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addLevel"))));
        addLevelButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addLevel.title", "Add Level"));
        addLevelButton.addActionListener(addLevel);

        addPropertyButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addProperty"))));
        addPropertyButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addProperty.title", "Add Property"));
        addPropertyButton.addActionListener(addProperty);

        addCalculatedMemberPropertyButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addCalculatedMemberProperty"))));
        addCalculatedMemberPropertyButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addCalculatedMemberProperty.title",
                "Add Calculated Member Property"));
        addCalculatedMemberPropertyButton.addActionListener(
            addCalculatedMemberProperty);

        addVirtualCubeButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addVirtualCube"))));
        addVirtualCubeButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCube.title", "Add Virtual Cube"));
        addVirtualCubeButton.addActionListener(addVirtualCube);

        addVirtualCubeDimensionButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addVirtualCubeDimension"))));
        addVirtualCubeDimensionButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCubeDimension.title",
                "Add Virtual Dimension"));
        addVirtualCubeDimensionButton
            .addActionListener(addVirtualCubeDimension);

        addVirtualCubeMeasureButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference(
                        "addVirtualCubeMeasure"))));
        addVirtualCubeMeasureButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addVirtualCubeMeasure.title",
                "Add Virtual Measure"));
        addVirtualCubeMeasureButton.addActionListener(addVirtualCubeMeasure);

        addRoleButton.setIcon(
            new ImageIcon(
                myClassLoader.getResource(
                    getResourceConverter().getGUIReference("addRole"))));
        addRoleButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.addRole.title", "Add Role"));
        addRoleButton.addActionListener(addRole);

        cutButton.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("cut"))));
        cutButton.setText("");
        cutButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.actionCut.title", "Cut"));

        copyButton.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("copy"))));
        copyButton.setText("");
        copyButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.actionCopy.title", "Copy"));

        pasteButton.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("paste"))));
        pasteButton.setText("");
        pasteButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.actionPaste.title", "Paste"));

        deleteButton.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("delete"))));
        deleteButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.actionDelete.title", "Delete"));
        deleteButton.addActionListener(delete);

        editModeButton.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("edit"))));
        editModeButton.setToolTipText(
            getResourceConverter().getString(
                "schemaExplorer.actionEdit.title", "Edit Mode"));
        editModeButton.addActionListener(editMode);

        databaseLabel.setIcon(
            new javax.swing.ImageIcon(
                getClass().getResource(
                    getResourceConverter().getGUIReference("database"))));


        jToolBar1.add(addCubeButton);
        jToolBar1.add(addDimensionButton);
        jToolBar1.add(addDimensionUsageButton);
        jToolBar1.add(addHierarchyButton);
        jToolBar1.add(addNamedSetButton);
        jToolBar1.add(addUserDefinedFunctionButton);
        jToolBar1.add(addCalculatedMemberButton);
        jToolBar1.add(addMeasureButton);
        jToolBar1.add(addLevelButton);
        jToolBar1.add(addPropertyButton);
        jToolBar1.add(addCalculatedMemberPropertyButton);
        jToolBar1.addSeparator();
        jToolBar1.add(addVirtualCubeButton);
        jToolBar1.add(addVirtualCubeDimensionButton);
        jToolBar1.add(addVirtualCubeMeasureButton);
        jToolBar1.addSeparator();
        jToolBar1.add(addRoleButton);
        jToolBar1.addSeparator();

        jToolBar1.add(cutButton);
        jToolBar1.add(copyButton);
        jToolBar1.add(pasteButton);
        jToolBar1.addSeparator();
        jToolBar1.add(deleteButton);
        jToolBar1.addSeparator();
        jToolBar1.add(editModeButton);

        // ========================================================
        // popup menu
        // ========================================================
        jPopupMenu = new CustomJPopupMenu();

        // ========================================================
        // tree mouse listener
        // ========================================================
        tree.addMouseListener(new PopupTrigger());
        tree.addKeyListener(
            new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
//                    keytext=Delete
//                    keycode=127
//                    keytext=NumPad .
//                    keycode=110
                    int kcode = e.getKeyCode();
                    if (kcode == 127 || kcode == 110) {
                        delete(e);
                    }
                }
            });


        // add footer for connected database
        footer.setLayout(new java.awt.BorderLayout());
        footer.add(databaseLabel, java.awt.BorderLayout.CENTER);

        // ========================================================
        // jpanel
        // ========================================================
        this.add(jSplitPane1, java.awt.BorderLayout.CENTER);
        this.add(jToolBar1, java.awt.BorderLayout.NORTH);
        this.add(footer, java.awt.BorderLayout.SOUTH);

        updater = new JTreeUpdater(tree);
    }

    protected void arrowButtonUpAction(ActionEvent evt) {
        TreePath tpath = tree.getSelectionPath();
        if (tpath != null) {
            TreePath parentpath = tpath.getParentPath();
            if (parentpath != null) {
                tree.setSelectionPath(parentpath);
                refreshTree(parentpath);
            }
        }
    }

    protected void arrowButtonDownAction(ActionEvent evt) {
        TreePath tpath = tree.getSelectionPath();
        if (tpath != null) {
            Object current = tpath.getLastPathComponent();
            Object child = tree.getModel().getChild(current, 0);
            if (child != null) {
                Object[] treeObjs = new Object[30];
                treeObjs[0] = child;
                treeObjs[1] = current;
                // traverse upward through tree, saving parent nodes
                TreePath parentpath;
                parentpath = tpath.getParentPath();
                int objCnt = 2;
                while (parentpath != null) {
                    Object po = parentpath.getLastPathComponent();
                    treeObjs[objCnt] = po;
                    objCnt += 1;
                    parentpath = parentpath.getParentPath();
                }
                // reverse the array so that schema is first, then the children
                Object[] nodes = new Object[objCnt];
                int loopCnt = objCnt - 1;
                for (int j = 0; j < objCnt; j++) {
                    nodes[j] = treeObjs[loopCnt];
                    loopCnt--;
                }
                TreePath childPath = new TreePath(nodes);
                tree.setSelectionPath(childPath);
                refreshTree(childPath);
            }
        }
    }

    /**
     * Several methods are called, e.g. editCellAt,  to get the focus set in the
     * value column of the specified row.  The attribute column has the
     * parameter name and should not receive focus.
     */
    protected void setTableCellFocus(int row) {
        propertyTable.editCellAt(row, 1);
        TableCellEditor editor = propertyTable.getCellEditor(row, 1);
        Component comp = editor.getTableCellEditorComponent(
            propertyTable, propertyTable.getValueAt(row, 1), true, row, 1);
    }

    protected void addCube(ActionEvent evt) {
        DrianmonGuiDef.Schema schema =
            (DrianmonGuiDef.Schema) tree.getModel().getRoot();
        DrianmonGuiDef.Cube cube = new DrianmonGuiDef.Cube();

        cube.name = "";

        cube.dimensions = new DrianmonGuiDef.Dimension[0];
        cube.measures = new DrianmonGuiDef.Measure[0];

        cube.calculatedMembers = new DrianmonGuiDef.CalculatedMember[0];
        cube.namedSets = new DrianmonGuiDef.NamedSet[0];

        cube.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newCube.title",
                    "New Cube"),
                schema.cubes);
        cube.cache = Boolean.TRUE;
        cube.enabled = Boolean.TRUE;
        cube.visible = Boolean.TRUE;
        NodeDef[] temp = schema.cubes;
        schema.cubes = new DrianmonGuiDef.Cube[temp.length + 1];
        for (int _i = 0; _i < temp.length; _i++) {
            schema.cubes[_i] = (DrianmonGuiDef.Cube) temp[_i];
        }
        schema.cubes[schema.cubes.length - 1] = cube;

        tree.setSelectionPath(
            (new TreePath(model.getRoot())).pathByAddingChild(
                cube));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addParameter(ActionEvent evt) {
        DrianmonGuiDef.Schema schema =
            (DrianmonGuiDef.Schema) tree.getModel().getRoot();
        DrianmonGuiDef.Parameter parameter = new DrianmonGuiDef.Parameter();

        parameter.name = "";

        // set the required fields
        parameter.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newParameter.title", "New Parameter"),
                schema.parameters);
        // set the default values
        parameter.type = "String";
        parameter.modifiable = Boolean.TRUE;

        NodeDef[] temp = schema.parameters;
        schema.parameters = new DrianmonGuiDef.Parameter[temp.length + 1];
        for (int _i = 0; _i < temp.length; _i++) {
            schema.parameters[_i] = (DrianmonGuiDef.Parameter) temp[_i];
        }
        schema.parameters[schema.parameters.length - 1] = parameter;

        tree.setSelectionPath(
            (new TreePath(model.getRoot())).pathByAddingChild(
                parameter));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addRole(ActionEvent evt) {
        DrianmonGuiDef.Schema schema =
            (DrianmonGuiDef.Schema) tree.getModel().getRoot();
        DrianmonGuiDef.Role role = new DrianmonGuiDef.Role();

        role.name = "";

        role.schemaGrants = new DrianmonGuiDef.SchemaGrant[0];

        // add cube to schema
        role.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newRole.title", "New Role"),
                schema.roles);
        NodeDef[] temp = schema.roles;
        schema.roles = new DrianmonGuiDef.Role[temp.length + 1];
        for (int _i = 0; _i < temp.length; _i++) {
            schema.roles[_i] = (DrianmonGuiDef.Role) temp[_i];
        }
        schema.roles[schema.roles.length - 1] = role;

        tree.setSelectionPath(
            (new TreePath(model.getRoot())).pathByAddingChild(
                role));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addVirtualCube(ActionEvent evt) {
        DrianmonGuiDef.Schema schema =
            (DrianmonGuiDef.Schema) tree.getModel().getRoot();
        DrianmonGuiDef.VirtualCube cube = new DrianmonGuiDef.VirtualCube();

        cube.name = "";
        cube.dimensions = new DrianmonGuiDef.VirtualCubeDimension[0];
        cube.measures = new DrianmonGuiDef.VirtualCubeMeasure[0];
        cube.calculatedMembers = new DrianmonGuiDef.CalculatedMember[0];
        cube.enabled = Boolean.TRUE;

        // add cube to schema
        cube.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newVirtualCube.title",
                    "New Virtual Cube"),
                schema.virtualCubes);
        NodeDef[] temp = schema.virtualCubes;
        schema.virtualCubes = new DrianmonGuiDef.VirtualCube[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            schema.virtualCubes[i] = (DrianmonGuiDef.VirtualCube) temp[i];
        }
        schema.virtualCubes[schema.virtualCubes.length - 1] = cube;

        tree.setSelectionPath(
            new TreePath(model.getRoot()).pathByAddingChild(
                cube));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addUserDefinedFunction(ActionEvent evt) {
        DrianmonGuiDef.Schema schema =
            (DrianmonGuiDef.Schema) tree.getModel().getRoot();
        DrianmonGuiDef.UserDefinedFunction udf =
            new DrianmonGuiDef.UserDefinedFunction();
        udf.name = "";
        udf.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newUserDefinedFunction.title",
                    "New User defined Function"),
                schema.userDefinedFunctions);
        NodeDef[] temp = schema.userDefinedFunctions;
        schema.userDefinedFunctions =
            new DrianmonGuiDef.UserDefinedFunction[temp.length + 1];
        for (int _i = 0; _i < temp.length; _i++) {
            schema.userDefinedFunctions[_i] =
                (DrianmonGuiDef.UserDefinedFunction) temp[_i];
        }
        schema.userDefinedFunctions[schema.userDefinedFunctions.length - 1] =
            udf;
        tree.setSelectionPath(
            new TreePath(model.getRoot()).pathByAddingChild(
                udf));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    /**
     * Updates the tree display after an Add / Delete operation.
     */
    private void refreshTree(TreePath path) {
        setDirty(true);
        if (!dirtyFlag) {
            setDirtyFlag(true);   // dirty indication shown on title
            setTitle();
        }
        updater.update();
        tree.scrollPathToVisible(path);
    }

    protected void addMeasure(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Cube) {
                    path = p;
                    break;
                }
            }
        }
        // Object path = tree.getSelectionPath().getLastPathComponent();
        if (!(path instanceof DrianmonGuiDef.Cube)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeNotSelected.alert",
                    "Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Cube cube = (DrianmonGuiDef.Cube) path;
        DrianmonGuiDef.Measure measure = new DrianmonGuiDef.Measure();
        measure.name = "";
        measure.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newMeasure.title",
                    "New Measure"),
                cube.measures);
        measure.visible = Boolean.TRUE;
        measure.aggregator = "distinct-count";
        NodeDef[] temp = cube.measures;
        cube.measures = new DrianmonGuiDef.Measure[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cube.measures[i] = (DrianmonGuiDef.Measure) temp[i];
        }

        cube.measures[cube.measures.length - 1] = measure;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(measure));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggPattern(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Table) {
                    if (((parentIndex - 1) >= 0)
                        && (tpath.getPathComponent(parentIndex - 1)
                        instanceof DrianmonGuiDef.Cube))
                    {
                        path = p;
                        break;
                    }
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.Table)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeFactTableNotSelected.alert",
                    "Cube Fact Table not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Table factTable = (DrianmonGuiDef.Table) path;

        DrianmonGuiDef.AggPattern aggname = new DrianmonGuiDef.AggPattern();
        aggname.pattern = "";

        // add cube to schema
        aggname.ignorecase = Boolean.TRUE;
        aggname.factcount = null;
        aggname.measuresfactcount = new DrianmonGuiDef.AggMeasureFactCount[0];
        aggname.ignoreColumns = new DrianmonGuiDef.AggIgnoreColumn[0];
        aggname.foreignKeys = new DrianmonGuiDef.AggForeignKey[0];
        aggname.measures = new DrianmonGuiDef.AggMeasure[0];
        aggname.levels = new DrianmonGuiDef.AggLevel[0];
        aggname.excludes = new DrianmonGuiDef.AggExclude[0];

        NodeDef[] temp = factTable.aggTables;
        factTable.aggTables = new DrianmonGuiDef.AggTable[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            factTable.aggTables[i] = (DrianmonGuiDef.AggTable) temp[i];
        }

        factTable.aggTables[factTable.aggTables.length - 1] = aggname;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggname));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggName(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Table) {
                    if (((parentIndex - 1) >= 0)
                        && tpath.getPathComponent(parentIndex - 1)
                        instanceof DrianmonGuiDef.Cube)
                    {
                        path = p;
                        break;
                    }
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.Table)) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.tableNotSelected.alert",
                    "Table not selected."), alert, JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Table factTable = (DrianmonGuiDef.Table) path;

        DrianmonGuiDef.AggName aggname = new DrianmonGuiDef.AggName();
        aggname.name = "";

        // add cube to schema
        aggname.ignorecase = Boolean.TRUE;
        aggname.factcount = null;
        aggname.measuresfactcount = new DrianmonGuiDef.AggMeasureFactCount[0];
        aggname.ignoreColumns = new DrianmonGuiDef.AggIgnoreColumn[0];
        aggname.foreignKeys = new DrianmonGuiDef.AggForeignKey[0];
        aggname.measures = new DrianmonGuiDef.AggMeasure[0];
        aggname.levels = new DrianmonGuiDef.AggLevel[0];

        NodeDef[] temp = factTable.aggTables;
        if (temp == null) {
            factTable.aggTables =
                new DrianmonGuiDef.AggTable[1];
        } else {
            factTable.aggTables =
                new DrianmonGuiDef.AggTable[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                factTable.aggTables[i] = (DrianmonGuiDef.AggTable) temp[i];
            }
        }

        factTable.aggTables[factTable.aggTables.length - 1] = aggname;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggname));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggExclude(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                // aggexcludes can be added to cube fact table or
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Table) {
                    if (((parentIndex - 1) >= 0)
                        && tpath.getPathComponent(parentIndex - 1)
                        instanceof DrianmonGuiDef.Cube)
                    {
                        path = p;
                        break;
                    }
                } else if (p instanceof DrianmonGuiDef.AggPattern) {
                    // aggexcludes can also be added to aggregate patterns
                    path = p;
                    break;
                }
            }
        }
        // Object path = tree.getSelectionPath().getLastPathComponent();
        if (!(path instanceof DrianmonGuiDef.Table
              || path instanceof DrianmonGuiDef.AggPattern))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeFactTableOrAggPatternNotSelected.alert",
                    "Cube Fact Table or Aggregate Pattern not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.AggExclude aggexclude = new DrianmonGuiDef.AggExclude();
        aggexclude.pattern = "";

        aggexclude.ignorecase = Boolean.TRUE;

        if (path instanceof DrianmonGuiDef.Table) {
            DrianmonGuiDef.Table parent =
                (DrianmonGuiDef.Table) path;  // fact table

            NodeDef[] temp = parent.aggExcludes;
            parent.aggExcludes = new DrianmonGuiDef.AggExclude[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                parent.aggExcludes[i] = (DrianmonGuiDef.AggExclude) temp[i];
            }

            parent.aggExcludes[parent.aggExcludes.length - 1] = aggexclude;
        } else {
            DrianmonGuiDef.AggPattern parent =
                (DrianmonGuiDef.AggPattern) path;  // aggpattern
            NodeDef[] temp = parent.excludes;
            parent.excludes = new DrianmonGuiDef.AggExclude[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                parent.excludes[i] = (DrianmonGuiDef.AggExclude) temp[i];
            }

            parent.excludes[parent.excludes.length - 1] = aggexclude;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggexclude));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggIgnoreColumn(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.AggTable)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateTableNotSelected.alert",
                    "Aggregate Table not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.AggTable aggTable = (DrianmonGuiDef.AggTable) path;

        DrianmonGuiDef.AggIgnoreColumn aggicol =
            new DrianmonGuiDef.AggIgnoreColumn();
        aggicol.column = "";

        NodeDef[] temp = aggTable.ignoreColumns;
        aggTable.ignoreColumns =
            new DrianmonGuiDef.AggIgnoreColumn[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            aggTable.ignoreColumns[i] =
                (DrianmonGuiDef.AggIgnoreColumn) temp[i];
        }

        aggTable.ignoreColumns[aggTable.ignoreColumns.length - 1] = aggicol;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggicol));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggForeignKey(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.AggTable)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateTableNotSelected.alert",
                    "Aggregate Table not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.AggTable aggTable = (DrianmonGuiDef.AggTable) path;

        DrianmonGuiDef.AggForeignKey aggfkey =
            new DrianmonGuiDef.AggForeignKey();

        NodeDef[] temp = aggTable.foreignKeys;
        aggTable.foreignKeys =
            new DrianmonGuiDef.AggForeignKey[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            aggTable.foreignKeys[i] = (DrianmonGuiDef.AggForeignKey) temp[i];
        }

        aggTable.foreignKeys[aggTable.foreignKeys.length - 1] = aggfkey;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggfkey));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggMeasure(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.AggTable)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateTableNotSelected.alert",
                    "Aggregate Table not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.AggTable aggTable = (DrianmonGuiDef.AggTable) path;

        DrianmonGuiDef.AggMeasure aggmeasure = new DrianmonGuiDef.AggMeasure();

        NodeDef[] temp = aggTable.measures;
        aggTable.measures = new DrianmonGuiDef.AggMeasure[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            aggTable.measures[i] = (DrianmonGuiDef.AggMeasure) temp[i];
        }

        aggTable.measures[aggTable.measures.length - 1] = aggmeasure;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggmeasure));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggLevel(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.AggTable)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateTableNotSelected.alert",
                    "Aggregate Table not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.AggTable aggTable = (DrianmonGuiDef.AggTable) path;

        DrianmonGuiDef.AggLevel agglevel = new DrianmonGuiDef.AggLevel();

        NodeDef[] temp = aggTable.levels;
        aggTable.levels = new DrianmonGuiDef.AggLevel[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            aggTable.levels[i] = (DrianmonGuiDef.AggLevel) temp[i];
        }

        aggTable.levels[aggTable.levels.length - 1] = agglevel;
        agglevel.collapsed = Boolean.TRUE;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(agglevel));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggLevelProperty(ActionEvent evt) {
        TreePath tpath = getTreePath(evt);
        Object path = null;
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                 parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggLevel) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.AggLevel)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateLevelNotSelected.alert",
                    "Aggregate Level not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        DrianmonGuiDef.AggLevel aggLevel = (DrianmonGuiDef.AggLevel) path;
        DrianmonGuiDef.AggLevelProperty aggLevelProperty =
            new DrianmonGuiDef.AggLevelProperty();

        appendAggLevelProperty(aggLevel, aggLevelProperty);

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggLevelProperty));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    private void appendAggLevelProperty(
        DrianmonGuiDef.AggLevel aggLevel,
        DrianmonGuiDef.AggLevelProperty aggLevelProperty)
    {
        if (aggLevel.properties == null) {
            aggLevel.properties = new DrianmonGuiDef.AggLevelProperty[]
                { aggLevelProperty };
        } else {
            aggLevel.properties = Arrays.copyOf(
                aggLevel.properties,
                aggLevel.properties.length + 1);
            aggLevel.properties[aggLevel.properties.length - 1] =
                aggLevelProperty;
        }
    }

    private TreePath getTreePath(ActionEvent evt) {
        TreePath tpath;
        if (evt.getSource() instanceof Component
            && ((Component)evt.getSource())
            .getParent() instanceof CustomJPopupMenu)
        {
            tpath = ((CustomJPopupMenu)((Component)evt.getSource())
                .getParent()).getPath();
        } else {
            tpath = tree.getSelectionPath();
        }
        return tpath;
    }

    protected void addAggFactCount(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!((path instanceof DrianmonGuiDef.AggName)
              || (path instanceof DrianmonGuiDef.AggPattern)))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.aggregateTableOrAggPatternNotSelected.alert",
                    "Aggregate Table or Aggregate Pattern not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.AggFactCount aggFactCount =
            new DrianmonGuiDef.AggFactCount();
        DrianmonGuiDef.AggTable aggName = null;
        DrianmonGuiDef.AggPattern aggPattern = null;
        if (path instanceof DrianmonGuiDef.AggName) {
            aggName = (DrianmonGuiDef.AggName) path;
            aggName.factcount = new DrianmonGuiDef.AggFactCount();
        } else {
            aggPattern = (DrianmonGuiDef.AggPattern) path;
            aggPattern.factcount = new DrianmonGuiDef.AggFactCount();
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggFactCount));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAggMeasureFactCount(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                 parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.AggTable) {
                    path = p;
                    break;
                }
            }
        }
        if (!((path instanceof DrianmonGuiDef.AggName)
                || (path instanceof DrianmonGuiDef.AggPattern)))
        {
            JOptionPane.showMessageDialog(
                    this,
                    getResourceConverter().getString(
                            "schemaExplorer.aggregateTableOrAggPatternNotSelected.alert",
                            "Aggregate Table or Aggregate Pattern not selected."),
                    alert,
                    JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.AggTable aggTable = (DrianmonGuiDef.AggTable) path;

        DrianmonGuiDef.AggMeasureFactCount aggMeasureFactCount = new DrianmonGuiDef.AggMeasureFactCount();

        NodeDef[] temp = aggTable.measuresfactcount;
        aggTable.measuresfactcount = new DrianmonGuiDef.AggMeasureFactCount[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            aggTable.measuresfactcount[i] = (DrianmonGuiDef.AggMeasureFactCount) temp[i];
        }

        aggTable.measuresfactcount[aggTable.measuresfactcount.length - 1] = aggMeasureFactCount;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(aggMeasureFactCount));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addVirtualCubeMeasure(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.VirtualCube) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.VirtualCube)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.virtualCubeNotSelected.alert",
                    "Virtual Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.VirtualCube cube = (DrianmonGuiDef.VirtualCube) path;

        DrianmonGuiDef.VirtualCubeMeasure measure =
            new DrianmonGuiDef.VirtualCubeMeasure();
        measure.name = "";
        measure.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newVirtualMeasure.title",
                    "New Virtual Measure"),
                cube.measures);
        measure.visible = Boolean.TRUE; // default true

        NodeDef[] temp = cube.measures;
        cube.measures = new DrianmonGuiDef.VirtualCubeMeasure[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cube.measures[i] = (DrianmonGuiDef.VirtualCubeMeasure) temp[i];
        }

        cube.measures[cube.measures.length - 1] = measure;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(measure));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addCalculatedMember(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Cube
                    || p instanceof DrianmonGuiDef.VirtualCube)
                {
                    path = p;
                    break;
                }
            }
        }
        if (!((path instanceof DrianmonGuiDef.Cube)
              || (path instanceof DrianmonGuiDef.VirtualCube)))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeOrVirtualCubeNotSelected.alert",
                    "Cube or Virtual Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Cube cube = null;
        DrianmonGuiDef.VirtualCube vcube = null;

        if (path instanceof DrianmonGuiDef.Cube) {
            cube = (DrianmonGuiDef.Cube) path;
        } else {
            vcube = (DrianmonGuiDef.VirtualCube) path;
        }

        DrianmonGuiDef.CalculatedMember calcmember =
            new DrianmonGuiDef.CalculatedMember();
        calcmember.name = "";
        calcmember.dimension = "Measures";
        calcmember.visible = Boolean.TRUE;  // default value
        calcmember.formatString = "";
        calcmember.formula = "";
        calcmember.formulaElement = new DrianmonGuiDef.Formula();
        calcmember.memberProperties =
            new DrianmonGuiDef.CalculatedMemberProperty[0];

        // add cube to schema
        if (cube != null) {
            calcmember.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newCalculatedMember.title",
                        "New Calculated Member"),
                    cube.calculatedMembers);
            NodeDef[] temp = cube.calculatedMembers;
            cube.calculatedMembers =
                new DrianmonGuiDef.CalculatedMember[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                cube.calculatedMembers[i] =
                    (DrianmonGuiDef.CalculatedMember) temp[i];
            }

            cube.calculatedMembers[cube.calculatedMembers.length - 1] =
                calcmember;
        } else {
            calcmember.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newCalculatedMember.title",
                        "New Calculated Member"),
                    vcube.calculatedMembers);
            NodeDef[] temp = vcube.calculatedMembers;
            vcube.calculatedMembers =
                new DrianmonGuiDef.CalculatedMember[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                vcube.calculatedMembers[i] =
                    (DrianmonGuiDef.CalculatedMember) temp[i];
            }

            vcube.calculatedMembers[vcube.calculatedMembers.length - 1] =
                calcmember;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(calcmember));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected boolean editMode(EventObject evt) {
        // toggle edit mode between xml or properties table form
        editModeXML = !isEditModeXML();

        editModeButton.setSelected(isEditModeXML());
        if (isEditModeXML()) {
            jSplitPane1.setRightComponent(jPanelXML);
        } else {
            jSplitPane1.setRightComponent(jPanel1);
        }
        // update the workbench view menu
        Component o = parentIFrame.getDesktopPane().getParent();
        while (o != null) {
            if (o.getClass() == Workbench.class) {
                ((Workbench) o).getViewXmlMenuItem().setSelected(editModeXML);
                break;
            }
            o = o.getParent();
        }
        return isEditModeXML();
    }

    protected void delete(EventObject evt) {
        // delete the selected schema object
        TreePath tpath = null;
        Object path = null;
        if (evt.getSource() instanceof Component
            && ((Component)evt.getSource())
                .getParent() instanceof CustomJPopupMenu)
        {
            tpath = ((CustomJPopupMenu)((Component)evt.getSource())
                    .getParent()).getPath();
        } else {
            tpath = tree.getSelectionPath();
        }
        if (tpath == null) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.objectToDeleteNotSelected.alert",
                    "Object to delete in Schema not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }
        delete(tpath);
    }

    void delete(TreePath tpath) {
        Object child = tpath.getLastPathComponent(); // to be deleted
        Object nextSibling = null;
        Object prevSibling = null;
        Object parent = null;
        Object grandparent = null;
        boolean grandparentAsSibling = false;

        for (int i = tpath.getPathCount() - 1 - 1; i >= 0; i--) {
            // get parent path
            parent = tpath.getPathComponent(i);
            if (tpath.getPathCount() - 3 > 0) {
                // get parent path
                grandparent = tpath.getPathComponent(i - 1);
            }
            break;
        }
        if (parent == null) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cantDeleteObject.alert",
                    "Schema object cannot be deleted."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        boolean tofind = true;

        Field[] fs = parent.getClass().getFields();
        for (int i = 0; i < fs.length; i++) {
            if (fs[i].getType().isArray()
                && (fs[i].getType().getComponentType().isInstance(child)))
            {
                try {
                    // get the parent's array of child objects
                    Object parentArr = fs[i].get(parent);
                    int parentArrLen = Array.getLength(parentArr);
                    Object newArr =
                        Array.newInstance(
                            fs[i].getType().getComponentType(),
                            parentArrLen - 1);
                    tofind = true;
                    for (int k = 0, m = 0; k < parentArrLen; k++) {
                        Object match = Array.get(parentArr, k);

                        if (tofind && match.equals(child)) {
                            if (child instanceof DrianmonGuiDef.CubeDimension) {
                                // Check equality of parent class attributes for
                                // a special case when child is an object of
                                // CubeDimensions
                                DrianmonGuiDef.CubeDimension matchDim =
                                    (DrianmonGuiDef.CubeDimension) match;
                                DrianmonGuiDef.CubeDimension childDim =
                                    (DrianmonGuiDef.CubeDimension) child;

                                if (eq(matchDim.name, childDim.name)
                                    && eq(matchDim.caption, childDim.caption)
                                    && eq(
                                        matchDim.foreignKey,
                                        childDim.foreignKey))
                                {
                                    tofind = false;
                                    if (k + 1 < parentArrLen) {
                                        nextSibling = Array.get(
                                            parentArr, k + 1);
                                    }
                                    if (k - 1 >= 0) {
                                        prevSibling = Array.get(
                                            parentArr, k - 1);
                                    }
                                    continue;
                                }
                            } else {
                                // other cases require no such check
                                tofind = false;
                                if (k + 1 < parentArrLen) {
                                    nextSibling = Array.get(parentArr, k + 1);
                                }
                                if (k - 1 >= 0) {
                                    prevSibling = Array.get(parentArr, k - 1);
                                }
                                continue;
                            }
                        }
                        Array.set(newArr, m++, match);
                    }
                    // After deletion check before the saving the new array in
                    // parent.  Check for min 1 SQL object(child) in (parent)
                    // expression for (grandparent) level.  If 1 or more, save
                    // the newarray in parent, otherwise delete parent from
                    // grandparent.
                    if ((child instanceof DrianmonGuiDef.SQL)
                        && (parent instanceof DrianmonGuiDef.ExpressionView)
                        && (Array.getLength(newArr) < 1))
                    {
                        if (parent instanceof DrianmonGuiDef.KeyExpression) {
                            ((DrianmonGuiDef.Level) grandparent).keyExp = null;
                        } else if (parent instanceof
                            DrianmonGuiDef.NameExpression)
                        {
                            ((DrianmonGuiDef.Level) grandparent).nameExp = null;
                        } else if (parent
                            instanceof DrianmonGuiDef.OrdinalExpression)
                        {
                            ((DrianmonGuiDef.Level) grandparent).ordinalExp =
                                null;
                        } else if (parent
                            instanceof DrianmonGuiDef.CaptionExpression)
                        {
                            ((DrianmonGuiDef.Level) grandparent).captionExp =
                                null;
                        } else if (parent
                            instanceof DrianmonGuiDef.ParentExpression)
                        {
                            ((DrianmonGuiDef.Level) grandparent).parentExp =
                                null;
                        } else if (parent
                            instanceof DrianmonGuiDef.MeasureExpression)
                        {
                            ((DrianmonGuiDef.Measure) grandparent).measureExp =
                                null;
                        }
                        grandparentAsSibling = true;
                    } else {
                        fs[i].set(parent, newArr);
                    }
                } catch (Exception ex) {
                    // field not found
                }
                break;
            } else if (fs[i].getType().isInstance(child)) {
                // parent's field is an instanceof child object'
                try {
                    if (fs[i].get(parent) == child) {
                        fs[i].set(parent, null);
                        break;
                    }
                } catch (Exception ex) {
                    LOGGER.error("delete", ex);
                    // field not found
                }
            }
        }


        // delete the node from set of expended nodes in JTreeUpdater also
        TreeExpansionEvent e = null;
        e = new TreeExpansionEvent(tree, tpath);
        updater.treeCollapsed(e);

        if (nextSibling != null) {
            tree.setSelectionPath(
                tpath.getParentPath().pathByAddingChild(
                    nextSibling));
        } else if (prevSibling != null) {
            tree.setSelectionPath(
                tpath.getParentPath().pathByAddingChild(
                    prevSibling));
        } else if (grandparentAsSibling) {
            tree.setSelectionPath(tpath.getParentPath().getParentPath());
        } else {
            tree.setSelectionPath(tpath.getParentPath());
        }
        refreshTree(tree.getSelectionPath());
    }

    private boolean eq(
        String o1,
        String o2)
    {
        return o1 == null
            ? o2 == null
            : o1.equals(o2);
    }

    protected void addDimension(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Cube
                    || p instanceof DrianmonGuiDef.Schema)
                {
                    path = p;
                    break;
                }
            }
        }

        if (!((path instanceof DrianmonGuiDef.Cube)
              || (path instanceof DrianmonGuiDef.Schema)))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeOrSchemaNotSelected.alert",
                    "Cube or Schema not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Cube cube = null;
        DrianmonGuiDef.Schema schema = null;

        if (path instanceof DrianmonGuiDef.Cube) {
            cube = (DrianmonGuiDef.Cube) path;
        } else {
            schema = (DrianmonGuiDef.Schema) path;
        }

        DrianmonGuiDef.Dimension dimension = new DrianmonGuiDef.Dimension();
        dimension.name = "";
        dimension.visible = Boolean.TRUE;
        dimension.type = "StandardDimension";    // default dimension type
        dimension.hierarchies = new DrianmonGuiDef.Hierarchy[1];
        dimension.hierarchies[0] = new DrianmonGuiDef.Hierarchy();
        dimension.hierarchies[0].name =
            getResourceConverter().getString(
                "schemaExplorer.newHierarchyInTree.title",
                "New Hierarchy 0");
        dimension.hierarchies[0].visible = Boolean.TRUE;
        dimension.hierarchies[0].hasAll = true;
        dimension.hierarchies[0].levels = new DrianmonGuiDef.Level[0];
        dimension.hierarchies[0].memberReaderParameters =
            new DrianmonGuiDef.MemberReaderParameter[0];

        // add cube to schema
        if (cube != null) {
            dimension.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newDimension.title",
                        "New Dimension"),
                    cube.dimensions);
            NodeDef[] temp = cube.dimensions;
            cube.dimensions = new DrianmonGuiDef.CubeDimension[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                cube.dimensions[i] = (DrianmonGuiDef.CubeDimension) temp[i];
            }
            cube.dimensions[cube.dimensions.length - 1] = dimension;
        } else {
            dimension.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newDimension.title",
                        "New Dimension"),
                    schema.dimensions);
            NodeDef[] temp = schema.dimensions;
            schema.dimensions = new DrianmonGuiDef.Dimension[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                schema.dimensions[i] = (DrianmonGuiDef.Dimension) temp[i];
            }

            schema.dimensions[schema.dimensions.length - 1] = dimension;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(dimension));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }


    protected void addVirtualCubeDimension(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.VirtualCube) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.VirtualCube)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.virtualCubeNotSelected.alert",
                    "Virtual Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.VirtualCube cube = (DrianmonGuiDef.VirtualCube) path;

        DrianmonGuiDef.VirtualCubeDimension dimension =
            new DrianmonGuiDef.VirtualCubeDimension();
        dimension.name = "";
        dimension.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newVirtualDimension.title",
                    "New Virtual Dimension"),
                cube.dimensions);
        NodeDef[] temp = cube.dimensions;
        cube.dimensions =
            new DrianmonGuiDef.VirtualCubeDimension[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cube.dimensions[i] = (DrianmonGuiDef.VirtualCubeDimension) temp[i];
        }
        cube.dimensions[cube.dimensions.length - 1] = dimension;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(dimension));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    private String getNewName(String preName, Object[] objs) {
        String newName = "";
        String workName = preName.trim() + " ";

        if (objs != null) {
            int objNo = objs.length;
            try {
                Field f = objs.getClass().getComponentType().getField("name");
                boolean exists;
                do {
                    newName = workName + objNo++;
                    exists = existsWithFieldValue(objs, newName, f);
                } while (exists);
            } catch (Exception ex) {
                LOGGER.error("getNewName", ex);
            }
        } else {
            newName = workName + 0;
        }
        return newName;
    }

    private static boolean existsWithFieldValue(
        Object[] objs,
        String seek,
        Field f)
        throws IllegalAccessException
    {
        for (int i = 0; i < objs.length; i++) {
            String value = (String) f.get(objs[i]);
            if (seek.equals(value)) {
                return true;
            }
        }
        return false;
    }

    protected void addNamedSet(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Cube
                    || p instanceof DrianmonGuiDef.Schema)
                {
                    path = p;
                    break;
                }
            }
        }

        if (!((path instanceof DrianmonGuiDef.Cube)
              || (path instanceof DrianmonGuiDef.Schema)))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeOrSchemaNotSelected.alert",
                    "Cube or Schema not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }


        DrianmonGuiDef.Cube cube = null;
        DrianmonGuiDef.Schema schema = null;

        if (path instanceof DrianmonGuiDef.Cube) {
            cube = (DrianmonGuiDef.Cube) path;
        } else {
            schema = (DrianmonGuiDef.Schema) path;
        }

        DrianmonGuiDef.NamedSet namedset = new DrianmonGuiDef.NamedSet();
        namedset.name = "";
        namedset.formula = "";
        namedset.formulaElement = new DrianmonGuiDef.Formula();

        // add cube to schema
        if (cube != null) {
            namedset.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newNamedSet.title",
                        "New Named Set"),
                    cube.namedSets);

            NodeDef[] temp = cube.namedSets;
            cube.namedSets = new DrianmonGuiDef.NamedSet[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                cube.namedSets[i] = (DrianmonGuiDef.NamedSet) temp[i];
            }

            cube.namedSets[cube.namedSets.length - 1] = namedset;
        } else {
            namedset.name =
                getNewName(
                    getResourceConverter().getString(
                        "schemaExplorer.newNamedSet.title",
                        "New Named Set"),
                    schema.namedSets);
            NodeDef[] temp = schema.namedSets;
            schema.namedSets = new DrianmonGuiDef.NamedSet[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                schema.namedSets[i] = (DrianmonGuiDef.NamedSet) temp[i];
            }

            schema.namedSets[schema.namedSets.length - 1] = namedset;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(namedset));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addDimensionUsage(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Cube) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.Cube)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeNotSelected.alert",
                    "Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Cube cube = (DrianmonGuiDef.Cube) path;

        DrianmonGuiDef.DimensionUsage dimension =
            new DrianmonGuiDef.DimensionUsage();
        dimension.name = "";
        dimension.visible = Boolean.TRUE;
        dimension.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newDimensionUsage.title",
                    "New Dimension Usage"),
                cube.dimensions);
        NodeDef[] temp = cube.dimensions;
        cube.dimensions = new DrianmonGuiDef.CubeDimension[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cube.dimensions[i] = (DrianmonGuiDef.CubeDimension) temp[i];
        }

        cube.dimensions[cube.dimensions.length - 1] = dimension;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(dimension));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addSchemaGrant(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Role) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.Role)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.roleNotSelected.alert",
                    "Role not selected."),
                alert, JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Role role = (DrianmonGuiDef.Role) path;

        DrianmonGuiDef.SchemaGrant schemaGrant =
            new DrianmonGuiDef.SchemaGrant();
        schemaGrant.access = "";
        schemaGrant.cubeGrants = new DrianmonGuiDef.CubeGrant[0];

        // add cube to schema
        NodeDef[] temp = role.schemaGrants;
        role.schemaGrants = new DrianmonGuiDef.SchemaGrant[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            role.schemaGrants[i] = (DrianmonGuiDef.SchemaGrant) temp[i];
        }

        role.schemaGrants[role.schemaGrants.length - 1] = schemaGrant;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(schemaGrant));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addCubeGrant(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.SchemaGrant) {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.SchemaGrant)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.schemaGrantNotSelected.alert",
                    "Schema Grant not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.SchemaGrant schemaGrant =
            (DrianmonGuiDef.SchemaGrant) path;

        DrianmonGuiDef.CubeGrant cubeGrant = new DrianmonGuiDef.CubeGrant();
        cubeGrant.access = "";
        cubeGrant.dimensionGrants = new DrianmonGuiDef.DimensionGrant[0];
        cubeGrant.hierarchyGrants = new DrianmonGuiDef.HierarchyGrant[0];

        // add cube to schema
        NodeDef[] temp = schemaGrant.cubeGrants;
        schemaGrant.cubeGrants = new DrianmonGuiDef.CubeGrant[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            schemaGrant.cubeGrants[i] = (DrianmonGuiDef.CubeGrant) temp[i];
        }

        schemaGrant.cubeGrants[schemaGrant.cubeGrants.length - 1] = cubeGrant;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(cubeGrant));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addDimensionGrant(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.CubeGrant) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.CubeGrant)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeGrantNotSelected.alert",
                    "Cube Grant not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.CubeGrant cubeGrant = (DrianmonGuiDef.CubeGrant) path;

        DrianmonGuiDef.DimensionGrant dimeGrant =
            new DrianmonGuiDef.DimensionGrant();
        dimeGrant.access = "";

        // add cube to schema
        NodeDef[] temp = cubeGrant.dimensionGrants;
        cubeGrant.dimensionGrants =
            new DrianmonGuiDef.DimensionGrant[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cubeGrant.dimensionGrants[i] =
                (DrianmonGuiDef.DimensionGrant) temp[i];
        }

        cubeGrant.dimensionGrants[cubeGrant.dimensionGrants.length - 1] =
            dimeGrant;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(dimeGrant));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addHierarchyGrant(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.CubeGrant) {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.CubeGrant)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.cubeGrantNotSelected.alert",
                    "Cube Grant not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.CubeGrant cubeGrant = (DrianmonGuiDef.CubeGrant) path;

        DrianmonGuiDef.HierarchyGrant hieGrant =
            new DrianmonGuiDef.HierarchyGrant();
        hieGrant.access = "";
        hieGrant.memberGrants = new DrianmonGuiDef.MemberGrant[0];

        // add cube to schema
        NodeDef[] temp = cubeGrant.hierarchyGrants;
        cubeGrant.hierarchyGrants =
            new DrianmonGuiDef.HierarchyGrant[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            cubeGrant.hierarchyGrants[i] =
                (DrianmonGuiDef.HierarchyGrant) temp[i];
        }

        cubeGrant.hierarchyGrants[cubeGrant.hierarchyGrants.length - 1] =
            hieGrant;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(hieGrant));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addMemberGrant(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.HierarchyGrant) {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.HierarchyGrant)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.hierarchyGrantNotSelected.alert",
                    "Hierarchy Grant not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.HierarchyGrant hieGrant =
            (DrianmonGuiDef.HierarchyGrant) path;

        DrianmonGuiDef.MemberGrant memberGrant =
            new DrianmonGuiDef.MemberGrant();
        memberGrant.access = "";

        // add cube to schema
        NodeDef[] temp = hieGrant.memberGrants;
        hieGrant.memberGrants = new DrianmonGuiDef.MemberGrant[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            hieGrant.memberGrants[i] = (DrianmonGuiDef.MemberGrant) temp[i];
        }

        hieGrant.memberGrants[hieGrant.memberGrants.length - 1] = memberGrant;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(memberGrant));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addAnnotations(ActionEvent evt) {
        TreePath tpath = null;
        tpath = getTreePath(evt);
        Object path = tree.getSelectionPath().getLastPathComponent();
        // Verify that the node selected in the tree is something that supports
        // annotations.
        if (path == null || (!(path instanceof DrianmonGuiDef.Schema)
            && !(path instanceof DrianmonGuiDef.CubeDimension)
            && !(path instanceof DrianmonGuiDef.Cube)
            && !(path instanceof DrianmonGuiDef.VirtualCube)
            && !(path instanceof DrianmonGuiDef.VirtualCubeMeasure)
            && !(path instanceof DrianmonGuiDef.Hierarchy)
            && !(path instanceof DrianmonGuiDef.Level)
            && !(path instanceof DrianmonGuiDef.Measure)
            && !(path instanceof DrianmonGuiDef.CalculatedMember)
            && !(path instanceof DrianmonGuiDef.NamedSet)
            && !(path instanceof DrianmonGuiDef.Role)))
        {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.objectNotSelectedForAnnotations.alert",
                    "Please select an object that supports annotations."),
                    alert, JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Annotations annotations =
            new DrianmonGuiDef.Annotations();
        annotations.array = new DrianmonGuiDef.Annotation[0];
        Class cls = path.getClass();
        try {
            Field field = cls.getField("annotations");
            field.set(path, annotations);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        tree.setSelectionPath(tpath.pathByAddingChild(annotations));

        refreshTree(tree.getSelectionPath());
    }

    protected void addAnnotation(ActionEvent evt) {
        TreePath tpath = null;
        tpath = getTreePath(evt);
        Object path = tree.getSelectionPath().getLastPathComponent();
        if (!(path instanceof DrianmonGuiDef.Annotations)) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.annotationsNotSelected.alert",
                    "Annotations not selected."),
                    alert, JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Annotations annotations =
            (DrianmonGuiDef.Annotations) path;
        DrianmonGuiDef.Annotation annotation = new DrianmonGuiDef.Annotation();
        annotation.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newAnnotation.title",
                    "New Annotation"),
                annotations.array);

        DrianmonGuiDef.Annotation[] temp = annotations.array;
        annotations.array = new DrianmonGuiDef.Annotation[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            annotations.array[i] = (DrianmonGuiDef.Annotation) temp[i];
        }
        annotations.array[annotations.array.length - 1] = annotation;
        tree.setSelectionPath(tpath.pathByAddingChild(annotation));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addLevel(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Hierarchy) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.Hierarchy)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.hierarchyNotSelected.alert",
                    "Hierarchy not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Hierarchy hierarchy = (DrianmonGuiDef.Hierarchy) path;

        DrianmonGuiDef.Level level = new DrianmonGuiDef.Level();
        level.uniqueMembers = false;
        level.visible = Boolean.TRUE;
        level.name = "";
        level.properties = new DrianmonGuiDef.Property[0];
        level.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newLevel.title",
                    "New Level"),
                hierarchy.levels);
        NodeDef[] temp = hierarchy.levels;
        hierarchy.levels = new DrianmonGuiDef.Level[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            hierarchy.levels[i] = (DrianmonGuiDef.Level) temp[i];
        }

        hierarchy.levels[hierarchy.levels.length - 1] = level;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(level));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addSQL(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.ExpressionView
                    || p instanceof DrianmonGuiDef.View)
                {
                    // parent could also be DrianmonGuiDef.Expression?
                    path = p;
                    break;
                }
            }
        }
        if (path == null) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.sqlExpressionNotSelected.alert",
                    "Expression or View for SQL not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.SQL sql = new DrianmonGuiDef.SQL();
        sql.dialect = "generic";

        if (path instanceof DrianmonGuiDef.ExpressionView) {
            DrianmonGuiDef.ExpressionView expview =
                (DrianmonGuiDef.ExpressionView) path;
            // add sql to ExpressionView
            NodeDef[] temp = expview.expressions;
            expview.expressions = new DrianmonGuiDef.SQL[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                expview.expressions[i] = (DrianmonGuiDef.SQL) temp[i];
            }

            expview.expressions[expview.expressions.length - 1] = sql;
        } else {
            // Its a View
            DrianmonGuiDef.View view = (DrianmonGuiDef.View) path;
            // add sql to ExpressionView
            NodeDef[] temp = view.selects;
            view.selects = new DrianmonGuiDef.SQL[temp.length + 1];
            for (int i = 0; i < temp.length; i++) {
                view.selects[i] = (DrianmonGuiDef.SQL) temp[i];
            }

            view.selects[view.selects.length - 1] = sql;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(sql));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addScript(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.UserDefinedFunction
                    || p instanceof DrianmonGuiDef.MemberFormatter
                    || p instanceof DrianmonGuiDef.CellFormatter
                    || p instanceof DrianmonGuiDef.PropertyFormatter)
                {
                    path = p;
                    break;
                }
            }
        }
        if (path == null) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.userDefinedFunctionOrFormatterNotSelected.alert",
                    "User Defined Function or Formatter not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        final DrianmonGuiDef.Script script = new DrianmonGuiDef.Script();

        if (path instanceof DrianmonGuiDef.UserDefinedFunction) {
            final DrianmonGuiDef.UserDefinedFunction parent =
                (DrianmonGuiDef.UserDefinedFunction) path;
            parent.script = script;
        } else if (path instanceof DrianmonGuiDef.CellFormatter) {
            final DrianmonGuiDef.CellFormatter parent =
                (DrianmonGuiDef.CellFormatter) path;
            parent.script = script;
        } else if (path instanceof DrianmonGuiDef.MemberFormatter) {
            final DrianmonGuiDef.MemberFormatter parent =
                (DrianmonGuiDef.MemberFormatter) path;
            parent.script = script;
        } else if (path instanceof DrianmonGuiDef.PropertyFormatter) {
            final DrianmonGuiDef.PropertyFormatter parent =
                (DrianmonGuiDef.PropertyFormatter) path;
            parent.script = script;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(script));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addCellFormatter(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Measure
                    || p instanceof DrianmonGuiDef.CalculatedMember)
                {
                    path = p;
                    break;
                }
            }
        }
        if (path == null) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.measureOrCalculatedMemberNotSelected.alert",
                    "Measure or Calculated Member not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        final DrianmonGuiDef.CellFormatter formatter =
            new DrianmonGuiDef.CellFormatter();

        if (path instanceof DrianmonGuiDef.Measure) {
            final DrianmonGuiDef.Measure parent =
                (DrianmonGuiDef.Measure) path;
            parent.cellFormatter = formatter;
        } else if (path instanceof DrianmonGuiDef.CalculatedMember) {
            final DrianmonGuiDef.CalculatedMember parent =
                (DrianmonGuiDef.CalculatedMember) path;
            parent.cellFormatter = formatter;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(formatter));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    private static class LevelInfo {
       DrianmonGuiDef.Level level = null;
       Object[] parentPathObjs = null;
    }

    protected LevelInfo getSelectedLevel(ActionEvent evt) {
        final LevelInfo info = new LevelInfo();
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;

        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1;
                parentIndex >= 0; parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Level) {
                    path = p;
                    break;
                }
            }
        }
        if (path == null || !(path instanceof DrianmonGuiDef.Level)) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.levelNotSelected.alert",
                    "Level not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return null;
        }
        info.level = (DrianmonGuiDef.Level) path;
        info.parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            info.parentPathObjs[i] = tpath.getPathComponent(i);
        }

        return info;
    }

    protected void addMemberFormatter(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        final DrianmonGuiDef.MemberFormatter formatter =
            new DrianmonGuiDef.MemberFormatter();
        info.level.memberFormatter = formatter;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(formatter));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addPropertyFormatter(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Property) {
                    path = p;
                    break;
                }
            }
        }
        if (path == null) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.propertyNotSelected.alert",
                    "Property not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        final DrianmonGuiDef.PropertyFormatter formatter =
            new DrianmonGuiDef.PropertyFormatter();

        final DrianmonGuiDef.Property parent =
            (DrianmonGuiDef.Property) path;
        parent.propertyFormatter = formatter;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(formatter));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addKeyExp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.KeyExpression keyExp =
            new DrianmonGuiDef.KeyExpression();
        keyExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        keyExp.expressions[0] = new DrianmonGuiDef.SQL();
        keyExp.expressions[0].dialect = "generic";
        keyExp.expressions[0].cdata = "";
        info.level.keyExp = keyExp;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(keyExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addNameExp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.NameExpression nameExp =
            new DrianmonGuiDef.NameExpression();
        nameExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        nameExp.expressions[0] = new DrianmonGuiDef.SQL();
        nameExp.expressions[0].dialect = "generic";
        nameExp.expressions[0].cdata = "";
        info.level.nameExp = nameExp;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(nameExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addOrdinalExp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.OrdinalExpression ordinalExp =
            new DrianmonGuiDef.OrdinalExpression();
        ordinalExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        ordinalExp.expressions[0] = new DrianmonGuiDef.SQL();
        ordinalExp.expressions[0].dialect = "generic";
        ordinalExp.expressions[0].cdata = "";
        info.level.ordinalExp = ordinalExp;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(ordinalExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addCaptionExp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.CaptionExpression captionExp =
            new DrianmonGuiDef.CaptionExpression();
        captionExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        captionExp.expressions[0] = new DrianmonGuiDef.SQL();
        captionExp.expressions[0].dialect = "generic";
        captionExp.expressions[0].cdata = "";
        info.level.captionExp = captionExp;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(captionExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addParentExp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.ParentExpression parentExp =
            new DrianmonGuiDef.ParentExpression();
        parentExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        parentExp.expressions[0] = new DrianmonGuiDef.SQL();
        parentExp.expressions[0].dialect = "generic";
        parentExp.expressions[0].cdata = "";
        info.level.parentExp = parentExp;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(parentExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addMeasureExp(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Measure) {
                    path = p;
                    break;
                }
            }
        }
        if (!(path instanceof DrianmonGuiDef.Measure)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.measureNotSelected.alert",
                    "Measure not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Measure measure = (DrianmonGuiDef.Measure) path;

        DrianmonGuiDef.MeasureExpression measureExp =
            new DrianmonGuiDef.MeasureExpression();
        measureExp.expressions = new DrianmonGuiDef.SQL[1];    // min 1
        measureExp.expressions[0] = new DrianmonGuiDef.SQL();
        measureExp.expressions[0].dialect = "generic";
        measureExp.expressions[0].cdata = "";
        measure.measureExp = measureExp;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(measureExp));

        refreshTree(tree.getSelectionPath());
    }

    protected void addFormula(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.NamedSet
                    || p instanceof DrianmonGuiDef.CalculatedMember)
                {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.NamedSet
              || path instanceof DrianmonGuiDef.CalculatedMember))
        {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.namedsetOrCalcMemberNotSelected.alert",
                    "Named Set or Calculated Member not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Formula formulaElement = new DrianmonGuiDef.Formula();
        formulaElement.cdata = "";

        if (path instanceof DrianmonGuiDef.NamedSet) {
            DrianmonGuiDef.NamedSet ns = (DrianmonGuiDef.NamedSet) path;
            ns.formulaElement = formulaElement;
        } else if (path instanceof DrianmonGuiDef.CalculatedMember) {
            DrianmonGuiDef.CalculatedMember ns =
                (DrianmonGuiDef.CalculatedMember) path;
            ns.formulaElement = formulaElement;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(formulaElement));

        refreshTree(tree.getSelectionPath());
    }

    protected void addTable(ActionEvent evt) {
        DrianmonGuiDef.RelationOrJoin relation = new DrianmonGuiDef.Table(
            "", "Table", "", null);
        addRelation(evt, relation);
    }

    protected void addJoin(ActionEvent evt) {
        DrianmonGuiDef.RelationOrJoin relation = new DrianmonGuiDef.Join(
            "",
            "",
            new DrianmonGuiDef.Table("", "Table 1", "", null),
            "",
            "",
            new DrianmonGuiDef.Table("", "Table 2", "", null));
        addRelation(evt, relation);
    }

    protected void addView(ActionEvent evt) {
        DrianmonGuiDef.View view = new DrianmonGuiDef.View();

        view.alias = "";

        view.selects = new DrianmonGuiDef.SQL[1];

        view.selects[0] = new DrianmonGuiDef.SQL();
        view.selects[0].dialect = "generic";
        view.selects[0].cdata = "";

        addRelation(evt, view);
    }

    protected void addInlineTable(ActionEvent evt) {
        DrianmonGuiDef.InlineTable inlineTable =
            new DrianmonGuiDef.InlineTable();

        inlineTable.alias = "";
        inlineTable.columnDefs = new DrianmonGuiDef.ColumnDefs();
        inlineTable.rows = new DrianmonGuiDef.Rows();

        addRelation(evt, inlineTable);
    }

    protected void addRelation(
        ActionEvent evt, DrianmonGuiDef.RelationOrJoin relation)
    {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Hierarchy
                    || p instanceof DrianmonGuiDef.Cube)
                {
                    path = p;
                    break;
                }
            }
        }

        if (path instanceof DrianmonGuiDef.Hierarchy) {
            DrianmonGuiDef.Hierarchy h = (DrianmonGuiDef.Hierarchy) path;

            // add relation to hierarchy
            h.relation = relation;
        } else if (path instanceof DrianmonGuiDef.Cube) {
            if (!(relation instanceof DrianmonGuiDef.Relation)) {
                JOptionPane.showMessageDialog(
                    this, getResourceConverter().getString(
                        "schemaExplorer.relationOrJoinNotRelation.alert",
                        "Can't add a Join."),
                    alert,
                    JOptionPane.WARNING_MESSAGE);
                return;
            }

            DrianmonGuiDef.Cube cube = (DrianmonGuiDef.Cube) path;

            // add relation to cube
            cube.fact = (DrianmonGuiDef.Relation) relation;
        } else {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.hierarchyOrCubeNotSelected.alert",
                    "Hierarchy or Cube not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(relation));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addHierarchy(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Dimension) {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.Dimension)) {
            JOptionPane.showMessageDialog(
                this,
                getResourceConverter().getString(
                    "schemaExplorer.dimensionNotSelected.alert",
                    "Dimension not selected."),
                alert,
                JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Dimension dimension = (DrianmonGuiDef.Dimension) path;

        DrianmonGuiDef.Hierarchy hierarchy = new DrianmonGuiDef.Hierarchy();

        hierarchy.name = "";
        hierarchy.hasAll = Boolean.TRUE;
        hierarchy.visible = Boolean.TRUE;
        hierarchy.levels = new DrianmonGuiDef.Level[0];
        hierarchy.memberReaderParameters =
            new DrianmonGuiDef.MemberReaderParameter[0];
        hierarchy.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newHierarchy.title",
                    "New Hierarchy"),
                dimension.hierarchies);
        NodeDef[] temp = dimension.hierarchies;
        dimension.hierarchies = new DrianmonGuiDef.Hierarchy[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            dimension.hierarchies[i] = (DrianmonGuiDef.Hierarchy) temp[i];
        }

        dimension.hierarchies[dimension.hierarchies.length - 1] = hierarchy;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(hierarchy));
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }


    protected void moveLevelUp(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.Hierarchy hierarchy =
            (DrianmonGuiDef.Hierarchy)
                info.parentPathObjs[info.parentPathObjs.length - 2];

        int loc = -1;
        for (int i = 0; i < hierarchy.levels.length; i++) {
            if (hierarchy.levels[i] == info.level) {
              loc = i;
              break;
            }
        }

        if (loc > 0) {
          DrianmonGuiDef.Level tmp = hierarchy.levels[loc - 1];
          hierarchy.levels[loc - 1] = info.level;
          hierarchy.levels[loc] = tmp;
        }

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath);
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void moveLevelDown(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.Hierarchy hierarchy =
            (DrianmonGuiDef.Hierarchy)
                info.parentPathObjs[info.parentPathObjs.length - 2];

        int loc = -1;
        for (int i = 0; i < hierarchy.levels.length; i++) {
            if (hierarchy.levels[i] == info.level) {
              loc = i;
              break;
            }
        }

        if (loc < hierarchy.levels.length - 1) {
          DrianmonGuiDef.Level tmp = hierarchy.levels[loc + 1];
          hierarchy.levels[loc + 1] = info.level;
          hierarchy.levels[loc] = tmp;
        }

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath);
        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }


    protected void addProperty(ActionEvent evt) {
        final LevelInfo info = getSelectedLevel(evt);
        if (info == null) {
            return;
        }

        DrianmonGuiDef.Property property = new DrianmonGuiDef.Property();
        property.name = "";

        if (info.level.properties == null) {
            info.level.properties = new DrianmonGuiDef.Property[0];
        }
        property.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newProperty.title",
                    "New Property"),
                info.level.properties);
        NodeDef[] temp = info.level.properties;
        info.level.properties = new DrianmonGuiDef.Property[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            info.level.properties[i] = (DrianmonGuiDef.Property) temp[i];
        }

        info.level.properties[info.level.properties.length - 1] = property;

        TreePath parentPath = new TreePath(info.parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(property));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addCalculatedMemberProperty(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.CalculatedMember
                    || p instanceof DrianmonGuiDef.Measure)
                {
                    path = p;
                    break;
                }
            }
        }
        if (path instanceof DrianmonGuiDef.CalculatedMember) {
            addCalcMemberPropToCalcMember(
                (DrianmonGuiDef.CalculatedMember)path, parentIndex, tpath);
        } else if (path instanceof DrianmonGuiDef.Measure) {
            addCalcMemberPropToMeasure(
                (DrianmonGuiDef.Measure)path, parentIndex, tpath);
        } else {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.calculatedMemberNotSelected.alert",
                    "Calculated Member or Measure not selected."),
                    alert, JOptionPane.WARNING_MESSAGE);
        }
    }

    protected void addCalcMemberPropToCalcMember(
        DrianmonGuiDef.CalculatedMember calcMember,
        int parentIndex,
        TreePath tpath)
    {
        DrianmonGuiDef.CalculatedMemberProperty property =
            new DrianmonGuiDef.CalculatedMemberProperty();
        property.name = "";

        if (calcMember.memberProperties == null) {
            calcMember.memberProperties =
                new DrianmonGuiDef.CalculatedMemberProperty[0];
        }
        property.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newProperty.title",
                    "New Property"),
                calcMember.memberProperties);
        NodeDef[] temp = calcMember.memberProperties;
        calcMember.memberProperties =
            new DrianmonGuiDef.CalculatedMemberProperty[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            calcMember.memberProperties[i] =
                (DrianmonGuiDef.CalculatedMemberProperty) temp[i];
        }

        calcMember.memberProperties[calcMember.memberProperties.length - 1] =
            property;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(property));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addCalcMemberPropToMeasure(
        DrianmonGuiDef.Measure measure,
        int parentIndex,
        TreePath tpath)
    {
        DrianmonGuiDef.CalculatedMemberProperty property =
            new DrianmonGuiDef.CalculatedMemberProperty();
        property.name = "";

        if (measure.memberProperties == null) {
            measure.memberProperties =
                new DrianmonGuiDef.CalculatedMemberProperty[0];
        }
        property.name =
            getNewName(
                getResourceConverter().getString(
                    "schemaExplorer.newProperty.title",
                    "New Property"),
                measure.memberProperties);
        NodeDef[] temp = measure.memberProperties;
        measure.memberProperties =
            new DrianmonGuiDef.CalculatedMemberProperty[temp.length + 1];
        for (int i = 0; i < temp.length; i++) {
            measure.memberProperties[i] =
                (DrianmonGuiDef.CalculatedMemberProperty) temp[i];
        }

        measure.memberProperties[measure.memberProperties.length - 1] =
            property;

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(property));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    protected void addClosure(ActionEvent evt) {
        TreePath tpath = null;
        Object path = null;
        tpath = getTreePath(evt);
        int parentIndex = -1;
        if (tpath != null) {
            for (parentIndex = tpath.getPathCount() - 1; parentIndex >= 0;
                parentIndex--)
            {
                final Object p = tpath.getPathComponent(parentIndex);
                if (p instanceof DrianmonGuiDef.Level) {
                    path = p;
                    break;
                }
            }
        }

        if (!(path instanceof DrianmonGuiDef.Level)) {
            JOptionPane.showMessageDialog(
                this, getResourceConverter().getString(
                    "schemaExplorer.levelNotSelected.alert",
                    "Level not selected."), alert, JOptionPane.WARNING_MESSAGE);
            return;
        }

        DrianmonGuiDef.Level level = (DrianmonGuiDef.Level) path;
        DrianmonGuiDef.Closure closure = new DrianmonGuiDef.Closure();
        closure.parentColumn = "";
        closure.childColumn = "";
        closure.table = new DrianmonGuiDef.Table("", "Table", "", null);
        if (level.closure == null) {
            level.closure = closure;
        }

        Object[] parentPathObjs = new Object[parentIndex + 1];
        for (int i = 0; i <= parentIndex; i++) {
            parentPathObjs[i] = tpath.getPathComponent(i);
        }
        TreePath parentPath = new TreePath(parentPathObjs);
        tree.setSelectionPath(parentPath.pathByAddingChild(closure));

        refreshTree(tree.getSelectionPath());
        setTableCellFocus(0);
    }

    public DrianmonGuiDef.Schema getSchema() {
        return this.schema;
    }

    /**
     * returns the schema file
     *
     * @return File
     */
    public File getSchemaFile() {
        return this.schemaFile;
    }

    /**
     * sets the schema file
     */
    public void setSchemaFile(File f) {
        this.schemaFile = f;
    }

    public Object lastSelected;

    /**
     * Called whenever the value of the selection changes.
     *
     * @param e the event that characterizes the change.
     */
    public void valueChanged(TreeSelectionEvent e) {
        if (propertyTable.isEditing() && (lastSelected != e.getPath()
            .getLastPathComponent()))
        {
            SchemaPropertyCellEditor sce =
                (SchemaPropertyCellEditor) propertyTable.getCellEditor();
            if (sce != null) {
                TreeSelectionEvent e2 = e;
                sce.stopCellEditing();
                e = e2;
            }
        }
        lastSelected = e.getPath().getLastPathComponent();

        String selectedFactTable = null;
        String selectedFactTableSchema = null;

        for (int i = e.getPath().getPathCount() - 1; i >= 0; i--) {
            Object comp = e.getPath().getPathComponent(i);
            if (comp instanceof DrianmonGuiDef.Cube) {
                final DrianmonGuiDef.Cube cube = (DrianmonGuiDef.Cube) comp;
                if (cube.fact instanceof DrianmonGuiDef.Table) {
                    final DrianmonGuiDef.Table table =
                        (DrianmonGuiDef.Table) cube.fact;
                    selectedFactTable = table.name;
                    selectedFactTableSchema = table.schema;
                }
            }
        }
        TreePath tpath = e.getPath();
        Object o = tpath.getLastPathComponent();
        Object po = null;
        // look for parent information
        TreePath parentTpath = tpath.getParentPath();
        String parentName = "";
        String elementName = "";
        if (parentTpath != null) {
            po = parentTpath.getLastPathComponent();
            Class parentClassName = po.getClass();
            try {
                Field nameField = po.getClass().getField("name");
                elementName = (String) nameField.get(po);
                if (elementName == null) {
                    elementName = "";
                } else {
                    elementName = "'" + elementName + "'";
                }
            } catch (Exception ex) {
                elementName = "";
            }
            int pos = parentClassName.toString().lastIndexOf("$");
            if (pos > 0) {
                parentName = parentClassName.toString().substring(pos + 1);
            }
        }

        // Begin : For xml edit mode display
        StringWriter sxml = new StringWriter();
        org.eigenbase.xom.XMLOutput pxml =
            new org.eigenbase.xom.XMLOutput(sxml);
        pxml.setIndentString("    ");
        pxml.setAlwaysQuoteCData(true);

        // End : For xml edit mode display

        String[] pNames = DEF_DEFAULT;

        validStatusLabel.setText(renderer.invalid(tree, e.getPath(), o));
        validStatusLabel2.setText(validStatusLabel.getText());

        if (o instanceof DrianmonGuiDef.Column) {
            pNames = DEF_COLUMN;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.column.title", LBL_COLUMN));
        } else if (o instanceof DrianmonGuiDef.Cube) {
            pNames = DEF_CUBE;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.cube.title", LBL_CUBE));
            ((DrianmonGuiDef.Cube) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Dimension) {
            pNames = DEF_DIMENSION;
            if (po instanceof DrianmonGuiDef.Schema) {
                targetLabel.setText(
                    getResourceConverter().getString(
                        "common.sharedDimension.title", "Shared Dimension"));
            } else {
                targetLabel.setText(
                    getResourceConverter().getFormattedString(
                        "schemaExplorer.dimensionElementParent.title",
                        "Dimension for {0} {1}",
                        elementName,
                        parentName));
            }
            ((DrianmonGuiDef.Dimension) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.DimensionUsage) {
            pNames = DEF_DIMENSION_USAGE;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.dimensionUsageForElement.title",
                    "Dimension Usage for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.DimensionUsage) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.KeyExpression) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.keyExpression.title", LBL_KEY_EXPRESSION));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.NameExpression) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.nameExpression.title", LBL_NAME_EXPRESSION));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.OrdinalExpression) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.ordinalExpression.title", LBL_ORDINAL_EXPRESSION));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.ParentExpression) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.parentExpression.title", LBL_PARENT_EXPRESSION));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.ExpressionView) {
            pNames = DEF_EXPRESSION_VIEW;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.expressionView.title", LBL_EXPRESSION_VIEW));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.MeasureExpression) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.measureExpression.title", LBL_MEASURE_EXPRESSION));
            ((DrianmonGuiDef.ExpressionView) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Hierarchy) {
            pNames = DEF_HIERARCHY;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.hierarchyElementParent.title",
                    "Hierarchy for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Hierarchy) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Join) {
            pNames = DEF_JOIN;
            if (parentName.equalsIgnoreCase("Join")) {
                Object parentJoin = parentTpath.getLastPathComponent();
                int indexOfChild = tree.getModel().getIndexOfChild(
                    parentJoin, o);
                switch (indexOfChild) {
                case 0:
                    targetLabel.setText(
                        getResourceConverter().getString(
                            "common.leftJoin.title", "Left : " + LBL_JOIN));
                    break;
                case 1:
                    targetLabel.setText(
                        getResourceConverter().getString(
                            "common.rightJoin.title", "Right : " + LBL_JOIN));
                }
            } else {
                targetLabel.setText(
                    getResourceConverter().getFormattedString(
                        "schemaExplorer.generalJoinForElement.title",
                        "Join for {0} {1}",
                        elementName,
                        parentName));
            }
            ((DrianmonGuiDef.Join) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Level) {
            pNames = DEF_LEVEL;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.levelForElement.title",
                    "Level for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Level) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Measure) {
            pNames = DEF_MEASURE;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.measureForElement.title",
                    "Measure for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Measure) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.CalculatedMember) {
            pNames = DEF_CALCULATED_MEMBER;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.calculatedMemberForElement.title",
                    "Calculated Member for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.CalculatedMember) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.CalculatedMemberProperty) {
            pNames = DEF_CALCULATED_MEMBER_PROPERTY;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.calculatedMemberProperty.title",
                    LBL_CALCULATED_MEMBER_PROPERTY));
        } else if (o instanceof DrianmonGuiDef.NamedSet) {
            pNames = DEF_NAMED_SET;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.namedSetForElement.title",
                    "Named Set for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.NamedSet) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Formula) {
            pNames = DEF_FORMULA;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.formulaForElement.title",
                    "Formula for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Formula) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.UserDefinedFunction) {
            pNames = DEF_USER_DEFINED_FUNCTION;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.userDefinedFunctionForElement.title",
                    "User Defined Function for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.UserDefinedFunction) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Script) {
            pNames = DEF_SCRIPT;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.scriptForElement.title",
                    "Script for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Script) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.CellFormatter) {
            pNames = DEF_FORMATTER;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.cellFormatterForElement.title",
                    "Cell Formatter for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.CellFormatter) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.PropertyFormatter) {
            pNames = DEF_FORMATTER;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.propertyFormatterForElement.title",
                    "Property Formatter for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.PropertyFormatter) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.MemberFormatter) {
            pNames = DEF_FORMATTER;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.memberFormatterForElement.title",
                    "Member Formatter for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.MemberFormatter) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.MemberReaderParameter) {
            pNames = DEF_PARAMETER;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.parameter.title", LBL_PARAMETER));
        } else if (o instanceof DrianmonGuiDef.Property) {
            pNames = DEF_PROPERTY;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.property.title", LBL_PROPERTY));
            ((DrianmonGuiDef.Property) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Closure) {
            pNames = DEF_CLOSURE;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.closure.title", LBL_CLOSURE));
            ((DrianmonGuiDef.Closure) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Schema) {
            pNames = DEF_SCHEMA;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.schema.title", LBL_SCHEMA));
            ((DrianmonGuiDef.Schema) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.SQL) {
            pNames = DEF_SQL;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.sql.title", LBL_SQL));
            ((DrianmonGuiDef.SQL) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Table) {
            pNames = DEF_TABLE;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.tableForElement.title",
                    "Table for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Table) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggName) {
            pNames = DEF_AGG_NAME;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggName.title", LBL_AGG_NAME));
            ((DrianmonGuiDef.AggName) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggIgnoreColumn) {
            pNames = DEF_AGG_IGNORE_COLUMN;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggIgnoreColumn.title", LBL_AGG_IGNORE_COLUMN));
            ((DrianmonGuiDef.AggIgnoreColumn) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggForeignKey) {
            pNames = DEF_AGG_FOREIGN_KEY;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggForeignKey.title", LBL_AGG_FOREIGN_KEY));
            ((DrianmonGuiDef.AggForeignKey) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggMeasure) {
            pNames = DEF_AGG_MEASURE;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggMeasure.title", LBL_AGG_MEASURE));
            ((DrianmonGuiDef.AggMeasure) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggLevel) {
            pNames = DEF_AGG_LEVEL;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggLevel.title", LBL_AGG_LEVEL));
            ((DrianmonGuiDef.AggLevel) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggLevelProperty) {
            pNames = DEF_AGG_LEVEL_PROP;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggLevelProperty.title", LBL_AGG_LEVEL_PROP));
            ((DrianmonGuiDef.AggLevelProperty) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggExclude) {
            pNames = DEF_AGG_EXCLUDE;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggExclude.title", LBL_AGG_EXCLUDE));
            ((DrianmonGuiDef.AggExclude) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggPattern) {
            pNames = DEF_AGG_PATTERN;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggPattern.title", LBL_AGG_PATTERN));
            ((DrianmonGuiDef.AggPattern) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.AggFactCount) {
            pNames = DEF_AGG_FACT_COUNT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.aggFactCount.title", LBL_AGG_FACT_COUNT));
            ((DrianmonGuiDef.AggFactCount) o).displayXML(pxml, 0);

        } else if (o instanceof DrianmonGuiDef.AggMeasureFactCount) {
            pNames = DEF_AGG_MEASURE_FACT_COUNT;
            targetLabel.setText(
                    getResourceConverter().getString(
                            "common.aggMeasureFactCount.title", LBL_AGG_MEASURE_FACT_COUNT));
            ((DrianmonGuiDef.AggMeasureFactCount) o).displayXML(pxml, 0);

        }
        else if (o instanceof DrianmonGuiDef.View) {
            pNames = DEF_VIEW;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.view.title", LBL_VIEW));

        } else if (o instanceof DrianmonGuiDef.Role) {
            pNames = DEF_ROLE;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.roleElementParent.title",
                    "Role for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.Role) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Parameter) {
            pNames = DEF_PARAMETER_SCHEMA;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.schemaParameter.title", LBL_PARAMETER_SCHEMA));
            ((DrianmonGuiDef.Parameter) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.SchemaGrant) {
            pNames = DEF_SCHEMA_GRANT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.schemaGrant.title", LBL_SCHEMA_GRANT));
            ((DrianmonGuiDef.SchemaGrant) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.CubeGrant) {
            pNames = DEF_CUBE_GRANT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.cubeGrant.title", LBL_CUBE_GRANT));
            ((DrianmonGuiDef.CubeGrant) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.DimensionGrant) {
            pNames = DEF_DIMENSION_GRANT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.dimensionGrant.title", LBL_DIMENSION_GRANT));
            ((DrianmonGuiDef.DimensionGrant) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.HierarchyGrant) {
            pNames = DEF_HIERARCHY_GRANT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.hierarchyGrant.title", LBL_HIERARCHY_GRANT));
            ((DrianmonGuiDef.HierarchyGrant) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.MemberGrant) {
            pNames = DEF_MEMBER_GRANT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.memberGrant.title", LBL_MEMBER_GRANT));
            ((DrianmonGuiDef.MemberGrant) o).displayXML(pxml, 0);

        } else if (o instanceof DrianmonGuiDef.VirtualCube) {
            pNames = DEF_VIRTUAL_CUBE;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.virtualCubeElementParent.title",
                    "Virtual Cube for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.VirtualCube) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.VirtualCubeDimension) {
            pNames = DEF_VIRTUAL_CUBE_DIMENSION;
            targetLabel.setText(
                getResourceConverter().getFormattedString(
                    "schemaExplorer.virtualCubeDimensionElementParent.title",
                    "Virtual Cube Dimension for {0} {1}",
                    elementName,
                    parentName));
            ((DrianmonGuiDef.VirtualCubeDimension) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.VirtualCubeMeasure) {
            pNames = DEF_VIRTUAL_CUBE_MEASURE;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.virtualCubeMeasure.title",
                    LBL_VIRTUAL_CUBE_MEASURE));
            ((DrianmonGuiDef.VirtualCubeMeasure) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Annotations) {
            pNames = DEF_DEFAULT;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.annotations.title", LBL_ANNOTATIONS));
            ((DrianmonGuiDef.Annotations) o).displayXML(pxml, 0);
        } else if (o instanceof DrianmonGuiDef.Annotation) {
            pNames = DEF_ANNOTATION;
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.annotation.title", LBL_ANNOTATION));
            ((DrianmonGuiDef.Annotation) o).displayXML(pxml, 0);
        } else {
            targetLabel.setText(
                getResourceConverter().getString(
                    "common.unknownType.title", LBL_UNKNOWN_TYPE));
        }

        try {
            jEditorPaneXML.read(new StringReader(sxml.toString()), null);
            jEditorPaneXML.getDocument().putProperty(
                PlainDocument.tabSizeAttribute,
                new Integer(2));
        } catch (Exception ex) {
        }

        targetLabel2.setText(targetLabel.getText());

        PropertyTableModel ptm = new PropertyTableModel(workbench, o, pNames);

        ptm.setFactTable(selectedFactTable);
        ptm.setFactTableSchema(selectedFactTableSchema);

        // Generate a list of pre-existing names of siblings in parent
        // component for checking unique names.
        Object parent = null;
        for (int i = e.getPath().getPathCount() - 1 - 1; i >= 0; i--) {
            parent = e.getPath().getPathComponent(i);   // get parent path
            break;
        }
        if (parent != null) {
            Field[] fs = parent.getClass().getFields();
            List<String> names = new ArrayList<String>();
            for (int i = 0; i < fs.length; i++) {
                if (fs[i].getType().isArray()
                    && (fs[i].getType().getComponentType().isInstance(o)))
                {
                    // Selected schema object is an instance of parent's field
                    // (an array).
                    try {
                        // name field of array's objects.
                        Field fname =
                            fs[i].getType().getComponentType().getField("name");
                        // get the parent's array of child objects
                        Object objs = fs[i].get(parent);
                        for (int j = 0; j < Array.getLength(objs); j++) {
                            Object child = Array.get(objs, j);
                            String vname = (String) fname.get(child);
                            names.add(vname);
                        }
                        ptm.setNames(names);
                    } catch (Exception ex) {
                        // name field dosen't exist, skip parent object.
                    }
                    break;
                }
            }
        }

        propertyTable.setModel(ptm);
        propertyTable.getColumnModel().getColumn(0).setMaxWidth(150);
        propertyTable.getColumnModel().getColumn(0).setMinWidth(150);

        for (int i = 0; i < propertyTable.getRowCount(); i++) {
            TableCellRenderer renderer = propertyTable.getCellRenderer(i, 1);
            Component comp =
                renderer.getTableCellRendererComponent(
                    propertyTable,
                    propertyTable.getValueAt(i, 1),
                    false,
                    false,
                    i,
                    1);
            try {
                int height = comp.getMaximumSize().height;
                propertyTable.setRowHeight(i, height);
            } catch (Exception ea) {
            }
        }
    }

    /**
     * @see javax.swing.event.CellEditorListener#editingCanceled(ChangeEvent)
     */
    public void editingCanceled(ChangeEvent e) {
        updater.update();
    }

    /**
     * @see javax.swing.event.CellEditorListener#editingStopped(ChangeEvent)
     */
    public void editingStopped(ChangeEvent e) {
        setDirty(true);
        if (!dirtyFlag || ((PropertyTableModel) propertyTable
            .getModel()).target instanceof DrianmonGuiDef.Schema)
        {
            setDirtyFlag(true);   // true means dirty indication shown on title
            setTitle();
        }

        String emsg =
            ((PropertyTableModel) propertyTable.getModel()).getErrorMsg();
        if (emsg != null) {
            JOptionPane.showMessageDialog(
                this, emsg, "Error", JOptionPane.ERROR_MESSAGE);
            ((PropertyTableModel) propertyTable.getModel()).setErrorMsg(null);
        }

        updater.update();
    }

    class PopupTrigger extends MouseAdapter {

        // From MouseAdapter javadoc:
        //
        // Popup menus are triggered differently
        // on different systems. Therefore, isPopupTrigger
        // should be checked in both mousePressed
        // and mouseReleased
        // for proper cross-platform functionality.

        public void mousePressed(MouseEvent e) {
            showMenu(e);
        }

        public void mouseReleased(MouseEvent e) {
            showMenu(e);
        }

        public void showMenu(MouseEvent e) {
            if (e.isPopupTrigger()) {
                int x = e.getX();
                int y = e.getY();
                TreePath path = tree.getPathForLocation(x, y);
                if (path != null) {
                    jPopupMenu.setPath(path);
                    jPopupMenu.removeAll();
                    Object pathSelected = path.getLastPathComponent();
                    if (pathSelected instanceof DrianmonGuiDef.Schema) {
                        DrianmonGuiDef.Schema s =
                            (DrianmonGuiDef.Schema) pathSelected;
                        jPopupMenu.add(addCube);
                        jPopupMenu.add(addDimension);
                        jPopupMenu.add(addNamedSet);
                        jPopupMenu.add(addUserDefinedFunction);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(addVirtualCube);
                        jPopupMenu.add(addRole);
                        jPopupMenu.add(addParameter);
                        jPopupMenu.add(addAnnotations);
                        if (s.annotations == null) {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                    } else if (pathSelected instanceof DrianmonGuiDef.Cube) {
                        jPopupMenu.add(addDimension);
                        jPopupMenu.add(addDimensionUsage);
                        jPopupMenu.add(addMeasure);
                        jPopupMenu.add(addCalculatedMember);
                        jPopupMenu.add(addNamedSet);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(addTable);
                        jPopupMenu.add(addView);
                        jPopupMenu.add(addInlineTable);
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.Cube) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator2);
                        jPopupMenu.add(delete);
                        if (((DrianmonGuiDef.Cube) pathSelected).fact == null) {
                            addMeasure.setEnabled(false);
                            addCalculatedMember.setEnabled(false);
                            addTable.setEnabled(true);
                            addView.setEnabled(true);
                            addInlineTable.setEnabled(true);
                        } else {
                            addMeasure.setEnabled(true);
                            addCalculatedMember.setEnabled(true);
                            addTable.setEnabled(false);
                            addView.setEnabled(false);
                            addInlineTable.setEnabled(false);
                        }
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.Dimension)
                    {
                        jPopupMenu.add(addHierarchy);
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.Dimension) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.Hierarchy)
                    {
                        jPopupMenu.add(addLevel);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(addTable);
                        jPopupMenu.add(addJoin);
                        jPopupMenu.add(addView);
                        jPopupMenu.add(addInlineTable);
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.Hierarchy) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator2);
                        jPopupMenu.add(delete);
                        // PSW-274
                        addLevel.setEnabled(true);
                        // Enable Add <Relation>
                        if (((DrianmonGuiDef.Hierarchy) pathSelected).relation
                            == null)
                        {
                            addTable.setEnabled(true);
                            addJoin.setEnabled(true);
                            addView.setEnabled(true);
                            addInlineTable.setEnabled(true);
                        } else {
                            // disable Add <Relation>
                            addTable.setEnabled(false);
                            addJoin.setEnabled(false);
                            addView.setEnabled(false);
                            addInlineTable.setEnabled(false);
                        }
                    } else if (pathSelected instanceof DrianmonGuiDef.Level) {
                        jPopupMenu.add(moveLevelUp);
                        jPopupMenu.add(moveLevelDown);
                        jPopupMenu.add(addProperty);
                        jPopupMenu.add(addKeyExp);
                        DrianmonGuiDef.Level level =
                            (DrianmonGuiDef.Level) pathSelected;
                        if (level.keyExp == null) {
                            addKeyExp.setEnabled(true);
                        } else {
                            addKeyExp.setEnabled(false);
                        }
                        jPopupMenu.add(addNameExp);
                        if (level.nameExp == null) {
                            addNameExp.setEnabled(true);
                        } else {
                            addNameExp.setEnabled(false);
                        }
                        jPopupMenu.add(addOrdinalExp);
                        if (level.ordinalExp == null) {
                            addOrdinalExp.setEnabled(true);
                        } else {
                            addOrdinalExp.setEnabled(false);
                        }
                        jPopupMenu.add(addCaptionExp);
                        if (level.captionExp == null) {
                            addCaptionExp.setEnabled(true);
                        } else {
                            addCaptionExp.setEnabled(false);
                        }
                        jPopupMenu.add(addParentExp);
                        if (level.parentExp == null) {
                            addParentExp.setEnabled(true);
                        } else {
                            addParentExp.setEnabled(false);
                        }
                        jPopupMenu.add(addClosure);
                        if (level.closure == null) {
                            addClosure.setEnabled(true);
                        } else {
                            addClosure.setEnabled(false);
                        }
                        jPopupMenu.add(addAnnotations);
                        if (level.annotations == null) {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(addMemberFormatter);
                        if (((DrianmonGuiDef.Level) pathSelected)
                            .memberFormatter == null)
                        {
                            addMemberFormatter.setEnabled(true);
                        } else {
                            addMemberFormatter.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.KeyExpression
                               || pathSelected
                               instanceof DrianmonGuiDef.NameExpression
                               || pathSelected
                               instanceof DrianmonGuiDef.OrdinalExpression
                               || pathSelected
                               instanceof DrianmonGuiDef.CaptionExpression
                               || pathSelected
                               instanceof DrianmonGuiDef.ParentExpression
                               || pathSelected
                               instanceof DrianmonGuiDef.ExpressionView
                               || pathSelected
                               instanceof DrianmonGuiDef.View)
                    {
                        jPopupMenu.add(addSQL);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.RelationOrJoin)
                    {
                        Object po = path.getParentPath().getLastPathComponent();
                        if (!(po instanceof DrianmonGuiDef.RelationOrJoin)
                            && !(po instanceof DrianmonGuiDef.Closure))
                        {
                            if (po instanceof DrianmonGuiDef.Cube) {
                                jPopupMenu.add(addAggName);
                                jPopupMenu.add(addAggPattern);
                                jPopupMenu.add(addAggExclude);
                                jPopupMenu.add(jSeparator1);
                            }
                            jPopupMenu.add(delete);
                        } else {
                            return;
                        }
                    } else if (pathSelected instanceof DrianmonGuiDef.Measure) {
                        jPopupMenu.add(addMeasureExp);
                        jPopupMenu.add(addCalculatedMemberProperty);
                        if (((DrianmonGuiDef.Measure) pathSelected).measureExp
                            == null)
                        {
                            addMeasureExp.setEnabled(true);
                        } else {
                            addMeasureExp.setEnabled(false);
                        }
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.Measure) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(addCellFormatter);
                        if (((DrianmonGuiDef.Measure) pathSelected)
                            .cellFormatter == null)
                        {
                            addCellFormatter.setEnabled(true);
                        } else {
                            addCellFormatter.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.NamedSet)
                    {
                        jPopupMenu.add(addFormula);
                        if (((DrianmonGuiDef.NamedSet) pathSelected)
                            .formulaElement == null)
                        {
                            addFormula.setEnabled(false);
                        } else {
                            addFormula.setEnabled(false);
                        }
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.NamedSet) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.CalculatedMember)
                    {
                        jPopupMenu.add(addFormula);
                        jPopupMenu.add(addCalculatedMemberProperty);
                        if (((DrianmonGuiDef.CalculatedMember) pathSelected)
                            .formulaElement == null)
                        {
                            addFormula.setEnabled(false);
                        } else {
                            addFormula.setEnabled(false);
                        }
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.CalculatedMember) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(addCellFormatter);
                        if (((DrianmonGuiDef.CalculatedMember) pathSelected)
                            .cellFormatter == null)
                        {
                            addCellFormatter.setEnabled(true);
                        } else {
                            addCellFormatter.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                               instanceof DrianmonGuiDef.MeasureExpression)
                    {
                        jPopupMenu.add(addSQL);
                        addSQL.setEnabled(false);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected instanceof DrianmonGuiDef.Closure) {
                        jPopupMenu.add(delete);
                    } else if (pathSelected instanceof DrianmonGuiDef.AggName
                               || pathSelected
                               instanceof DrianmonGuiDef.AggPattern)
                    {
                        jPopupMenu.add(addAggFactCount);
                        jPopupMenu.add(addAggMeasureFactCount);
                        jPopupMenu.add(addAggIgnoreColumn);
                        jPopupMenu.add(addAggForeignKey);
                        jPopupMenu.add(addAggMeasure);
                        jPopupMenu.add(addAggLevel);
                        if (pathSelected instanceof DrianmonGuiDef.AggPattern) {
                            jPopupMenu.add(addAggExclude);
                            if (((DrianmonGuiDef.AggPattern) pathSelected)
                                .factcount == null)
                            {
                                addAggFactCount.setEnabled(true);
                            } else {
                                addAggFactCount.setEnabled(false);
                            }
                        } else {
                            if (((DrianmonGuiDef.AggName) pathSelected)
                                .factcount == null)
                            {
                                addAggFactCount.setEnabled(true);
                            } else {
                                addAggFactCount.setEnabled(false);
                            }
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.AggLevel)
                    {
                        jPopupMenu.add(addAggLevelProperty);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.VirtualCube)
                    {
                        jPopupMenu.add(addVirtualCubeDimension);
                        jPopupMenu.add(addVirtualCubeMeasure);
                        jPopupMenu.add(addCalculatedMember);
                        jPopupMenu.add(addAnnotations);
                        if (
                            ((DrianmonGuiDef.VirtualCube) pathSelected)
                                        .annotations == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (pathSelected instanceof DrianmonGuiDef.Role) {
                        jPopupMenu.add(addSchemaGrant);
                        jPopupMenu.add(addAnnotations);
                        if (((DrianmonGuiDef.Role) pathSelected).annotations
                                        == null)
                        {
                            addAnnotations.setEnabled(true);
                        } else {
                            addAnnotations.setEnabled(false);
                        }
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.SchemaGrant)
                    {
                        jPopupMenu.add(addCubeGrant);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.CubeGrant)
                    {
                        jPopupMenu.add(addDimensionGrant);
                        jPopupMenu.add(addHierarchyGrant);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected
                            instanceof DrianmonGuiDef.VirtualCubeMeasure)
                    {
                        jPopupMenu.add(addAnnotations);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.HierarchyGrant)
                    {
                        jPopupMenu.add(addMemberGrant);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected instanceof DrianmonGuiDef.Annotations)
                    {
                        jPopupMenu.add(addAnnotation);
                        jPopupMenu.add(jSeparator1);
                        jPopupMenu.add(delete);
                    } else if (
                        pathSelected
                            instanceof DrianmonGuiDef.UserDefinedFunction)
                    {
                        jPopupMenu.add(addScript);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                        instanceof DrianmonGuiDef.Property)
                    {
                        jPopupMenu.add(addPropertyFormatter);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                        instanceof DrianmonGuiDef.CellFormatter)
                    {
                        jPopupMenu.add(addScript);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                        instanceof DrianmonGuiDef.MemberFormatter)
                    {
                        jPopupMenu.add(addScript);
                        jPopupMenu.add(delete);
                    } else if (pathSelected
                        instanceof DrianmonGuiDef.PropertyFormatter)
                    {
                        jPopupMenu.add(addScript);
                        jPopupMenu.add(delete);
                    } else {
                        jPopupMenu.add(delete);
                    }
                    jPopupMenu.show(tree, x, y);
                }
            }
        }
    }

    static final String[] DEF_DEFAULT = {};
    static final String[] DEF_VIRTUAL_CUBE = {"name", "description", "caption",
        "enabled", "visible"};
    static final String[] DEF_VIRTUAL_CUBE_MEASURE = {
        "name", "cubeName", "visible"};
    static final String[] DEF_VIRTUAL_CUBE_DIMENSION = {
        "name", "cubeName", "caption", "foreignKey"};
    static final String[] DEF_VIEW = {"alias"};
    static final String[] DEF_TABLE = {"schema", "name", "alias"};
    static final String[] DEF_AGG_FACT_COUNT = {"column"};
    static final String[] DEF_AGG_MEASURE_FACT_COUNT = {"column", "factColumn"};
    static final String[] DEF_AGG_NAME = {
        "name", "ignorecase", "approxRowCount"};
    static final String[] DEF_AGG_PATTERN = {"pattern", "ignorecase"};
    static final String[] DEF_AGG_EXCLUDE = {"pattern", "name", "ignorecase"};
    static final String[] DEF_AGG_IGNORE_COLUMN = {"column"};
    static final String[] DEF_AGG_FOREIGN_KEY = {"factColumn", "aggColumn"};
    static final String[] DEF_AGG_MEASURE = {"column", "name", "rollupType"};
    static final String[] DEF_AGG_LEVEL = {
        "column",
        "name",
        "collapsed",
        "captionColumn",
        "ordinalColumn",
        "nameColumn"
    };

    static final String[] DEF_AGG_LEVEL_PROP = {"name", "column"};
    static final String[] DEF_CLOSURE = {"parentColumn", "childColumn"};
    static final String[] DEF_RELATION = {"name"};
    static final String[] DEF_SQL = {"cdata", "dialect"}; //?
    static final String[] DEF_ANNOTATION = {"name", "cdata"};
    static final String[] DEF_SCHEMA = {
        "name", "description", "measuresCaption", "defaultRole"};
    static final String[] DEF_PROPERTY = {
        "name", "description", "column", "type", "formatter", "caption"};
    static final String[] DEF_PARAMETER_SCHEMA = {
        "name", "description", "type", "modifiable", "defaultValue"}; //?
    static final String[] DEF_PARAMETER = {"name", "value"}; //?
    static final String[] DEF_MEASURE = {
        "name",
        "description",
        "aggregator",
        "column",
        "formatString",
        "datatype",
        "formatter",
        "caption",
        "visible"
    };

    static final String[] DEF_CALCULATED_MEMBER = {
        "name",
        "description",
        "caption",
        "dimension",
        "hierarchy",
        "parent",
        "visible",
        "formula | formulaElement.cdata",
        "formatString",
    };
    static final String[] DEF_FORMULA = {"cdata"};
    static final String[] DEF_CALCULATED_MEMBER_PROPERTY = {
        "name", "description", "caption", "expression", "value"};
    static final String[] DEF_NAMED_SET = {"name", "description", "formula",
        "caption"};
    static final String[] DEF_USER_DEFINED_FUNCTION = {"name", "className"};

    static final String[] DEF_SCRIPT = {"language", "cdata"};

    static final String[] DEF_LEVEL = {
        "name",
        "description",
        "table",
        "column",
        "nameColumn",
        "parentColumn",
        "nullParentValue",
        "ordinalColumn",
        "type",
        "internalType",
        "uniqueMembers",
        "levelType",
        "hideMemberIf",
        "approxRowCount",
        "caption",
        "captionColumn",
        "formatter",
        "visible"
    };
    static final String[] DEF_JOIN = {
        "leftAlias", "leftKey", "rightAlias", "rightKey"};
    static final String[] DEF_HIERARCHY = {
        "name",
        "description",
        "hasAll",
        "allMemberName",
        "allMemberCaption",
        "allLevelName",
        "defaultMember",
        "memberReaderClass",
        "primaryKeyTable",
        "primaryKey",
        "caption",
        "visible"
    };
    static final String[] DEF_FORMATTER = {"className"};
    static final String[] DEF_EXPRESSION_VIEW = {};
    static final String[] DEF_DIMENSION_USAGE = {
        "name", "foreignKey", "source", "level",
        "usagePrefix", "caption", "visible"};
    static final String[] DEF_DIMENSION = {
        "name", "description", "foreignKey", "type",
        "usagePrefix", "caption", "visible"};
    static final String[] DEF_CUBE = {"name", "description", "caption",
        "cache", "enabled", "visible"};
    static final String[] DEF_ROLE = {"name"};
    static final String[] DEF_SCHEMA_GRANT = {"access"};
    static final String[] DEF_CUBE_GRANT = {"access", "cube"};
    static final String[] DEF_DIMENSION_GRANT = {"access", "dimension"};
    static final String[] DEF_HIERARCHY_GRANT = {
        "access", "hierarchy", "topLevel", "bottomLevel", "rollupPolicy"};
    static final String[] DEF_MEMBER_GRANT = {"access", "member"};
    static final String[] DEF_COLUMN = {"name", "table"};   //?

    private static final String LBL_COLUMN = "Column";
    private static final String LBL_CUBE = "Cube";
    private static final String LBL_ROLE = "Role";
    private static final String LBL_SCHEMA_GRANT = "Schema Grant";
    private static final String LBL_CUBE_GRANT = "Cube Grant";
    private static final String LBL_DIMENSION_GRANT = "Dimension Grant";
    private static final String LBL_HIERARCHY_GRANT = "Hierarchy Grant";
    private static final String LBL_MEMBER_GRANT = "Member Grant";
    private static final String LBL_DIMENSION = "Dimension";
    private static final String LBL_DIMENSION_USAGE = "Dimension Usage";
    private static final String LBL_EXPRESSION_VIEW = "Expression View";
    private static final String LBL_KEY_EXPRESSION = "Key Expression";
    private static final String LBL_NAME_EXPRESSION = "Name Expression";
    private static final String LBL_ANNOTATIONS = "Annotations";
    private static final String LBL_ANNOTATION = "Annotation";
    private static final String LBL_ORDINAL_EXPRESSION = "Ordinal Expression";
    private static final String LBL_PARENT_EXPRESSION = "Parent Expression";
    private static final String LBL_MEASURE_EXPRESSION = "Measure Expression";
    private static final String LBL_HIERARCHY = "Hierarchy";
    private static final String LBL_JOIN = "Join";
    private static final String LBL_LEVEL = "Level";
    private static final String LBL_MEASURE = "Measure";
    private static final String LBL_CALCULATED_MEMBER = "Calculated Member";
    private static final String LBL_CALCULATED_MEMBER_PROPERTY =
        "Calculated Member Property";
    private static final String LBL_NAMED_SET = "Named Set";
    private static final String LBL_USER_DEFINED_FUNCTION =
        "User Defined Function";
    private static final String LBL_PARAMETER = "Parameter";
    private static final String LBL_PARAMETER_SCHEMA = "Schema Parameter";
    private static final String LBL_PROPERTY = "Property";
    private static final String LBL_SCHEMA = "Schema";
    private static final String LBL_SQL = "SQL";
    private static final String LBL_TABLE = "Table";
    private static final String LBL_CLOSURE = "Closure";

    private static final String LBL_AGG_NAME = "Aggregate Name";
    private static final String LBL_AGG_IGNORE_COLUMN =
        "Aggregate Ignore Column";
    private static final String LBL_AGG_FOREIGN_KEY = "Aggregate Foreign Key";
    private static final String LBL_AGG_MEASURE = "Aggregate Measure";
    private static final String LBL_AGG_LEVEL = "Aggregate Level";
    private static final String LBL_AGG_LEVEL_PROP = "Aggregate Level Property";
    private static final String LBL_AGG_PATTERN = "Aggregate Pattern";
    private static final String LBL_AGG_EXCLUDE = "Aggregate Exclude";
    private static final String LBL_AGG_FACT_COUNT = "Aggregate Fact Count";
    private static final String LBL_AGG_MEASURE_FACT_COUNT = "Aggregate Measure Fact Count";

    private static final String LBL_VIEW = "View";
    private static final String LBL_VIRTUAL_CUBE = "Virtual Cube";
    private static final String LBL_VIRTUAL_CUBE_DIMENSION =
        "Virtual Cube Dimension";
    private static final String LBL_VIRTUAL_CUBE_MEASURE =
        "Virtual Cube Measure";
    private static final String LBL_UNKNOWN_TYPE = "Unknown Type";

    private static String alert = "Alert";

    private AbstractAction arrowButtonUpAction;
    private AbstractAction arrowButtonDownAction;

    private AbstractAction addCube;
    private AbstractAction addRole;
    private AbstractAction addParameter;
    private AbstractAction addSchemaGrant;
    private AbstractAction addCubeGrant;
    private AbstractAction addDimensionGrant;
    private AbstractAction addHierarchyGrant;
    private AbstractAction addMemberGrant;
    private AbstractAction addAnnotations;
    private AbstractAction addAnnotation;


    private AbstractAction addDimension;
    private AbstractAction addDimensionUsage;
    private AbstractAction addHierarchy;
    private AbstractAction addNamedSet;
    private AbstractAction addUserDefinedFunction;
    private AbstractAction addScript;
    private AbstractAction addCalculatedMember;

    private AbstractAction addMeasure;
    private AbstractAction addMeasureExp;
    private AbstractAction addFormula;
    private AbstractAction addLevel;
    private AbstractAction addSQL;
    private AbstractAction addKeyExp;
    private AbstractAction addNameExp;
    private AbstractAction addOrdinalExp;
    private AbstractAction addCaptionExp;
    private AbstractAction addParentExp;

    private AbstractAction addTable;
    private AbstractAction addJoin;
    private AbstractAction addView;
    private AbstractAction addInlineTable;
    private AbstractAction moveLevelUp;
    private AbstractAction moveLevelDown;
    private AbstractAction addProperty;
    private AbstractAction addCalculatedMemberProperty;
    private AbstractAction addClosure;

    private AbstractAction addAggName;
    private AbstractAction addAggIgnoreColumn;
    private AbstractAction addAggForeignKey;
    private AbstractAction addAggMeasure;
    private AbstractAction addAggLevel;
    private AbstractAction addAggLevelProperty;
    private AbstractAction addAggPattern;
    private AbstractAction addAggExclude;
    private AbstractAction addAggFactCount;
    private AbstractAction addAggMeasureFactCount;

    private AbstractAction addVirtualCube;
    private AbstractAction addVirtualCubeDimension;
    private AbstractAction addVirtualCubeMeasure;

    private AbstractAction addCellFormatter;
    private AbstractAction addMemberFormatter;
    private AbstractAction addPropertyFormatter;

    private AbstractAction delete;

    private AbstractAction editMode;

    private JTable propertyTable;
    private JPanel jPanel1;
    private JPanel jPanel2;
    private JPanel jPanel3;
    private JButton addLevelButton;
    private JScrollPane jScrollPane2;
    private JScrollPane jScrollPane1;
    private JButton addPropertyButton;
    private JButton addCalculatedMemberPropertyButton;
    private JButton pasteButton;
    private JLabel targetLabel;
    private JLabel validStatusLabel;
    private JLabel targetLabel2;
    private JLabel validStatusLabel2;
    JTree tree;
    private JSplitPane jSplitPane1;

    private JButton addDimensionButton;
    private JButton addDimensionUsageButton;
    private JButton addHierarchyButton;
    private JButton addNamedSetButton;
    private JButton addUserDefinedFunctionButton;
    private JButton addCalculatedMemberButton;
    private JButton cutButton;
    private JButton addMeasureButton;
    private JButton addCubeButton;
    private JButton addRoleButton;
    private JButton addVirtualCubeButton;
    private JButton addVirtualCubeDimensionButton;
    private JButton addVirtualCubeMeasureButton;

    private JButton deleteButton;
    private JToggleButton editModeButton;
    private JButton copyButton;
    private JToolBar jToolBar1;
    private CustomJPopupMenu jPopupMenu;
    private class CustomJPopupMenu extends JPopupMenu {
        private TreePath path;
        void setPath(TreePath path) {
            this.path = path;
        }
        public TreePath getPath() {
            return path;
        }
    }

    private JSeparator jSeparator1;
    private JSeparator jSeparator2;

    private JPanel footer;
    private JLabel databaseLabel;

    private JPanel jPanelXML;
    private JScrollPane jScrollPaneXML;
    private JEditorPane jEditorPaneXML;

    public boolean isNewFile() {
        return newFile;
    }

    public void setNewFile(boolean newFile) {
        this.newFile = newFile;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public void setTitle() {
        // Sets the title of Internal Frame within which this schema explorer is
        // displayed.  The title includes schema name and schema file name.
        parentIFrame.setTitle(
            getResourceConverter().getFormattedString(
                "schemaExplorer.frame.title",
                "Schema - {0} ({1}){2}",
                schema.name,
                schemaFile.getName(),
                isDirty() ? "*" : ""));

        parentIFrame.setToolTipText(schemaFile.toString());
    }

    public void setDirtyFlag(boolean dirtyFlag) {
        this.dirtyFlag = dirtyFlag;
    }

    public Object getParentObject() {
        TreePath tPath = tree.getSelectionPath();
        if ((tPath != null) && (tPath.getParentPath() != null)) {
            return tPath.getParentPath().getLastPathComponent();
        }
        return null;
    }

    public String getJdbcConnectionUrl() {
        return this.jdbcMetaData.jdbcConnectionUrl;
    }

    public String getJdbcUsername() {
        return this.jdbcMetaData.jdbcUsername;
    }

    public String getJdbcPassword() {
        return this.jdbcMetaData.jdbcPassword;
    }

    public String getErrMsg() {
        return errMsg;
    }

    public boolean isEditModeXML() {
        // used by schema frame focuslistener in workbench/desktoppane
        return editModeXML;
    }

    public I18n getResourceConverter() {
        return workbench.getResourceConverter();
    }

    public static void getTableNamesForJoin(
        DrianmonGuiDef.RelationOrJoin aRelOrJoin, Set<String> aTableNames)
    {
        // EC: Loops join tree and collects table names.
        if (aRelOrJoin instanceof DrianmonGuiDef.Join) {
            DrianmonGuiDef.RelationOrJoin theRelOrJoin_L =
                ((DrianmonGuiDef.Join) aRelOrJoin).left;
            DrianmonGuiDef.RelationOrJoin theRelOrJoin_R =
                ((DrianmonGuiDef.Join) aRelOrJoin).right;
            for (int i = 0; i < 2; i++) {
                // Searches first using the Left Join and then the Right.
                DrianmonGuiDef.RelationOrJoin theCurrentRelOrJoin =
                    (i == 0)
                    ? theRelOrJoin_L
                    : theRelOrJoin_R;
                if (theCurrentRelOrJoin instanceof DrianmonGuiDef.Table) {
                    DrianmonGuiDef.Table theTable =
                        ((DrianmonGuiDef.Table) theCurrentRelOrJoin);
                    String theTableName = (theTable.alias != null
                                           && theTable.alias.trim().length()
                                              > 0)
                        ? theTable.alias
                        : theTable.name;
                    aTableNames.add(theTableName);
                } else {
                    // Calls recursively collecting all table names down the
                    // join tree.
                    getTableNamesForJoin(theCurrentRelOrJoin, aTableNames);
                }
            }
        }
    }

    public static String[] getTableNameForAlias(
        DrianmonGuiDef.RelationOrJoin aRelOrJoin, String anAlias)
    {
        String theTableName = anAlias;
        String schemaName = null;

        // EC: Loops join tree and finds the table name for an alias.
        if (aRelOrJoin instanceof DrianmonGuiDef.Join) {
            DrianmonGuiDef.RelationOrJoin theRelOrJoin_L =
                ((DrianmonGuiDef.Join) aRelOrJoin).left;
            DrianmonGuiDef.RelationOrJoin theRelOrJoin_R =
                ((DrianmonGuiDef.Join) aRelOrJoin).right;
            for (int i = 0; i < 2; i++) {
                // Searches first using the Left Join and then the Right.
                DrianmonGuiDef.RelationOrJoin theCurrentRelOrJoin =
                    (i == 0)
                    ? theRelOrJoin_L
                    : theRelOrJoin_R;
                if (theCurrentRelOrJoin instanceof DrianmonGuiDef.Table) {
                    DrianmonGuiDef.Table theTable =
                        ((DrianmonGuiDef.Table) theCurrentRelOrJoin);
                    if (theTable.alias != null && theTable.alias
                        .equals(anAlias))
                    {
                        // If the alias was found get its table name and return
                        // it.
                        theTableName = theTable.name;
                        schemaName = theTable.schema;
                    }
                } else {
                    // otherwise continue down the join tree.
                    String[] result = getTableNameForAlias(
                        theCurrentRelOrJoin, anAlias);
                    schemaName = result[0];
                    theTableName = result[1];
                }
            }
        }
        return new String[]{schemaName, theTableName};
    }

    public void resetMetaData(JdbcMetaData aMetaData) {
        // Update the JdbcMetaData in the SchemaExplorer
        jdbcMetaData = aMetaData;

        // Update the database label
        String theLabel =
            getResourceConverter().getFormattedString(
                "schemaExplorer.database.text",
                "Database - {0} ({1})",
                jdbcMetaData.getDbCatalogName(),
                jdbcMetaData.getDatabaseProductName());
        databaseLabel.setText(theLabel);

        // Update the JdbcMetaData in the SchemaTreeCellRenderer.
        renderer.setMetaData(aMetaData);

        // Update the JdbcMetaData in the SchemaPropertyCellEditor.
        TableCellEditor theTableCellEditor = propertyTable.getDefaultEditor(
            Object.class);
        if (theTableCellEditor instanceof SchemaPropertyCellEditor) {
            ((SchemaPropertyCellEditor) theTableCellEditor).setMetaData(
                aMetaData);
        }
    }

    public JTreeUpdater getTreeUpdater() {
        return updater;
    }
}

// End SchemaExplorer.java
