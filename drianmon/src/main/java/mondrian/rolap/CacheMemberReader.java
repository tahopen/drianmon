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

package mondrian.rolap;

import java.util.*;

import mondrian.olap.*;
import mondrian.rolap.TupleReader.MemberBuilder;
import mondrian.rolap.sql.MemberChildrenConstraint;
import mondrian.rolap.sql.TupleConstraint;

/**
 * <code>CacheMemberReader</code> implements {@link MemberReader} by reading
 * from a pre-populated array of {@link mondrian.olap.Member}s.
 * <p>Note: CacheMemberReader can not handle ragged hierarchies. (HR
 * Tests fail if {@link SmartMemberReader} is replaced with
 * CacheMemberReader).
 *
 * @author jhyde
 * @since 21 December, 2001
 */
class CacheMemberReader implements MemberReader, MemberCache {
    private final MemberSource source;
    private final List<RolapMember> members;
    /** Maps a {@link MemberKey} to a {@link RolapMember}. */
    private final Map<Object, RolapMember> mapKeyToMember;

    CacheMemberReader(MemberSource source) {
        this.source = source;
        if (false) {
            // we don't want the reader to write back to our cache
            Util.discard(source.setCache(this));
        }
        this.mapKeyToMember = new HashMap<Object, RolapMember>();
        this.members = source.getMembers();
        for (int i = 0; i < members.size(); i++) {
            RolapMember member = RolapUtil.strip(members.get(i));
            ((RolapMemberBase) member).setOrdinal(i);
        }
    }

    // implement MemberReader
    public RolapHierarchy getHierarchy() {
        return source.getHierarchy();
    }

    public boolean setCache(MemberCache cache) {
        // we do not support cache writeback -- we must be masters of our
        // own cache
        return false;
    }

    public RolapMember substitute(RolapMember member) {
        return member;
    }

    public RolapMember desubstitute(RolapMember member) {
        return member;
    }

    public RolapMember getMemberByKey(
        RolapLevel level, List<Comparable> keyValues)
    {
        assert keyValues.size() == 1;
        return mapKeyToMember.get(keyValues.get(0));
    }

    public int countMemberChildren( Member member, List<RolapMember> children, MemberChildrenConstraint constraint ) {
        return getMemberChildren( (RolapMember) member, children, constraint ).size();
    }

    // implement MemberReader
    public List<RolapMember> getMembers() {
        return members;
    }

    // implement MemberCache
    public Object makeKey(RolapMember parent, Object key) {
        return new MemberKey(parent, key);
    }

    // implement MemberCache
    public RolapMember getMember(Object key) {
        return mapKeyToMember.get(key);
    }
    public RolapMember getMember(Object key, boolean mustCheckCacheStatus) {
        return mapKeyToMember.get(key);
    }

    // implement MemberCache
    public Object putMember(Object key, RolapMember value) {
        return mapKeyToMember.put(key, value);
    }

    // don't need to implement this MemberCache method because we're never
    // used in a context where it is needed
    public void putChildren(
        RolapMember member,
        MemberChildrenConstraint constraint,
        List<RolapMember> children)
    {
        throw new UnsupportedOperationException();
    }

    // don't need to implement this MemberCache method because we're never
    // used in a context where it is needed
    public void putChildren(
        RolapLevel level,
        TupleConstraint constraint,
        List<RolapMember> children)
    {
        throw new UnsupportedOperationException();
    }

    // this cache is immutable
    public boolean isMutable()
    {
        return false;
    }

    public RolapMember removeMember(Object key)
    {
        throw new UnsupportedOperationException();
    }

    public RolapMember removeMemberAndDescendants(Object key)
    {
        throw new UnsupportedOperationException();
    }

    // don't need to implement this MemberCache method because we're never
    // used in a context where it is needed
    public List<RolapMember> getChildrenFromCache(
        RolapMember member,
        MemberChildrenConstraint constraint)
    {
        return null;
    }

    // don't need to implement this MemberCache method because we're never
    // used in a context where it is needed
    public List<RolapMember> getLevelMembersFromCache(
        RolapLevel level,
        TupleConstraint constraint)
    {
        return null;
    }

