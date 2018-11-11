/**
 * Copyright (C) 2009 - present by OpenGamma Inc. and the OpenGamma group of companies
 *
 * Please see distribution for license.
 */
package com.opengamma.web.server;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cometd.Client;

import com.opengamma.core.position.Portfolio;
import com.opengamma.core.position.PortfolioNode;
import com.opengamma.core.position.Position;
import com.opengamma.engine.ComputationTargetResolver;
import com.opengamma.engine.ComputationTargetSpecification;
import com.opengamma.engine.ComputationTargetType;
import com.opengamma.engine.view.client.ViewClient;
import com.opengamma.engine.view.compilation.CompiledViewDefinition;
import com.opengamma.id.UniqueId;
import com.opengamma.web.server.conversion.DoubleValueFormatter;
import com.opengamma.web.server.conversion.DoubleValueOptionalDecimalPlaceFormatter;
import com.opengamma.web.server.conversion.ResultConverterCache;

/**
 * Represents a portfolio grid
 */
public class WebViewPortfolioGrid extends RequirementBasedWebViewGrid {

  private Map<Integer, PortfolioRow> _rowIdToRowMap;

  public WebViewPortfolioGrid(ViewClient viewClient, CompiledViewDefinition compiledViewDefinition, ResultConverterCache resultConverterCache, Client local, Client remote,
      ComputationTargetResolver computationTargetResolver) {
    this(viewClient, compiledViewDefinition, flattenPortfolio(compiledViewDefinition.getPortfolio()), resultConverterCache, local, remote, computationTargetResolver);
  }

  private WebViewPortfolioGrid(ViewClient viewClient, CompiledViewDefinition compiledViewDefinition, List<PortfolioRow> rows, ResultConverterCache resultConverterCache,
                               Client local, Client remote, ComputationTargetResolver computationTargetResolver) {
    super("portfolio", viewClient, compiledViewDefinition, getTargets(rows),
        EnumSet.of(ComputationTargetType.PORTFOLIO_NODE, ComputationTargetType.POSITION), resultConverterCache, local,
        remote, "Loading...", computationTargetResolver);
    _rowIdToRowMap = new HashMap<Integer, PortfolioRow>();
    for (PortfolioRow row : rows) {
      int rowId = getGridStructure().getRowId(row.getTarget());
      _rowIdToRowMap.put(rowId, row);
    }
  }

  @Override
  protected void addRowDetails(UniqueId target, int rowId, Map<String, Object> details) {
    PortfolioRow row = _rowIdToRowMap.get(rowId);
    details.put("indent", row.getDepth());
    if (row.getParentRow() != null) {
      int parentRowId = getGridStructure().getRowId(row.getParentRow().getTarget());
      details.put("parentRowId", parentRowId);
    }
    ComputationTargetType targetType = row.getTarget().getType();
    details.put("type", targetType.toString());
    
    if (targetType == ComputationTargetType.POSITION) {
      Position position = row.getPosition();
      details.put("posId", position.getUniqueId());
      details.put("position", position.getSecurity().getName());
      DoubleValueFormatter formatter = new DoubleValueOptionalDecimalPlaceFormatter();
      details.put("quantity", position.getQuantity().signum() == 0 ? "0" : formatter.format(position.getQuantity()));
    } else {
      details.put("position", row.getAggregateName());
    }
  }

  private static List<ComputationTargetSpecification> getTargets(List<PortfolioRow> rows) {
    List<ComputationTargetSpecification> targets = new ArrayList<ComputationTargetSpecification>(rows.size());
    for (PortfolioRow row : rows) {
      targets.add(row.getTarget());
    }
    return targets;
  }

  private static List<PortfolioRow> flattenPortfolio(final Portfolio portfolio) {
    List<PortfolioRow> rows = new ArrayList<PortfolioRow>();
    if (portfolio == null) {
      return rows;
    }
    flattenPortfolio(portfolio.getRootNode(), null, 0, portfolio.getName(), rows);
    return rows;
  }

  private static void flattenPortfolio(final PortfolioNode portfolio, final PortfolioRow parentRow, final int depth,
                                       final String nodeName, final List<PortfolioRow> rows) {
    PortfolioRow aggregateRow = new PortfolioRow(depth, parentRow,
        new ComputationTargetSpecification(ComputationTargetType.PORTFOLIO_NODE, portfolio.getUniqueId()), null, nodeName);
    rows.add(aggregateRow);

    for (Position position : portfolio.getPositions()) {
      PortfolioRow portfolioRow = new PortfolioRow(depth + 1, aggregateRow,
          new ComputationTargetSpecification(ComputationTargetType.POSITION, position.getUniqueId()), position, null);
      rows.add(portfolioRow);
    }
    Collection<PortfolioNode> subNodes = portfolio.getChildNodes();
    if (subNodes != null && subNodes.size() > 0) {
      for (PortfolioNode subNode : subNodes) {
        flattenPortfolio(subNode, aggregateRow, depth + 1, subNode.getName(), rows);
      }
    }
  }

  //-------------------------------------------------------------------------

  @Override
  protected int getAdditionalCsvColumnCount() {
    return 4;
  }

  @Override
  protected int getCsvDataColumnOffset() {
    // All at start
    return 4;
  }

  @Override
  protected void supplementCsvColumnHeaders(String[] headers) {
    headers[0] = "ID";
    headers[1] = "Parent ID";
    headers[2] = "Position";
    headers[3] = "Quantity";
  }

  @Override
  protected void supplementCsvRowData(int rowId, ComputationTargetSpecification target, String[] row) {
    PortfolioRow portfolioRow = _rowIdToRowMap.get(rowId);
    PortfolioRow parentRow = portfolioRow.getParentRow();
    String parentRowIdText = parentRow != null ? getGridStructure().getRowId(parentRow.getTarget()).toString() : null;
    
    String name;
    String quantity;
    if (target.getType() == ComputationTargetType.POSITION) {
      Position position = portfolioRow.getPosition();
      name = position.getSecurity().getName();
      quantity = position.getQuantity().toPlainString();
    } else {
      name = portfolioRow.getAggregateName();
      quantity = "";
    }
    
    row[0] = Integer.toString(rowId);
    row[1] = parentRowIdText;
    row[2] = name;
    row[3] = quantity;
  }

}
