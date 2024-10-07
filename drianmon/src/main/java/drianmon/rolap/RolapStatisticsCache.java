/*
* This software is subject to the terms of the Eclipse Public License v1.0
* Agreement, available at the following URL:
* http://www.eclipse.org/legal/epl-v10.html.
* You must accept the terms of that agreement to use this software.
*
* Copyright (c) 2002-2017 Hitachi Vantara..  All rights reserved.
*/

package drianmon.rolap;

import drianmon.olap.DrianmonDef;

import java.util.*;
import javax.sql.DataSource;

import drianmon.rolap.sql.SqlQuery;
import drianmon.server.Execution;
import drianmon.spi.Dialect;
import drianmon.spi.StatisticsProvider;

/**
 * Provides and caches statistics.
 *
 * <p>Wrapper around a chain of {@link drianmon.spi.StatisticsProvider}s,
 * followed by a cache to store the results.</p>
 */
public class RolapStatisticsCache {
    private final RolapStar star;
    private final Map<List, Long> columnMap = new HashMap<List, Long>();
    private final Map<List, Long> tableMap = new HashMap<List, Long>();
    private final Map<String, Long> queryMap =
        new HashMap<String, Long>();

    public RolapStatisticsCache(RolapStar star) {
        this.star = star;
    }

    public long getRelationCardinality(
        DrianmonDef.Relation relation,
        String alias,
        long approxRowCount)
    {
        if (approxRowCount >= 0) {
            return approxRowCount;
        }
        if (relation instanceof DrianmonDef.Table) {
            final DrianmonDef.Table table = (DrianmonDef.Table) relation;
            return getTableCardinality(
                null, table.schema, table.name);
        } else {
            final SqlQuery sqlQuery = star.getSqlQuery();
            sqlQuery.addSelect("*", null);
            sqlQuery.addFrom(relation, null, true);
            return getQueryCardinality(sqlQuery.toString());
        }
    }

    private long getTableCardinality(
        String catalog,
        String schema,
        String table)
    {
        final List<String> key = Arrays.asList(catalog, schema, table);
        long rowCount = -1;
        if (tableMap.containsKey(key)) {
            rowCount = tableMap.get(key);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            final List<StatisticsProvider> statisticsProviders =
                dialect.getStatisticsProviders();
            final Execution execution =
                new Execution(
                    star.getSchema().getInternalConnection()
                        .getInternalStatement(),
                    0);
            for (StatisticsProvider statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getTableCardinality(
                    dialect,
                    star.getDataSource(),
                    catalog,
                    schema,
                    table,
                    execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            tableMap.put(key, rowCount);
        }
        return rowCount;
    }

    private long getQueryCardinality(String sql) {
        long rowCount = -1;
        if (queryMap.containsKey(sql)) {
            rowCount = queryMap.get(sql);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            final List<StatisticsProvider> statisticsProviders =
                dialect.getStatisticsProviders();
            final Execution execution =
                new Execution(
                    star.getSchema().getInternalConnection()
                        .getInternalStatement(),
                    0);
            for (StatisticsProvider statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getQueryCardinality(
                    dialect, star.getDataSource(), sql, execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            queryMap.put(sql, rowCount);
        }
        return rowCount;
    }

    public long getColumnCardinality(
        DrianmonDef.Relation relation,
        DrianmonDef.Expression expression,
        long approxCardinality)
    {
        if (approxCardinality >= 0) {
            return approxCardinality;
        }
        if (relation instanceof DrianmonDef.Table
            && expression instanceof DrianmonDef.Column)
        {
            final DrianmonDef.Table table = (DrianmonDef.Table) relation;
            final DrianmonDef.Column column = (DrianmonDef.Column) expression;
            return getColumnCardinality(
                null,
                table.schema,
                table.name,
                column.name);
        } else {
            final SqlQuery sqlQuery = star.getSqlQuery();
            sqlQuery.setDistinct(true);
            sqlQuery.addSelect(expression.getExpression(sqlQuery), null);
            sqlQuery.addFrom(relation, null, true);
            return getQueryCardinality(sqlQuery.toString());
        }
    }

    private long getColumnCardinality(
        String catalog,
        String schema,
        String table,
        String column)
    {
        final List<String> key = Arrays.asList(catalog, schema, table, column);
        long rowCount = -1;
        if (columnMap.containsKey(key)) {
            rowCount = columnMap.get(key);
        } else {
            final Dialect dialect = star.getSqlQueryDialect();
            final List<StatisticsProvider> statisticsProviders =
                dialect.getStatisticsProviders();
            final Execution execution =
                new Execution(
                    star.getSchema().getInternalConnection()
                        .getInternalStatement(),
                    0);
            for (StatisticsProvider statisticsProvider : statisticsProviders) {
                rowCount = statisticsProvider.getColumnCardinality(
                    dialect,
                    star.getDataSource(),
                    catalog,
                    schema,
                    table,
                    column,
                    execution);
                if (rowCount >= 0) {
                    break;
                }
            }

            // Note: If all providers fail, we put -1 into the cache, to ensure
            // that we won't try again.
            columnMap.put(key, rowCount);
        }
        return rowCount;
    }

    public int getColumnCardinality2(
        DataSource dataSource,
        Dialect dialect,
        String catalog,
        String schema,
        String table,
        String column)
    {
        return -1;
    }
}

// End RolapStatisticsCache.java
