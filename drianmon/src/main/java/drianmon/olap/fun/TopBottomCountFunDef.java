/*
// This software is subject to the terms of the Eclipse Public License v1.0
// Agreement, available at the following URL:
// http://www.eclipse.org/legal/epl-v10.html.
// You must accept the terms of that agreement to use this software.
//
// Copyright (C) 2002-2005 Julian Hyde
// Copyright (C) 2005-2020 Hitachi Vantara and others
// All Rights Reserved.
*/

package drianmon.olap.fun;

import static drianmon.olap.fun.sort.Sorter.partiallySortTuples;

import java.util.AbstractList;
import java.util.List;

import drianmon.calc.Calc;
import drianmon.calc.ExpCompiler;
import drianmon.calc.IntegerCalc;
import drianmon.calc.ListCalc;
import drianmon.calc.ResultStyle;
import drianmon.calc.TupleCollections;
import drianmon.calc.TupleList;
import drianmon.calc.impl.AbstractListCalc;
import drianmon.calc.impl.DelegatingTupleList;
import drianmon.calc.impl.UnaryTupleList;
import drianmon.mdx.ResolvedFunCall;
import drianmon.olap.Evaluator;
import drianmon.olap.Exp;
import drianmon.olap.FunDef;
import drianmon.olap.Hierarchy;
import drianmon.olap.Member;
import drianmon.olap.NativeEvaluator;
import drianmon.olap.SchemaReader;
import drianmon.olap.fun.sort.Sorter;

/**
 * Definition of the <code>TopCount</code> and <code>BottomCount</code> MDX builtin functions.
 *
 * @author jhyde
 * @since Mar 23, 2006
 */
class TopBottomCountFunDef extends FunDefBase {
  boolean top;

  static final MultiResolver TopCountResolver =
    new MultiResolver(
      "TopCount",
      "TopCount(<Set>, <Count>[, <Numeric Expression>])",
      "Returns a specified number of items from the top of a set, optionally ordering the set first.",
      new String[] { "fxxnn", "fxxn" } ) {
      protected FunDef createFunDef( Exp[] args, FunDef dummyFunDef ) {
        return new TopBottomCountFunDef( dummyFunDef, true );
      }
    };

  static final MultiResolver BottomCountResolver =
    new MultiResolver(
      "BottomCount",
      "BottomCount(<Set>, <Count>[, <Numeric Expression>])",
      "Returns a specified number of items from the bottom of a set, optionally ordering the set first.",
      new String[] { "fxxnn", "fxxn" } ) {
      protected FunDef createFunDef( Exp[] args, FunDef dummyFunDef ) {
        return new TopBottomCountFunDef( dummyFunDef, false );
      }
    };

  public TopBottomCountFunDef( FunDef dummyFunDef, final boolean top ) {
    super( dummyFunDef );
    this.top = top;

  }

  public Calc compileCall( final ResolvedFunCall call, ExpCompiler compiler ) {
    // Compile the member list expression. Ask for a mutable list, because
    // we're going to sort it later.
    final ListCalc listCalc =
      compiler.compileList( call.getArg( 0 ), true );
    final IntegerCalc integerCalc =
      compiler.compileInteger( call.getArg( 1 ) );
    final Calc orderCalc =
      call.getArgCount() > 2
        ? compiler.compileScalar( call.getArg( 2 ), true )
        : null;
    final int arity = call.getType().getArity();
    return new AbstractListCalc(
      call,
      new Calc[] { listCalc, integerCalc, orderCalc } ) {
      public TupleList evaluateList( Evaluator evaluator ) {
        // Use a native evaluator, if more efficient.
        // TODO: Figure this out at compile time.
        SchemaReader schemaReader = evaluator.getSchemaReader();
        NativeEvaluator nativeEvaluator =
          schemaReader.getNativeSetEvaluator(
            call.getFunDef(), call.getArgs(), evaluator, this );
        if ( nativeEvaluator != null ) {
          return
            (TupleList) nativeEvaluator.execute( ResultStyle.LIST );
        }

        int n = integerCalc.evaluateInteger( evaluator );
        if ( n == 0 || n == drianmon.olap.fun.FunUtil.IntegerNull ) {
          return TupleCollections.emptyList( arity );
        }

        TupleList list = listCalc.evaluateList( evaluator );
        assert list.getArity() == arity;
        if ( list.isEmpty() ) {
          return list;
        }

        if ( orderCalc == null ) {
          // REVIEW: Why require "instanceof AbstractList"?
          if ( list instanceof AbstractList && list.size() <= n ) {
            return list;
          } else if ( top ) {
            return list.subList( 0, n );
          } else {
            return list.subList( list.size() - n, list.size() );
          }
        }

        return partiallySortList(
          evaluator, list, hasHighCardDimension( list ),
          Math.min( n, list.size() ) );
      }

      private TupleList partiallySortList(
        Evaluator evaluator,
        TupleList list,
        boolean highCard,
        int n ) {
        assert list.size() > 0;
        assert n <= list.size();
        if ( highCard ) {
          // sort list in chunks, collect the results
          final int chunkSize = 6400; // what is this really?
          TupleList allChunkResults = TupleCollections.createList(
            arity );
          for ( int i = 0, next; i < list.size(); i = next ) {
            next = Math.min( i + chunkSize, list.size() );
            final TupleList chunk = list.subList( i, next );
            TupleList chunkResult =
              partiallySortList(
                evaluator, chunk, false, n );
            allChunkResults.addAll( chunkResult );
          }
          // one last sort, to merge and cull
          return partiallySortList(
            evaluator, allChunkResults, false, n );
        }

        // normal case: no need for chunks
        final int savepoint = evaluator.savepoint();
        try {
          switch ( list.getArity() ) {
            case 1:
              final List<Member> members =
                Sorter.partiallySortMembers(
                  evaluator.push(),
                  list.slice( 0 ),
                  orderCalc, n, top );
              return new UnaryTupleList( members );
            default:
              final List<List<Member>> tuples =
                partiallySortTuples(
                  evaluator.push(),
                  list,
                  orderCalc, n, top );
              return new DelegatingTupleList(
                list.getArity(),
                tuples );
          }
        } finally {
          evaluator.restore( savepoint );
        }
      }

      public boolean dependsOn( Hierarchy hierarchy ) {
        return anyDependsButFirst( getCalcs(), hierarchy );
      }

      private boolean hasHighCardDimension( TupleList l ) {
        final List<Member> trial = l.get( 0 );
        for ( Member m : trial ) {
          if ( m.getHierarchy().getDimension().isHighCardinality() ) {
            return true;
          }
        }
        return false;
      }
    };
  }
}

// End TopBottomCountFunDef.java
