/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2001-2005 Julian Hyde
// Copyright (C) 2005-2018 Hitachi Vantara and others
// All Rights Reserved.
*/
package drianmon.rolap;

import drianmon.olap.DrianmonDef;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.olap4j.impl.UnmodifiableArrayMap;

import drianmon.olap.*;
import drianmon.rolap.format.FormatterCreateContext;
import drianmon.rolap.format.FormatterFactory;
import drianmon.spi.Dialect;
import drianmon.spi.PropertyFormatter;
import mondrian.resource.MondrianResource;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * <code>RolapLevel</code> implements {@link Level} for a ROLAP database.
 *
 * @author jhyde
 * @since 10 August, 2001
 */
public class RolapLevel extends LevelBase {

    private static final Logger LOGGER = LogManager.getLogger(RolapLevel.class);

    /**
     * The column or expression which yields the level's key.
     */
    protected DrianmonDef.Expression keyExp;

    /**
     * The column or expression which yields the level's ordinal.
     */
    protected DrianmonDef.Expression ordinalExp;

    /**
     * The column or expression which yields the level members' caption.
     */
    protected DrianmonDef.Expression captionExp;

    private final Dialect.Datatype datatype;

    private final int flags;

    static final int FLAG_ALL = 0x02;

    /**
     * For SQL generator. Whether values of "column" are unique globally
     * unique (as opposed to unique only within the context of the parent
     * member).
     */
    static final int FLAG_UNIQUE = 0x04;

    private RolapLevel closedPeerLevel;

    protected RolapProperty[] properties;
    private final RolapProperty[] inheritedProperties;

    /**
     * Ths expression which gives the name of members of this level. If null,
     * members are named using the key expression.
     */
    protected DrianmonDef.Expression nameExp;
    /** The expression which joins to the parent member in a parent-child
     * hierarchy, or null if this is a regular hierarchy. */
    protected DrianmonDef.Expression parentExp;
    /** Value which indicates a null parent in a parent-child hierarchy. */
    private final String nullParentValue;

    /** Condition under which members are hidden. */
    private final HideMemberCondition hideMemberCondition;
    protected final DrianmonDef.Closure xmlClosure;
    private final Map<String, Annotation> annotationMap;
    private final SqlStatement.Type internalType; // may be null

