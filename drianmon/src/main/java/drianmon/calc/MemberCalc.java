/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package drianmon.calc;

import drianmon.olap.Evaluator;
import drianmon.olap.Member;

/**
 * Expression which yields a {@link Member}.
 *
 * <p>When implementing this interface, it is convenient to extend
 * {@link drianmon.calc.impl.AbstractMemberCalc}, but it is not required.

 * @author jhyde
 * @since Sep 26, 2005
 */
public interface MemberCalc extends Calc {
    /**
     * Evaluates this expression to yield a member.
     *
     * <p>May return the null member (see
     * {@link drianmon.olap.Hierarchy#getNullMember()}) but never null.
     *
     * @param evaluator Evaluation context
     * @return a member
     */
    Member evaluateMember(Evaluator evaluator);
}

// End MemberCalc.java