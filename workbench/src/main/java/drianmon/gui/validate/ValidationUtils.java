/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package drianmon.gui.validate;

import drianmon.gui.DrianmonGuiDef;

import org.apache.logging.log4j.Logger;

import drianmon.gui.SchemaExplorer;

import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.util.TreeSet;

/**
 * Validates a <code>DrianmonGuiDef</code>. Class contains <code>invalid</code>
 * method formerly from <code>drianmon.gui.SchemaTreeCellRenderer</code>.
 *
 * @author mlowery
 */
public class ValidationUtils {

    private static final Logger LOGGER =
        LogManager.getLogger(ValidationUtils.class);

    static String[] DEF_LEVEL = {
        "column", "nameColumn", "parentColumn", "ordinalColumn", "captionColumn"
    };

    /**
     * Validate a schema model and returns the first error message if it is
     * invalid.
     *
     * @param messages Message provider
     * @param jdbcValidator Validator
     * @param treeModel Tree model
     * @param tpath Path
     * @param value Value
     * @param cube Cube
     * @param parentDimension Parent dimension
     * @param parentHierarchy Parent hierarchy
     * @param parentLevel Parent level
     * @param isSchemaRequired Whether schema is required
     * @return Error message if element is invalid, null if it is valid
     */
    public static String invalid(
        Messages messages,
        JdbcValidator jdbcValidator,
        TreeModel treeModel,
        TreeModelPath tpath,
        Object value,
        DrianmonGuiDef.Cube cube,
        DrianmonGuiDef.Dimension parentDimension,
        DrianmonGuiDef.Hierarchy parentHierarchy,
        DrianmonGuiDef.Level parentLevel,
        boolean isSchemaRequired)
    {
        String nameMustBeSet = messages.getString(
            "schemaTreeCellRenderer.nameMustBeSet.alert", "Name must be set");

        if (!tpath.isEmpty()) {
            int pathcount = tpath.getPathCount();
            for (int i = 0;
                i < pathcount
                && (cube == null
                    || parentDimension == null
                    || parentHierarchy == null
                    || parentLevel == null);
                i++)
            {
                final Object p = tpath.getPathComponent(i);
                if (p instanceof DrianmonGuiDef.Cube
                    && cube == null)
                {
                    cube = (DrianmonGuiDef.Cube) p;
                }
                if (p instanceof DrianmonGuiDef.Dimension
                    && parentDimension == null)
                {
                    parentDimension = (DrianmonGuiDef.Dimension) p;
                }
                if (p instanceof DrianmonGuiDef.Hierarchy
                    && parentHierarchy == null)
                {
                    parentHierarchy = (DrianmonGuiDef.Hierarchy) p;
                }
                if (p instanceof DrianmonGuiDef.Level
                    && parentLevel == null)
                {
                    parentLevel = (DrianmonGuiDef.Level) p;
                }
            }
        }

        //Step 1: check validity of this value object
        if (value instanceof DrianmonGuiDef.Schema) {
            if (isEmpty(((DrianmonGuiDef.Schema) value).name)) {
                return nameMustBeSet;
            }
        } else if (value instanceof DrianmonGuiDef.VirtualCube) {
            DrianmonGuiDef.VirtualCube virtCube =
                (DrianmonGuiDef.VirtualCube)value;
            if (isEmpty(virtCube.name)) {
                return nameMustBeSet;
            }
            if (isEmpty(virtCube.dimensions)) {
                return messages.getString(
                    "schemaTreeCellRenderer.cubeMustHaveDimensions.alert",
                    "Cube must contain dimensions");
            }
            if (isEmpty(virtCube.measures)) {
                return messages.getString(
                    "schemaTreeCellRenderer.cubeMustHaveMeasures.alert",
                    "Cube must contain measures");
            }
        } else if (value instanceof DrianmonGuiDef.VirtualCubeDimension) {
            if (isEmpty(((DrianmonGuiDef.VirtualCubeDimension) value).name)) {
                return nameMustBeSet;
            }
        } else if (value instanceof DrianmonGuiDef.VirtualCubeMeasure) {
            if (isEmpty(((DrianmonGuiDef.VirtualCubeMeasure) value).name)) {
                return nameMustBeSet;
            }
        } else if (value instanceof DrianmonGuiDef.Cube) {
            DrianmonGuiDef.Cube cubeVal = (DrianmonGuiDef.Cube) value;
            if (isEmpty(cubeVal.name)) {
                return nameMustBeSet;
            }
            if (cubeVal.fact == null
                || ((cubeVal.fact instanceof DrianmonGuiDef.Table)
                    && isEmpty(((DrianmonGuiDef.Table) cubeVal.fact).name))
                || ((cubeVal.fact instanceof DrianmonGuiDef.View)
                    && isEmpty(((DrianmonGuiDef.View) cubeVal.fact).alias)))
            {
                return messages.getString(
                    "schemaTreeCellRenderer.factNameMustBeSet.alert",
                    "Fact name must be set");
            }
            if (isEmpty(cubeVal.dimensions)) {
                return messages.getString(
                    "schemaTreeCellRenderer.cubeMustHaveDimensions.alert",
                    "Cube must contain dimensions");
            }
            if (isEmpty(cubeVal.measures)) {
                return messages.getString(
                    "schemaTreeCellRenderer.cubeMustHaveMeasures.alert",
                    "Cube must contain measures");
            }
            // database validity check, if database connection is successful
            if (jdbcValidator.isInitialized()) {
                if (((DrianmonGuiDef.Cube) value).fact
                    instanceof DrianmonGuiDef.Table)
                {
                    final DrianmonGuiDef.Table table =
                        (DrianmonGuiDef.Table) cubeVal.fact;
                    String schemaName = table.schema;
                    String factTable = table.name;
                    if (!jdbcValidator.isTableExists(schemaName, factTable)) {
                        return messages.getFormattedString(
                            "schemaTreeCellRenderer.factTableDoesNotExist.alert",
                            "Fact table {0} does not exist in database {1}",
                            factTable,
                            ((schemaName == null || schemaName.equals(""))
                                ? "."
                                : "schema " + schemaName));
                    }
                }
            }
        } else {
            if (value instanceof DrianmonGuiDef.CubeDimension) {
                if (isEmpty(((DrianmonGuiDef.CubeDimension) value).name)) {
                    return nameMustBeSet;
                }
                if (value instanceof DrianmonGuiDef.DimensionUsage) {
                    if (isEmpty(
                            ((DrianmonGuiDef.DimensionUsage) value).source))
                    {
                        return messages.getString(
                            "schemaTreeCellRenderer.sourceMustBeSet.alert",
                            "Source must be set");
                    }
                    // Check source is name of one of dimensions of schema
                    // (shared dimensions)
                    DrianmonGuiDef.Schema s =
                        (DrianmonGuiDef.Schema) treeModel.getRoot();
                    DrianmonGuiDef.Dimension ds[] = s.dimensions;
                    String sourcename =
                        ((DrianmonGuiDef.DimensionUsage) value).source;
                    boolean notfound = true;
                    for (int j = 0; j < ds.length; j++) {
                        if (ds[j].name.equalsIgnoreCase(sourcename)) {
                            notfound = false;
                            break;
                        }
                    }
                    if (notfound) {
                        return messages.getFormattedString(
                            "schemaTreeCellRenderer.sourceInSharedDimensionDoesNotExist.alert",
                            "Source {0} does not exist as Shared Dimension of Schema",
                            sourcename);
                    }
                }
                if (value instanceof DrianmonGuiDef.Dimension && cube != null) {
                    if (!isEmpty(
                            ((DrianmonGuiDef.Dimension) value).foreignKey))
                    {
                        // database validity check, if database connection is
                        // successful
                        if (jdbcValidator.isInitialized()) {
                            // TODO: Need to add validation for Views
                            if (cube.fact instanceof DrianmonGuiDef.Table) {
                                final DrianmonGuiDef.Table factTable =
                                    (DrianmonGuiDef.Table) cube.fact;
                                String foreignKey =
                                    ((DrianmonGuiDef.Dimension) value)
                                    .foreignKey;
                                if (!jdbcValidator.isColExists(
                                        factTable.schema,
                                        factTable.name,
                                        foreignKey))
                                {
                                    return messages.getFormattedString(
                                        "schemaTreeCellRenderer.foreignKeyDoesNotExist.alert",
                                        "foreignKey {0} does not exist in fact table",
                                        foreignKey);
                                }
                            }
                        }
                    }
                }
            } else if (value instanceof DrianmonGuiDef.Level) {
                // Check 'column' exists in 'table' if table is specified
                // otherwise :: case of join.

                // It should exist in relation table if it is specified
                // otherwise :: case of table.

                // It should exist in fact table :: case of degenerate dimension
                // where dimension columns exist in fact table and there is no
                // separate table.
                DrianmonGuiDef.Level level = (DrianmonGuiDef.Level) value;
                if (!isEmpty(level.levelType)) {
                    // Empty leveltype is treated as default value of "Regular""
                    // which is ok with standard/time dimension.
                    if (parentDimension != null) {
                        if ((isEmpty(parentDimension.type)
                             || parentDimension.type.equals(
                                 "StandardDimension"))
                            && !isEmpty(level.levelType)
                            && (!level.levelType.equals(
                                DrianmonGuiDef.Level._levelType_values[0])))
                        {
                            // If dimension type is 'standard' then leveltype
                            // should be 'regular'
                            return messages.getFormattedString(
                                "schemaTreeCellRenderer.levelUsedOnlyInTimeDimension.alert",
                                "levelType {0} can only be used with a TimeDimension",
                                level.levelType);
                        } else if (!isEmpty(parentDimension.type)
                                   && (parentDimension.type.equals(
                                       "TimeDimension"))
                                   && !isEmpty(level.levelType)
                                   && (level.levelType.equals(
                                       DrianmonGuiDef.Level
                                       ._levelType_values[0])))
                        {
                            // If dimension type is 'time' then leveltype value
                            // could be 'timeyears', 'timedays' etc'
                            return messages.getFormattedString(
                                "schemaTreeCellRenderer.levelUsedOnlyInStandardDimension.alert",
                                "levelType {0} can only be used with a StandardDimension",
                                level.levelType);
                        }
                    }
                }
                // verify level's name is set
                if (isEmpty(level.name)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.nameMustBeSet.alert",
                        "Level name must be set"
                    );
                }

                // check level's column is in fact table
                String column = level.column;
                if (isEmpty(column)) {
                    if (level.properties == null
                        || level.properties.length == 0)
                    {
                        return messages.getString(
                            "schemaTreeCellRenderer.columnMustBeSet.alert",
                            "Column must be set");
                    }
                } else {
                    // Enforces validation for all column types against invalid
                    // value.
                    String theMessage = null;
                    try {
                        for (int i = 0; i < DEF_LEVEL.length; i++) {
                            Field theField =
                                level.getClass().getDeclaredField(DEF_LEVEL[i]);
                            column = (String) theField.get(level);
                            theMessage = validateColumn(
                                column,
                                DEF_LEVEL[i],
                                messages,
                                level,
                                jdbcValidator,
                                cube,
                                parentHierarchy);
                            if (theMessage != null) {
                                break;
                            }
                        }
                    } catch (Exception ex) {
                        LOGGER.error("ValidationUtils", ex);
                    }
                    return theMessage;
                }
            } else if (value instanceof DrianmonGuiDef.Property) {
                // Check 'column' exists in 'table' if [level table] is
                // specified otherwise :: case of join.

                // It should exist in [hierarchy relation table] if it is
                // specified otherwise :: case of table.

                // It should exist in [fact table] :: case of degenerate
                // dimension where dimension columns exist in fact table and
                // there is no separate table.
                DrianmonGuiDef.Property p = (DrianmonGuiDef.Property) value;
                // check property's column is in table
                String column = p.column;
                if (isEmpty(column)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.columnMustBeSet.alert",
                        "Column must be set");
                }
                // Database validity check, if database connection is successful
                if (jdbcValidator.isInitialized()) {
                    String table = null;
                    if (parentLevel != null) {
                        // specified table for level's column'
                        table = parentLevel.table;
                    }
                    if (isEmpty(table)) {
                        if (parentHierarchy != null) {
                            if (parentHierarchy.relation == null
                                && cube != null)
                            {
                                // Case of degenerate dimension within cube,
                                // hierarchy table not specified
                                final DrianmonGuiDef.Table factTable =
                                    (DrianmonGuiDef.Table) cube.fact;
                                if (!jdbcValidator.isColExists(
                                        factTable.schema,
                                        factTable.name,
                                        column))
                                {
                                    return messages.getFormattedString(
                                        "schemaTreeCellRenderer.degenDimensionColumnDoesNotExist.alert",
                                        "Degenerate dimension validation check - Column {0} does not exist in fact table",
                                        column);
                                }
                            } else if (parentHierarchy.relation
                                       instanceof DrianmonGuiDef.Table)
                            {
                                final DrianmonGuiDef.Table parentTable =
                                    (DrianmonGuiDef.Table)
                                    parentHierarchy.relation;
                                if (!jdbcValidator.isColExists(
                                        parentTable.schema,
                                        parentTable.name,
                                        column))
                                {
                                    return messages.getFormattedString(
                                        "schemaTreeCellRenderer.columnInDimensionDoesNotExist.alert",
                                        "Column {0} does not exist in Dimension table",
                                        parentTable.name);
                                }
                            }
                        }
                    } else {
                        if (!jdbcValidator.isColExists(null, table, column)) {
                            return messages.getFormattedString(
                                "schemaTreeCellRenderer.columnInDimensionDoesNotExist.alert",
                                "Column {0} does not exist in Level table {1}",
                                column,
                                table);
                        }
                    }
                }
            } else if (value instanceof DrianmonGuiDef.Measure) {
                final DrianmonGuiDef.Measure measure =
                    (DrianmonGuiDef.Measure) value;
                if (isEmpty(measure.name)) {
                    return nameMustBeSet;
                }
                if (isEmpty(measure.aggregator)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.aggregatorMustBeSet.alert",
                        "Aggregator must be set");
                }
                if (measure.measureExp != null) {
                    // Measure expressions are OK
                } else if (isEmpty(measure.column)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.columnMustBeSet.alert",
                        "Column must be set");
                } else if (cube != null && cube.fact != null) {
                    // Database validity check, if database connection is
                    // successful
                    if (cube.fact instanceof DrianmonGuiDef.Table) {
                        final DrianmonGuiDef.Table factTable =
                            (DrianmonGuiDef.Table) cube.fact;
                        if (jdbcValidator.isInitialized()) {
                            String column = measure.column;
                            if (jdbcValidator.isColExists(
                                    factTable.schema,
                                    factTable.name,
                                    column))
                            {
                                // Check for aggregator type only if column
                                // exists in table.

                                // Check if aggregator selected is valid on
                                // the data type of the column selected.
                                int colType =
                                    jdbcValidator.getColumnDataType(
                                        factTable.schema,
                                        factTable.name,
                                        measure.column);
                                // Coltype of 2, 4,5, 7, 8, -5 is numeric types
                                // whereas 1, 12 are char varchar string
                                // and 91 is date type.
                                // Types are enumerated in java.sql.Types.
                                int agIndex = -1;
                                if ("sum".equals(
                                        measure.aggregator)
                                    || "avg".equals(
                                        measure.aggregator))
                                {
                                    // aggregator = sum or avg, column should
                                    // be numeric
                                    agIndex = 0;
                                }
                                if (!(agIndex == -1
                                    || (colType >= 2 && colType <= 8)
                                    || colType == -5 || colType == -6))
                                {
                                    return messages.getFormattedString(
                                        "schemaTreeCellRenderer.aggregatorNotValidForColumn.alert",
                                        "Aggregator {0} is not valid for the data type of the column {1}",
                                        measure.aggregator,
                                        measure.column);
                                }
                            }
                        }
                    }
                }
            } else if (value instanceof DrianmonGuiDef.Hierarchy) {
                final DrianmonGuiDef.Hierarchy hierarchy =
                    (DrianmonGuiDef.Hierarchy) value;
                if (hierarchy.relation instanceof DrianmonGuiDef.Join) {
                    if (isEmpty(hierarchy.primaryKeyTable)) {
                        if (isEmpty(hierarchy.primaryKey)) {
                            return messages.getString(
                                "schemaTreeCellRenderer.primaryKeyTableAndPrimaryKeyMustBeSet.alert",
                                "PrimaryKeyTable and PrimaryKey must be set for Join");
                        } else {
                            return messages.getString(
                                "schemaTreeCellRenderer.primaryKeyTableMustBeSet.alert",
                                "PrimaryKeyTable must be set for Join");
                        }
                    }
                    if (isEmpty(hierarchy.primaryKey)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.primaryKeyMustBeSet.alert",
                            "PrimaryKey must be set for Join");
                    }
                }

                DrianmonGuiDef.Level[] levels = hierarchy.levels;
                if (levels == null || levels.length == 0) {
                    return messages.getString(
                        "schemaTreeCellRenderer.atLeastOneLevelForHierarchy.alert",
                        "At least one Level must be set for Hierarchy");
                }

                // Validates that value in primaryKey exists in Table.
                String schema = null;
                String pkTable = null;
                if (hierarchy.relation instanceof DrianmonGuiDef.Join) {
                    String[] schemaAndTable =
                        SchemaExplorer.getTableNameForAlias(
                            hierarchy.relation,
                            hierarchy.primaryKeyTable);
                    schema = schemaAndTable[0];
                    pkTable = schemaAndTable[1];
                } else if (hierarchy.relation instanceof DrianmonGuiDef.Table) {
                    final DrianmonGuiDef.Table table =
                        (DrianmonGuiDef.Table) hierarchy.relation;
                    pkTable = table.name;
                    schema = table.schema;
                }

                if (pkTable != null
                    && !jdbcValidator.isColExists(
                        schema, pkTable, hierarchy.primaryKey))
                {
                    return messages.getFormattedString(
                        "schemaTreeCellRenderer.columnInTableDoesNotExist.alert",
                        "Column {0} defined in field {1} does not exist in table {2}",
                            isEmpty(hierarchy.primaryKey.trim())
                                ? "' '"
                                : hierarchy.primaryKey, "primaryKey",
                            pkTable);
                }

                // Validates against primaryKeyTable name on field when using
                // Table.
                if (hierarchy.relation instanceof DrianmonGuiDef.Table) {
                    if (!isEmpty(hierarchy.primaryKeyTable)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.fieldMustBeEmpty",
                            "Table field must be empty");
                    }
                }

                // Validates that the value at primaryKeyTable corresponds to
                // tables in joins.
                String primaryKeyTable = hierarchy.primaryKeyTable;
                if (!isEmpty(primaryKeyTable)
                    && (hierarchy.relation instanceof DrianmonGuiDef.Join))
                {
                    TreeSet<String> joinTables = new TreeSet<String>();
                    SchemaExplorer.getTableNamesForJoin(
                        hierarchy.relation, joinTables);
                    if (!joinTables.contains(primaryKeyTable)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.wrongTableValue",
                            "Table value does not correspond to any join");
                    }
                }

                if (!isEmpty(primaryKeyTable)
                    && (hierarchy.relation instanceof DrianmonGuiDef.Table))
                {
                    DrianmonGuiDef.Table theTable =
                        (DrianmonGuiDef.Table) hierarchy.relation;
                    String compareTo =
                        (theTable.alias != null
                         && theTable.alias.trim().length() > 0)
                            ? theTable.alias
                            : theTable.name;
                    if (!primaryKeyTable.equals(compareTo)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.tableDoesNotMatch",
                            "Table value does not correspond to Hierarchy Relation");
                    }
                }

            } else if (value instanceof DrianmonGuiDef.NamedSet) {
                final DrianmonGuiDef.NamedSet namedSet =
                    (DrianmonGuiDef.NamedSet) value;
                if (isEmpty(namedSet.name)) {
                    return nameMustBeSet;
                }
                if (isEmpty(namedSet.formula)
                    && namedSet.formulaElement == null)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.formulaMustBeSet.alert",
                        "Formula must be set");
                }
            } else if (value instanceof DrianmonGuiDef.Formula) {
                final DrianmonGuiDef.Formula formula =
                    (DrianmonGuiDef.Formula) value;
                if (isEmpty(formula.cdata)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.formulaMustBeSet.alert",
                        "Formula must be set");
                }
            } else if (value instanceof DrianmonGuiDef.UserDefinedFunction) {
                final DrianmonGuiDef.UserDefinedFunction udf =
                    (DrianmonGuiDef.UserDefinedFunction) value;
                if (isEmpty(udf.name)) {
                    return nameMustBeSet;
                }
                if (isEmpty(udf.className)
                    && udf.script == null)
                {
                    return messages.getString(
                        "Either a Class Name or a Script are required",
                        "Class name must be set");
                }
            } else if (value instanceof DrianmonGuiDef.MemberFormatter) {
                final DrianmonGuiDef.MemberFormatter f =
                    (DrianmonGuiDef.MemberFormatter) value;
                if (isEmpty(f.className)
                    && f.script == null)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.classNameOrScriptRequired.alert",
                        "Either a Class Name or a Script are required");
                }
            } else if (value instanceof DrianmonGuiDef.CellFormatter) {
                final DrianmonGuiDef.CellFormatter f =
                    (DrianmonGuiDef.CellFormatter) value;
                if (isEmpty(f.className)
                    && f.script == null)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.classNameOrScriptRequired.alert",
                        "Either a Class Name or a Script are required");
                }
            } else if (value instanceof DrianmonGuiDef.PropertyFormatter) {
                final DrianmonGuiDef.PropertyFormatter f =
                    (DrianmonGuiDef.PropertyFormatter) value;
                if (isEmpty(f.className)
                    && f.script == null)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.classNameOrScriptRequired.alert",
                        "Either a Class Name or a Script are required");
                }
            } else if (value instanceof DrianmonGuiDef.CalculatedMember) {
                final DrianmonGuiDef.CalculatedMember calculatedMember =
                    (DrianmonGuiDef.CalculatedMember) value;
                if (isEmpty(calculatedMember.name)) {
                    return nameMustBeSet;
                }
                if (isEmpty(calculatedMember.dimension)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.dimensionMustBeSet.alert",
                        "Dimension must be set");
                }
                if (isEmpty(calculatedMember.formula)
                    && calculatedMember.formulaElement == null)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.formulaMustBeSet.alert",
                        "Formula must be set");
                }
            } else if (value instanceof DrianmonGuiDef.Join) {
                final DrianmonGuiDef.Join join = (DrianmonGuiDef.Join) value;
                if (isEmpty(join.leftKey)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.leftKeyMustBeSet.alert",
                        "Left key must be set");
                }
                if (isEmpty(join.rightKey)) {
                    return messages.getString(
                        "schemaTreeCellRenderer.rightKeyMustBeSet.alert",
                        "Right key must be set");
                }
            } else if (value instanceof DrianmonGuiDef.Table) {
                final DrianmonGuiDef.Table table = (DrianmonGuiDef.Table) value;
                String tableName = table.name;
                if (!jdbcValidator.isTableExists(null, tableName)) {
                    return messages.getFormattedString(
                        "schemaTreeCellRenderer.tableDoesNotExist.alert",
                        "Table {0} does not exist in database",
                        tableName);
                }

                String theSchema = table.schema;
                if (!isEmpty(theSchema)
                    && !jdbcValidator.isSchemaExists(theSchema))
                {
                    return messages.getFormattedString(
                        "schemaTreeCellRenderer.schemaDoesNotExist.alert",
                        "Schema {0} does not exist",
                        theSchema);
                }
                if (isEmpty(theSchema) && isSchemaRequired) {
                    return messages.getString(
                        "schemaTreeCellRenderer.schemaMustBeSet.alert",
                        "Schema must be set");
                }
            }
        }

        // Step 2: check validity of all child objects for this value object.
        int childCnt = treeModel.getChildCount(value);
        for (int i = 0; i < childCnt; i++) {
            Object child = treeModel.getChild(value, i);
            String childErrMsg;
            if (child instanceof DrianmonGuiDef.Cube) {
                // check current cube child and its children
                childErrMsg = invalid(
                    messages,
                    jdbcValidator,
                    treeModel,
                    tpath,
                    child,
                    (DrianmonGuiDef.Cube) child,
                    parentDimension,
                    parentHierarchy,
                    parentLevel,
                    isSchemaRequired);
            } else if (child instanceof DrianmonGuiDef.Dimension) {
                // check the current hierarchy and its children
                childErrMsg = invalid(
                    messages,
                    jdbcValidator,
                    treeModel,
                    tpath,
                    child,
                    cube,
                    (DrianmonGuiDef.Dimension) child,
                    parentHierarchy,
                    parentLevel,
                    isSchemaRequired);
            } else if (child instanceof DrianmonGuiDef.Hierarchy) {
                // special check for cube dimension where foreign key is blank :
                // allowed/not allowed
                if (value instanceof DrianmonGuiDef.Dimension
                    && cube != null
                    && ((DrianmonGuiDef.Hierarchy) child).relation != null)
                {
                    if (isEmpty(
                            ((DrianmonGuiDef.Dimension) value).foreignKey))
                    {
                        // check foreignkey is not blank;
                        // if relation is null, foreignkey must be specified
                        return messages.getString(
                            "schemaTreeCellRenderer.foreignKeyMustBeSet.alert",
                            "Foreign key must be set");
                    }
                }
                // check the current hierarchy and its children
                childErrMsg = invalid(
                    messages,
                    jdbcValidator,
                    treeModel,
                    tpath,
                    child,
                    cube,
                    parentDimension,
                    (DrianmonGuiDef.Hierarchy) child,
                    parentLevel,
                    isSchemaRequired);
            } else if (child instanceof DrianmonGuiDef.Level) {
                // check the current hierarchy and its children
                childErrMsg = invalid(
                    messages,
                    jdbcValidator,
                    treeModel,
                    tpath,
                    child,
                    cube,
                    parentDimension,
                    parentHierarchy,
                    (DrianmonGuiDef.Level) child,
                    isSchemaRequired);
            } else {
                // check this child and all its children objects with incoming
                // cube and hierarchy
                childErrMsg = invalid(
                    messages,
                    jdbcValidator,
                    treeModel,
                    tpath,
                    child,
                    cube,
                    parentDimension,
                    parentHierarchy,
                    parentLevel,
                    isSchemaRequired);
            }

            // If all children are valid then do a special check.
            // Special check for cubes to see if their child dimensions have
            // foreign key set and set the childErrMsg with error msg
            /* === Begin : disabled
            if (childErrMsg == null) {  // all children are valid
                if (child instanceof DrianmonGuiDef.Cube) {
                    DrianmonGuiDef.Cube c = (DrianmonGuiDef.Cube) child;
                    DrianmonGuiDef.CubeDimension [] ds = c.dimensions;
                    for (int j=0; j<ds.length; j++) {
                        DrianmonGuiDef.CubeDimension d =
                            (DrianmonGuiDef.CubeDimension) ds[j];
                        if (d instanceof DrianmonGuiDef.DimensionUsage) {
                            continue;   // check the next dimension.
                        }

                        // check foreignkey is not blank
                        if(isEmpty(d.foreignKey)) {
                            childErrMsg = "ForeignKey" + emptyMsg;
                            break;
                        }

                        // database validity check, if database connection is
                        // successful
                        if (jdbcMetaData.getErrMsg() == null) {
                            String foreignKey = d.foreignKey;
                            if (! jdbcMetaData.isColExists(
                                ((DrianmonGuiDef.Table) c.fact).schema,
                                 ((DrianmonGuiDef.Table) c.fact).name,
                                  foreignKey))
                            {
                                childErrMsg =
                                 "ForeignKey '" + foreignKey +
                                  "' does not exist in fact table.";
                                break;
                            }
                           // check foreignKey is a fact table column
                            if (! allcols.contains(foreignKey)) {
                               childErrMsg =
                                "ForeignKey '" + foreignKey
                                + "' does not exist in fact table.";
                                break;
                            }
             * /
                        }
                    }
                }
            }
             * === End : disabled
             */
            // Now set the final errormsg
            if (childErrMsg != null) {
                String childClassName = child.getClass().getName();
                String simpleName[] = childClassName.split("[$.]", 0);
                String childName;
                try {
                    Field f = child.getClass().getField("name");
                    childName = (String) f.get(child);
                    if (childName == null) {
                        childName = "";
                    }
                    childErrMsg = messages.getFormattedString(
                        "schemaTreeCellRenderer.childErrorMessageWithName.alert",
                        "{0} {1} is invalid",
                        simpleName[simpleName.length - 1],
                        childName);
                } catch (Exception ex) {
                    childErrMsg = messages.getFormattedString(
                        "schemaTreeCellRenderer.childErrorExceptionMessage.alert",
                        "{0} is invalid",
                        simpleName[simpleName.length - 1]);
                }
                return childErrMsg;
            }
        }

        return null;
    }

    /**
     * Returns whether an object is null or the empty string.
     *
     * @param v Object
     * @return Whether object is null or the empty string
     */
    public static boolean isEmpty(String v) {
        return (v == null) || v.equals("");
    }

    /**
     * Returns whether an array is null or empty
     *
     * @param arr array
     * @return whether the array is null or empty
     */
    public static boolean isEmpty(Object[] arr) {
        return arr == null || arr.length == 0;
    }

    /**
     * Validates a column, and returns an error message if it is invalid.
     *
     * @param column Column
     * @param fieldName Field name
     * @param messages Message provider
     * @param level  Level
     * @param jdbcValidator JDBC validator
     * @param cube Cube
     * @param parentHierarchy Hierarchy
     * @return Error message if invalid, null if valid
     */
    private static String validateColumn(
        String column,
        String fieldName,
        Messages messages,
        DrianmonGuiDef.Level level,
        JdbcValidator jdbcValidator,
        DrianmonGuiDef.Cube cube,
        DrianmonGuiDef.Hierarchy parentHierarchy)
    {
        if (!isEmpty(column)) {
            // database validity check, if database connection is successful
            if (jdbcValidator.isInitialized()) {
                // specified table for level's column
                String table = level.table;
                // If table has been changed in join then sets the table value
                // to null to cause "tableMustBeSet" validation fail.
                if (!isEmpty(table)
                    && parentHierarchy != null
                    && parentHierarchy.relation instanceof DrianmonGuiDef.Join)
                {
                    TreeSet<String> joinTables = new TreeSet<String>();
                    SchemaExplorer.getTableNamesForJoin(
                        parentHierarchy.relation, joinTables);
                    if (!joinTables.contains(table)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.wrongTableValue",
                            "Table value does not correspond to any join");
                    }
                }

                if (!isEmpty(table)
                    && parentHierarchy != null
                    && parentHierarchy.relation instanceof DrianmonGuiDef.Table)
                {
                    final DrianmonGuiDef.Table parentTable =
                        (DrianmonGuiDef.Table) parentHierarchy.relation;
                    DrianmonGuiDef.Table theTable = parentTable;
                    String compareTo =
                        (theTable.alias != null
                         && theTable.alias.trim().length() > 0)
                        ? theTable.alias
                        : theTable.name;
                    if (!table.equals(compareTo)) {
                        return messages.getString(
                            "schemaTreeCellRenderer.tableDoesNotMatch",
                            "Table value does not correspond to Hierarchy Relation");
                    }
                }

                if (!isEmpty(table)
                    && parentHierarchy != null
                    && parentHierarchy.relation instanceof DrianmonGuiDef.View)
                {
                    return messages.getString(
                        "schemaTreeCellRenderer.noTableForView",
                        "Table for column cannot be set in View");
                }

                if (isEmpty(table)) {
                    if (parentHierarchy != null) {
                        if (parentHierarchy.relation == null
                            && cube != null)
                        {
                            // case of degenerate dimension within cube,
                            // hierarchy table not specified
                            if (!jdbcValidator.isColExists(
                                    ((DrianmonGuiDef.Table) cube.fact).schema,
                                    ((DrianmonGuiDef.Table) cube.fact).name,
                                    column))
                            {
                                return messages.getFormattedString(
                                    "schemaTreeCellRenderer.degenDimensionColumnDoesNotExist.alert",
                                    "Degenerate dimension validation check - Column {0} does not exist in fact table",
                                    column);
                            }
                        } else if (parentHierarchy.relation
                                   instanceof DrianmonGuiDef.Table)
                        {
                            final DrianmonGuiDef.Table parentTable =
                                (DrianmonGuiDef.Table) parentHierarchy.relation;
                            if (!jdbcValidator.isColExists(
                                    parentTable.schema,
                                    parentTable.name,
                                    column))
                            {
                                return messages.getFormattedString(
                                    "schemaTreeCellRenderer.columnInTableDoesNotExist.alert",
                                    "Column {0} defined in field {1} does not exist in table {2}",
                                    isEmpty(column.trim())
                                        ? "' '"
                                        : column,
                                    fieldName,
                                    parentTable.name);
                            }
                        } else if (parentHierarchy.relation
                            instanceof DrianmonGuiDef.Join)
                        {
                            // relation is join, table should be specified
                            return messages.getString(
                                "schemaTreeCellRenderer.tableMustBeSet.alert",
                                "Table must be set");
                        }
                    }
                } else {
                    String schema = null;
                    // if using Joins then gets the table name for isColExists
                    // validation.
                    if (parentHierarchy != null
                        && parentHierarchy.relation
                        instanceof DrianmonGuiDef.Join)
                    {
                        String[] schemaAndTable =
                            SchemaExplorer.getTableNameForAlias(
                                parentHierarchy.relation,
                                table);
                        schema = schemaAndTable[0];
                        table = schemaAndTable[1];
                    }
                    if (!jdbcValidator.isColExists(schema, table, column)) {
                        return messages.getFormattedString(
                            "schemaTreeCellRenderer.columnInTableDoesNotExist.alert",
                            "Column {0} defined in field {1} does not exist in table {2}",
                            isEmpty(column.trim())
                                ? "' '"
                                : column,
                            fieldName,
                            table);
                    }
                }
            }
        }
        return null;
    }

}

// End ValidationUtils.java