    public RolapMember lookupMember(
        List<Id.Segment> uniqueNameParts,
        boolean failIfNotFound)
    {
        return RolapUtil.lookupMember(this, uniqueNameParts, failIfNotFound);
    }

    public List<RolapMember> getRootMembers() {
        List<RolapMember> list = new ArrayList<RolapMember>();
        for (RolapMember member : members) {
            if (member.getParentMember() == null) {
                list.add(member);
            }
        }
        return list;
    }

    public List<RolapMember> getMembersInLevel(
        RolapLevel level)
    {
        List<RolapMember> list = new ArrayList<RolapMember>();
        int levelDepth = level.getDepth();
        for (RolapMember member : members) {
            if (member.getLevel().getDepth() == levelDepth) {
                list.add(member);
            }
        }
        return list;
    }

    public List<RolapMember> getMembersInLevel(
        RolapLevel level,
        TupleConstraint constraint)
    {
        return getMembersInLevel(level);
    }

    public int getLevelMemberCount(RolapLevel level) {
        int count = 0;
        int levelDepth = level.getDepth();
        for (Member member : members) {
            if (member.getLevel().getDepth() == levelDepth) {
                ++count;
            }
        }
        return count;
    }

    public void getMemberChildren(
        RolapMember parentMember,
        List<RolapMember> children)
    {
        for (Member member : members) {
            if (member.getParentMember() == parentMember) {
                ((List)children).add(member);
            }
        }
    }

    public Map<? extends Member, Access> getMemberChildren(
        RolapMember member,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        getMemberChildren(member, children);
        return Util.toNullValuesMap(children);
    }

    public void getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children)
    {
        for (Member member : members) {
            if (parentMembers.contains(member.getParentMember())) {
                ((List)children).add(member);
            }
        }
    }

    public Map<? extends Member, Access> getMemberChildren(
        List<RolapMember> parentMembers,
        List<RolapMember> children,
        MemberChildrenConstraint constraint)
    {
        getMemberChildren(parentMembers, children);
        return Util.toNullValuesMap(children);
    }

    public RolapMember getLeadMember(RolapMember member, int n) {
        if (n >= 0) {
            for (int ordinal = member.getOrdinal(); ordinal < members.size();
                 ordinal++)
            {
                if ((members.get(ordinal).getLevel() == member.getLevel())
                    && (n-- == 0))
                {
                    return members.get(ordinal);
                }
            }
            return (RolapMember) member.getHierarchy().getNullMember();

        } else {
            for (int ordinal = member.getOrdinal(); ordinal >= 0; ordinal--) {
                if ((members.get(ordinal).getLevel() == member.getLevel())
                    && (n++ == 0))
                {
                    return members.get(ordinal);
                }
            }
            return (RolapMember) member.getHierarchy().getNullMember();
        }
    }

    public void getMemberRange(
        RolapLevel level,
        RolapMember startMember,
        RolapMember endMember,
        List<RolapMember> list)
    {
        assert startMember != null;
        assert endMember != null;
        assert startMember.getLevel() == endMember.getLevel();
        final int endOrdinal = endMember.getOrdinal();
        for (int i = startMember.getOrdinal(); i <= endOrdinal; i++) {
            if (members.get(i).getLevel() == endMember.getLevel()) {
                list.add(members.get(i));
            }
        }
    }

    public int getMemberCount() {
        return members.size();
    }

    public int compare(
        RolapMember m1,
        RolapMember m2,
        boolean siblingsAreEqual)
    {
        if (m1 == m2) {
            return 0;
        }
        if (siblingsAreEqual
            && (m1.getParentMember() == m2.getParentMember()))
        {
            return 0;
        }
        Util.assertTrue(members.get(m1.getOrdinal()) == m1);
        Util.assertTrue(members.get(m2.getOrdinal()) == m2);

        return (m1.getOrdinal() < m2.getOrdinal()) ? -1 : 1;
    }

    public MemberBuilder getMemberBuilder() {
        return null;
    }

    public RolapMember getDefaultMember() {
        RolapMember defaultMember =
            (RolapMember) getHierarchy().getDefaultMember();
        if (defaultMember != null) {
            return defaultMember;
        }
        return getRootMembers().get(0);
    }

    public RolapMember getMemberParent(RolapMember member) {
        return member.getParentMember();
    }
}

// End CacheMemberReader.java