    /**
     * Creates a level.
     *
     * @pre parentExp != null || nullParentValue == null
     * @pre properties != null
     * @pre levelType != null
     * @pre hideMemberCondition != null
     */
    RolapLevel(
        RolapHierarchy hierarchy,
        String name,
        String caption,
        boolean visible,
        String description,
        int depth,
        DrianmonDef.Expression keyExp,
        DrianmonDef.Expression nameExp,
        DrianmonDef.Expression captionExp,
        DrianmonDef.Expression ordinalExp,
        DrianmonDef.Expression parentExp,
        String nullParentValue,
        DrianmonDef.Closure xmlClosure,
        RolapProperty[] properties,
        int flags,
        Dialect.Datatype datatype,
        SqlStatement.Type internalType,
        HideMemberCondition hideMemberCondition,
        LevelType levelType,
        String approxRowCount,
        Map<String, Annotation> annotationMap)
    {
        super(
            hierarchy, name, caption, visible, description, depth, levelType);
        assert annotationMap != null;
        Util.assertPrecondition(properties != null, "properties != null");
        Util.assertPrecondition(
            hideMemberCondition != null,
            "hideMemberCondition != null");
        Util.assertPrecondition(levelType != null, "levelType != null");

        if (keyExp instanceof DrianmonDef.Column) {
            checkColumn((DrianmonDef.Column) keyExp);
        }
        this.annotationMap = annotationMap;
        this.approxRowCount = loadApproxRowCount(approxRowCount);
        this.flags = flags;
        this.datatype = datatype;
        this.keyExp = keyExp;
        if (nameExp != null) {
            if (nameExp instanceof DrianmonDef.Column) {
                checkColumn((DrianmonDef.Column) nameExp);
            }
        }
        this.nameExp = nameExp;
        if (captionExp != null) {
            if (captionExp instanceof DrianmonDef.Column) {
                checkColumn((DrianmonDef.Column) captionExp);
            }
        }
        this.captionExp = captionExp;
        if (ordinalExp != null) {
            if (ordinalExp instanceof DrianmonDef.Column) {
                checkColumn((DriamonnDef.Column) ordinalExp);
            }
            this.ordinalExp = ordinalExp;
        } else {
            this.ordinalExp = this.keyExp;
        }
        if (parentExp instanceof DrianmonDef.Column) {
            checkColumn((DrianmonDef.Column) parentExp);
        }
        this.parentExp = parentExp;
        if (parentExp != null) {
            Util.assertTrue(
                !isAll(),
                "'All' level '" + this + "' must not be parent-child");
            Util.assertTrue(
                isUnique(),
                "Parent-child level '" + this
                + "' must have uniqueMembers=\"true\"");
        }
        this.nullParentValue = nullParentValue;
        Util.assertPrecondition(
            parentExp != null || nullParentValue == null,
            "parentExp != null || nullParentValue == null");
        this.xmlClosure = xmlClosure;
        for (RolapProperty property : properties) {
            if (property.getExp() instanceof DrianmonDef.Column) {
                checkColumn((DrianmonDef.Column) property.getExp());
            }
        }
        this.properties = properties;
        List<Property> list = new ArrayList<Property>();
        for (Level level = this; level != null;
             level = level.getParentLevel())
        {
            final Property[] levelProperties = level.getProperties();
            for (final Property levelProperty : levelProperties) {
                Property existingProperty = lookupProperty(
                    list, levelProperty.getName());
                if (existingProperty == null) {
                    list.add(levelProperty);
                } else if (existingProperty.getType()
                    != levelProperty.getType())
                {
                    throw Util.newError(
                        "Property " + this.getName() + "."
                        + levelProperty.getName() + " overrides a "
                        + "property with the same name but different type");
                }
            }
        }
        this.inheritedProperties = list.toArray(new RolapProperty[list.size()]);

        Dimension dim = hierarchy.getDimension();
        if (dim.getDimensionType() == DimensionType.TimeDimension) {
            if (!levelType.isTime() && !isAll()) {
                throw MondrianResource.instance()
                    .NonTimeLevelInTimeHierarchy.ex(getUniqueName());
            }
        } else if (dim.getDimensionType() == null) {
            // there was no dimension type assigned to the dimension
            // - check later
        } else {
            if (levelType.isTime()) {
                throw MondrianResource.instance()
                    .TimeLevelInNonTimeHierarchy.ex(getUniqueName());
            }
        }
        this.internalType = internalType;
        this.hideMemberCondition = hideMemberCondition;
    }

    public RolapHierarchy getHierarchy() {
        return (RolapHierarchy) hierarchy;
    }

    public Map<String, Annotation> getAnnotationMap() {
        return annotationMap;
    }

    private int loadApproxRowCount(String approxRowCount) {
        boolean notNullAndNumeric =
            approxRowCount != null
                && approxRowCount.matches("^\\d+$");
        if (notNullAndNumeric) {
            return Integer.parseInt(approxRowCount);
        } else {
            // if approxRowCount is not set, return MIN_VALUE to indicate
            return Integer.MIN_VALUE;
        }
    }

    protected Logger getLogger() {
        return LOGGER;
    }

    String getTableName() {
        String tableName = null;

        DrianmonDef.Expression expr = getKeyExp();
        if (expr instanceof DrianmonDef.Column) {
            DrianmonDef.Column mc = (DrianmonDef.Column) expr;
            tableName = mc.getTableAlias();
        }
        return tableName;
    }

    public DrianmonDef.Expression getKeyExp() {
        return keyExp;
    }

    public DrianmonDef.Expression getOrdinalExp() {
        return ordinalExp;
    }

    public DrianmonDef.Expression getCaptionExp() {
        return captionExp;
    }

    public boolean hasCaptionColumn() {
        return captionExp != null;
    }

