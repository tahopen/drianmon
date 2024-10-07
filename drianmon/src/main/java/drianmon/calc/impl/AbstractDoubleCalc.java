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
import drianmon.calc.DoubleCalc;
import drianmon.olap.Evaluator;
import drianmon.olap.Exp;
import drianmon.olap.fun.FunUtil;
import drianmon.olap.type.NumericType;

/**
 * Abstract implementation of the {@link drianmon.calc.DoubleCalc} interface.
 *
 * <p>The derived class must
 * implement the {@link #evaluateDouble(drianmon.olap.Evaluator)} method,
 * and the {@link #evaluate(drianmon.olap.Evaluator)} method will call it.
 *
 * @author jhyde
 * @since Sep 27, 2005
 */
public abstract class AbstractDoubleCalc
    extends AbstractCalc
    implements DoubleCalc
{
    /**
     * Creates an AbstractDoubleCalc.
     *
     * @param exp Source expression
     * @param calcs Child compiled expressions
     */
    protected AbstractDoubleCalc(Exp exp, Calc[] calcs) {
        super(exp, calcs);
        assert getType() instanceof NumericType;
    }

    public Object evaluate(Evaluator evaluator) {
        final double d = evaluateDouble(evaluator);
        if (d == FunUtil.DoubleNull) {
            return null;
        }
        return new Double(d);
    }
}

// End AbstractDoubleCalc.java
