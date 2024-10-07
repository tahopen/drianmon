/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package drianmon.udf;

import java.util.Date;
import java.util.Locale;

import drianmon.olap.Evaluator;
import drianmon.olap.Syntax;
import drianmon.olap.type.StringType;
import drianmon.olap.type.Type;
import drianmon.spi.UserDefinedFunction;
import drianmon.util.Format;

/**
 * User-defined function <code>CurrentDateString<code>, which returns the
 * current date value as a formatted string, based on a format string passed in
 * as a parameter.  The format string conforms to the format string implemented
 * by {@link Format}.
 *
 * @author Zelaine Fong
 */
public class CurrentDateStringUdf implements UserDefinedFunction {

    public Object execute(Evaluator evaluator, Argument[] arguments) {
        Object arg = arguments[0].evaluateScalar(evaluator);

        final Locale locale = Locale.getDefault();
        final Format format = new Format((String) arg, locale);
        Date currDate = evaluator.getQueryStartTime();
        return format.format(currDate);
    }

    public String getDescription() {
        return "Returns the current date formatted as specified by the format "
            + "parameter.";
    }

    public String getName() {
        return "CurrentDateString";
    }

    public Type[] getParameterTypes() {
        return new Type[] { new StringType() };
    }

    public String[] getReservedWords() {
        return null;
    }

    public Type getReturnType(Type[] parameterTypes) {
        return new StringType();
    }

    public Syntax getSyntax() {
        return Syntax.Function;
    }

}

// End CurrentDateStringUdf.java