    public boolean hasOrdinalExp() {
      return !getOrdinalExp().equals(getKeyExp());
    }

    final int getFlags() {
        return flags;
    }

    HideMemberCondition getHideMemberCondition() {
        return hideMemberCondition;
    }

    public final boolean isUnique() {
        return (flags & FLAG_UNIQUE) != 0;
    }

    public final Dialect.Datatype getDatatype() {
        return datatype;
    }

    final String getNullParentValue() {
        return nullParentValue;
    }

    /**
     * Returns whether this level is parent-child.
     */
    public boolean isParentChild() {
        return parentExp != null;
    }

    DrianmonDef.Expression getParentExp() {
        return parentExp;
    }

    // RME: this has to be public for two of the DrillThroughTest test.
    public
    DrianmonDef.Expression getNameExp() {
        return nameExp;
    }

    private Property lookupProperty(List<Property> list, String propertyName) {
        for (Property property : list) {
            if (property.getName().equals(propertyName)) {
                return property;
            }
        }
        return null;
    }

    RolapLevel(
        RolapHierarchy hierarchy,
        int depth,
        DrianmonDef.Level xmlLevel)
    {
        this(
            hierarchy,
            xmlLevel.name,
            xmlLevel.caption,
            xmlLevel.visible,
            xmlLevel.description,
            depth,
            xmlLevel.getKeyExp(),
            xmlLevel.getNameExp(),
            xmlLevel.getCaptionExp(),
            xmlLevel.getOrdinalExp(),
            xmlLevel.getParentExp(),
            xmlLevel.nullParentValue,
            xmlLevel.closure,
            createProperties(xmlLevel),
            (xmlLevel.uniqueMembers ? FLAG_UNIQUE : 0),
            xmlLevel.getDatatype(),
            toInternalType(xmlLevel.internalType),
            HideMemberCondition.valueOf(xmlLevel.hideMemberIf),
            LevelType.valueOf(
                xmlLevel.levelType.equals("TimeHalfYear")
                    ? "TimeHalfYears"
                    : xmlLevel.levelType),
            xmlLevel.approxRowCount,
            RolapHierarchy.createAnnotationMap(xmlLevel.annotations));

        if (!Util.isEmpty(xmlLevel.caption)) {
            setCaption(xmlLevel.caption);
        }

        FormatterCreateContext memberFormatterContext =
            new FormatterCreateContext.Builder(getUniqueName())
                .formatterDef(xmlLevel.memberFormatter)
                .formatterAttr(xmlLevel.formatter)
                .build();
        memberFormatter =
            FormatterFactory.instance()
                .createRolapMemberFormatter(memberFormatterContext);
    }

    // helper for constructor
    private static RolapProperty[] createProperties(DrianmonDef.Level xmlLevel)
    {
        List<RolapProperty> list = new ArrayList<RolapProperty>();
        final DrianmonDef.Expression nameExp = xmlLevel.getNameExp();

        if (nameExp != null) {
            list.add(
                new RolapProperty(
                    Property.NAME.name, Property.Datatype.TYPE_STRING,
                    nameExp, null, null, null, true,
                    Property.NAME.description));
        }
        for (int i = 0; i < xmlLevel.properties.length; i++) {
            DrianmonDef.Property xmlProperty = xmlLevel.properties[i];

            FormatterCreateContext formatterContext =
                    new FormatterCreateContext.Builder(xmlProperty.name)
                        .formatterDef(xmlProperty.propertyFormatter)
                        .formatterAttr(xmlProperty.formatter)
                        .build();
            PropertyFormatter formatter =
                FormatterFactory.instance()
                    .createPropertyFormatter(formatterContext);

            list.add(
                new RolapProperty(
                    xmlProperty.name,
                    convertPropertyTypeNameToCode(xmlProperty.type),
                    xmlLevel.getPropertyExp(i),
                    formatter,
                    xmlProperty.caption,
                    xmlLevel.properties[i].dependsOnLevelValue,
                    false,
                    xmlProperty.description));
        }
        return list.toArray(new RolapProperty[list.size()]);
    }

