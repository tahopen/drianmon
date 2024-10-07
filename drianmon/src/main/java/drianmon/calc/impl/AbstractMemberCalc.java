/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package drianmon.calc.impl;

import drianmon.calc.Calc;
import drianmon.calc.MemberCalc;
import drianmon.olap.Evaluator;
import drianmon.olap.Exp;
import drianmon.olap.type.MemberType;

/**
 * Abstract implementation of the {@link drianmon.calc.MemberCalc} interface.
 *
 * <p>The derived class must
 * implement the {@link #evaluateMember(drianmon.olap.Evaluator)} method,
 * and the {@link #evaluate(drianmon.olap.Evaluator)} method will call it.
 *
 * @author jhyde
 * @since Sep 26, 2005
 */
public abstract class AbstractMemberCalc
    extends AbstractCalc
    implements MemberCalc
{
    /**
     * Creates an AbstractMemberCalc.
     *
     * @param exp Source expression
     * @param calcs Child compiled expressions
     */
    protected AbstractMemberCalc(Exp exp, Calc[] calcs) {
        super(exp, calcs);
        assert getType() instanceof MemberType;
    }

    public Object evaluate(Evaluator evaluator) {
        return evaluateMember(evaluator);
    }
}

// End AbstractMemberCalc.java