    private static Property.Datatype convertPropertyTypeNameToCode(
        String type)
    {
        if (type.equals("String")) {
            return Property.Datatype.TYPE_STRING;
        } else if (type.equals("Numeric")) {
            return Property.Datatype.TYPE_NUMERIC;
        } else if (type.equals("Integer")) {
            return Property.Datatype.TYPE_INTEGER;
        } else if (type.equals("Long")) {
            return Property.Datatype.TYPE_LONG;
        } else if (type.equals("Boolean")) {
            return Property.Datatype.TYPE_BOOLEAN;
        } else if (type.equals("Timestamp")) {
            return Property.Datatype.TYPE_TIMESTAMP;
        } else if (type.equals("Time")) {
            return Property.Datatype.TYPE_TIME;
        } else if (type.equals("Date")) {
            return Property.Datatype.TYPE_DATE;
        } else {
            throw Util.newError("Unknown property type '" + type + "'");
        }
    }

    private void checkColumn(DrianmonDef.Column nameColumn) {
        final RolapHierarchy rolapHierarchy = (RolapHierarchy) hierarchy;
        if (nameColumn.table == null) {
            final DrianmonDef.Relation table = rolapHierarchy.getUniqueTable();
            if (table == null) {
                throw Util.newError(
                    "must specify a table for level " + getUniqueName()
                    + " because hierarchy has more than one table");
            }
            nameColumn.table = table.getAlias();
        } else {
            if (!rolapHierarchy.tableExists(nameColumn.table)) {
                throw Util.newError(
                    "Table '" + nameColumn.table + "' not found");
            }
        }
    }

    void init(DrianmonDef.CubeDimension xmlDimension) {
        if (xmlClosure != null) {
            final RolapDimension dimension = ((RolapHierarchy) hierarchy)
                .createClosedPeerDimension(this, xmlClosure, xmlDimension);
            closedPeerLevel =
                    (RolapLevel) dimension.getHierarchies()[0].getLevels()[1];
        }
    }

    public final boolean isAll() {
        return (flags & FLAG_ALL) != 0;
    }

    public boolean areMembersUnique() {
        return (depth == 0) || (depth == 1) && hierarchy.hasAll();
    }

    public String getTableAlias() {
        return keyExp.getTableAlias();
    }

    public RolapProperty[] getProperties() {
        return properties;
    }

    public Property[] getInheritedProperties() {
        return inheritedProperties;
    }

    public int getApproxRowCount() {
        return approxRowCount;
    }

    private static final Map<String, SqlStatement.Type> VALUES =
        UnmodifiableArrayMap.of(
            "int", SqlStatement.Type.INT,
            "double", SqlStatement.Type.DOUBLE,
            "Object", SqlStatement.Type.OBJECT,
            "String", SqlStatement.Type.STRING,
            "long", SqlStatement.Type.LONG);

    private static SqlStatement.Type toInternalType(String internalTypeName) {
        SqlStatement.Type type = VALUES.get(internalTypeName);
        if (type == null && internalTypeName != null) {
            throw Util.newError(
                "Invalid value '" + internalTypeName
                + "' for attribute 'internalType' of element 'Level'. "
                + "Valid values are: "
                + VALUES.keySet());
        }
        return type;
    }

    public SqlStatement.Type getInternalType() {
        return internalType;
    }

    /**
     * Conditions under which a level's members may be hidden (thereby creating
     * a <dfn>ragged hierarchy</dfn>).
     */
    public enum HideMemberCondition {
        /** A member always appears. */
        Never,

        /** A member doesn't appear if its name is null or empty. */
        IfBlankName,

        /** A member appears unless its name matches its parent's. */
        IfParentsName
    }

    public OlapElement lookupChild(SchemaReader schemaReader, Id.Segment name) {
        return lookupChild(schemaReader, name, MatchType.EXACT);
    }

    public OlapElement lookupChild(
        SchemaReader schemaReader, Id.Segment name, MatchType matchType)
    {
        if (name instanceof Id.KeySegment) {
            Id.KeySegment keySegment = (Id.KeySegment) name;
            List<Comparable> keyValues = new ArrayList<Comparable>();
            for (Id.NameSegment nameSegment : keySegment.getKeyParts()) {
                final String keyValue = nameSegment.name;
                if (RolapUtil.mdxNullLiteral().equalsIgnoreCase(keyValue)) {
                    keyValues.add(RolapUtil.sqlNullValue);
                } else {
                    keyValues.add(keyValue);
                }
            }
            final List<DrianmonDef.Expression> keyExps = getInheritedKeyExps();
            if (keyExps.size() != keyValues.size()) {
                throw Util.newError(
                    "Wrong number of values in member key; "
                    + keySegment + " has " + keyValues.size()
                    + " values, whereas level's key has " + keyExps.size()
                    + " columns "
                    + new AbstractList<String>() {
                        public String get(int index) {
                            return keyExps.get(index).getGenericExpression();
                        }

                        public int size() {
                            return keyExps.size();
                        }
                    }
                    + ".");
            }
            return getHierarchy().getMemberReader().getMemberByKey(
                this, keyValues);
        }
        List<Member> levelMembers = schemaReader.getLevelMembers(this, true);
        if (levelMembers.size() > 0) {
            Member parent = levelMembers.get(0).getParentMember();
            return
                RolapUtil.findBestMemberMatch(
                    levelMembers,
                    (RolapMember) parent,
                    this,
                    name,
                    matchType);
        }
        return null;
    }

    private List<DrianmonDef.Expression> getInheritedKeyExps() {
        final List<DrianmonDef.Expression> list =
            new ArrayList<DrianmonDef.Expression>();
        for (RolapLevel x = this;; x = (RolapLevel) x.getParentLevel()) {
            final DrianmonDef.Expression keyExp1 = x.getKeyExp();
            if (keyExp1 != null) {
                list.add(keyExp1);
            }
            if (x.isUnique()) {
                break;
            }
        }
        return list;
    }

    /**
     * Indicates that level is not ragged and not a parent/child level.
     */
    public boolean isSimple() {
        // most ragged hierarchies are not simple -- see isTooRagged.
        if (isTooRagged()) {
            return false;
        }
        if (isParentChild()) {
            return false;
        }
        // does not work for measures
        if (isMeasure()) {
            return false;
        }
        return true;
    }

    /**
     * Determines whether the specified level is too ragged for native
     * evaluation, which is able to handle one special case of a ragged
     * hierarchy: when the level specified in the query is the leaf level of
     * the hierarchy and HideMemberCondition for the level is IfBlankName.
     * This is true even if higher levels of the hierarchy can be hidden
     * because even in that case the only column that needs to be read is the
     * column that holds the leaf. IfParentsName can't be handled even at the
     * leaf level because in the general case we aren't reading the column
     * that holds the parent. Also, IfBlankName can't be handled for non-leaf
     * levels because we would have to read the column for the next level
     * down for members with blank names.
     *
     * @return true if the specified level is too ragged for native
     *         evaluation.
     */
    private boolean isTooRagged() {
        // Is this the special case of raggedness that native evaluation
        // is able to handle?
        if (getDepth() == getHierarchy().getLevels().length - 1) {
            switch (getHideMemberCondition()) {
            case Never:
            case IfBlankName:
                return false;
            default:
                return true;
            }
        }
        // Handle the general case in the traditional way.
        return getHierarchy().isRagged();
    }


    /**
     * Returns true when the level is part of a parent/child hierarchy and has
     * an equivalent closed level.
     */
    boolean hasClosedPeer() {
        return closedPeerLevel != null;
    }

    public RolapLevel getClosedPeer() {
        return closedPeerLevel;
    }

    public static RolapLevel lookupLevel(
        RolapLevel[] levels,
        String levelName)
    {
        for (RolapLevel level : levels) {
            if (level.getName().equals(levelName)) {
                return level;
            }
        }
        return null;
    }

}
// End RolapLevel.java
